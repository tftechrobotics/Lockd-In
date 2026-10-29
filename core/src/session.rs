//! What a session wants *right now*: the desired-policy half of
//! "desired policy / device acknowledgment / observed enforcement".
//!
//! `evaluate` is a pure function of (occurrence, controls, now). Clients call
//! it on every timer, wake-up, clock change and incoming sync message, and
//! never derive state from a per-second counter.
//!
//! Four different things can mean "not restricting", and they are kept
//! separate because the UI, history and paired devices must tell them apart:
//!
//! | Situation                 | Where it lives                          |
//! |---------------------------|-----------------------------------------|
//! | Scheduled break           | the plan ([`ReleaseReason::ScheduledBreak`]) |
//! | Delayed intentional override | [`OverrideRecord`] (countdown, cancellable) |
//! | Immediate emergency recovery | [`EmergencyRecovery`] (no countdown)  |
//! | Enforcement failure       | *not here*: a device-level observation, see [`crate::protection`] |
//!
//! Enforcement failure never changes the desired state: if a device cannot
//! enforce, the session still *wants* restriction and the device reports that
//! it is failing to deliver it.

use chrono::{DateTime, Duration, Utc};
use serde::{Deserialize, Serialize};

use crate::plan::PhaseKind;
use crate::timeline::{Occurrence, Phase};

/// Recommended (not yet owner-approved) countdown before an override takes effect.
pub const DEFAULT_OVERRIDE_DELAY_SECONDS: u32 = 90;
/// Upper bound on any countdown. Larger values are clamped down, never up,
/// so bad input can only make release sooner, never extend a lock.
pub const MAX_OVERRIDE_DELAY_SECONDS: u32 = 15 * 60;
/// Default warning lead before restrictions resume after a break.
pub const DEFAULT_RESUME_WARNING_SECONDS: i64 = 60;

#[derive(Debug, Clone, Copy, PartialEq, Eq, Serialize, Deserialize)]
#[serde(rename_all = "snake_case")]
pub enum OverrideScope {
    /// Lift restrictions for the rest of the focus period it was requested in.
    /// Later focus periods restrict again.
    CurrentFocusPeriod,
    /// End the whole session.
    RestOfSession,
}

/// A delayed, intentional, cancellable decision by the user to stop restricting.
#[derive(Debug, Clone, PartialEq, Eq, Serialize, Deserialize)]
#[serde(deny_unknown_fields)]
pub struct OverrideRecord {
    pub id: String,
    pub scope: OverrideScope,
    /// Phase the user was in when requesting. Must contain `requested_at`.
    pub phase_index: usize,
    pub requested_at: DateTime<Utc>,
    pub delay_seconds: u32,
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub cancelled_at: Option<DateTime<Utc>>,
}

impl OverrideRecord {
    pub fn effective_at(&self) -> DateTime<Utc> {
        self.requested_at
            + Duration::seconds(i64::from(
                self.delay_seconds.min(MAX_OVERRIDE_DELAY_SECONDS),
            ))
    }

    fn cancelled_by(&self, t: DateTime<Utc>) -> bool {
        self.cancelled_at.is_some_and(|c| c <= t)
    }

    /// The phase this override was legitimately requested in, if any. Prevents
    /// an override from "pre-authorizing" a later focus period.
    fn origin<'a>(&self, occ: &'a Occurrence) -> Option<&'a Phase> {
        let phase = occ.phases.get(self.phase_index)?;
        let inside = phase.start <= self.requested_at && self.requested_at < phase.end;
        let kind_ok = match self.scope {
            OverrideScope::CurrentFocusPeriod => phase.kind == PhaseKind::Focus,
            OverrideScope::RestOfSession => true,
        };
        (inside && kind_ok).then_some(phase)
    }

    /// When this override actually lifts restrictions, if it ever does.
    fn takes_effect(&self, occ: &Occurrence) -> Option<DateTime<Utc>> {
        let origin = self.origin(occ)?;
        let eff = self.effective_at();
        let limit = match self.scope {
            OverrideScope::CurrentFocusPeriod => origin.end,
            OverrideScope::RestOfSession => occ.end,
        };
        if eff >= limit || self.cancelled_by(eff) {
            return None;
        }
        Some(eff)
    }
}

/// Immediate release with no countdown, for real emergencies or broken
/// enforcement. Always available; always recorded locally and included in the
/// device's next status report (it cannot be delivered while offline).
#[derive(Debug, Clone, PartialEq, Eq, Serialize, Deserialize)]
#[serde(deny_unknown_fields)]
pub struct EmergencyRecovery {
    pub at: DateTime<Utc>,
}

/// User actions recorded against one occurrence.
#[derive(Debug, Clone, Default, PartialEq, Eq, Serialize, Deserialize)]
#[serde(deny_unknown_fields)]
pub struct SessionControls {
    #[serde(default)]
    pub overrides: Vec<OverrideRecord>,
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub emergency_recovery: Option<EmergencyRecovery>,
}

#[derive(Debug, Clone, Copy, PartialEq, Eq, Serialize, Deserialize)]
#[serde(rename_all = "snake_case")]
pub enum SessionStatus {
    Upcoming,
    Active,
    Completed,
    EndedByOverride,
    EndedByEmergencyRecovery,
}

/// Why the desired state is "release". `None` when restricting.
#[derive(Debug, Clone, Copy, PartialEq, Eq, Serialize, Deserialize)]
#[serde(rename_all = "snake_case")]
pub enum ReleaseReason {
    NotStarted,
    ScheduledBreak,
    OverrideCurrentFocusPeriod,
    SessionEndedByOverride,
    EmergencyRecovery,
    Completed,
}

#[derive(Debug, Clone, PartialEq, Eq, Serialize, Deserialize)]
#[serde(tag = "type", rename_all = "snake_case")]
pub enum OverrideState {
    None,
    /// Countdown running; takes effect at `effective_at` unless cancelled.
    Pending {
        scope: OverrideScope,
        effective_at: DateTime<Utc>,
    },
    /// Restrictions lifted until the end of this focus period.
    ActiveForFocusPeriod {
        until: DateTime<Utc>,
    },
}

#[derive(Debug, Clone, PartialEq, Eq, Serialize, Deserialize)]
pub struct Snapshot {
    pub status: SessionStatus,
    pub phase: Option<Phase>,
    /// The single bit enforcement acts on.
    pub restricting: bool,
    pub release_reason: Option<ReleaseReason>,
    pub override_state: OverrideState,
    /// Next instant at which this snapshot may change. Clients set a timer for it.
    pub next_transition: Option<DateTime<Utc>>,
}

/// Desired state of `occ` at `now`.
pub fn evaluate(occ: &Occurrence, controls: &SessionControls, now: DateTime<Utc>) -> Snapshot {
    let emergency_at = controls
        .emergency_recovery
        .as_ref()
        .map(|e| e.at)
        .filter(|t| *t < occ.end);
    let session_override_at = controls
        .overrides
        .iter()
        .filter(|o| o.scope == OverrideScope::RestOfSession)
        .filter_map(|o| o.takes_effect(occ))
        .min();

    let status = if emergency_at.is_some_and(|t| t <= now) {
        SessionStatus::EndedByEmergencyRecovery
    } else if session_override_at.is_some_and(|t| t <= now) {
        SessionStatus::EndedByOverride
    } else if now < occ.start {
        SessionStatus::Upcoming
    } else if now >= occ.end {
        SessionStatus::Completed
    } else {
        SessionStatus::Active
    };

    let phase = (status == SessionStatus::Active)
        .then(|| occ.phase_at(now).cloned())
        .flatten();

    // Override state as seen at `now` (period overrides and pending countdowns).
    let mut override_state = OverrideState::None;
    if status == SessionStatus::Active {
        for o in &controls.overrides {
            if o.cancelled_by(now) {
                continue;
            }
            let Some(origin) = o.origin(occ) else {
                continue;
            };
            let Some(eff) = o.takes_effect(occ) else {
                continue;
            };
            let current = phase.as_ref().is_some_and(|p| p.index == origin.index);
            match o.scope {
                OverrideScope::CurrentFocusPeriod if current && eff <= now => {
                    override_state = OverrideState::ActiveForFocusPeriod { until: origin.end };
                    break;
                }
                _ if eff > now && (current || o.scope == OverrideScope::RestOfSession) => {
                    override_state = OverrideState::Pending {
                        scope: o.scope,
                        effective_at: eff,
                    };
                }
                _ => {}
            }
        }
    }

    let in_focus = phase.as_ref().is_some_and(|p| p.kind == PhaseKind::Focus);
    let lifted = matches!(override_state, OverrideState::ActiveForFocusPeriod { .. });
    let restricting = status == SessionStatus::Active && in_focus && !lifted;

    let release_reason = if restricting {
        None
    } else {
        Some(match status {
            SessionStatus::Upcoming => ReleaseReason::NotStarted,
            SessionStatus::Completed => ReleaseReason::Completed,
            SessionStatus::EndedByOverride => ReleaseReason::SessionEndedByOverride,
            SessionStatus::EndedByEmergencyRecovery => ReleaseReason::EmergencyRecovery,
            SessionStatus::Active if lifted => ReleaseReason::OverrideCurrentFocusPeriod,
            SessionStatus::Active => ReleaseReason::ScheduledBreak,
        })
    };

    // Candidate instants where the answer can change.
    let mut candidates: Vec<DateTime<Utc>> = vec![occ.start, occ.end];
    candidates.extend(occ.phases.iter().map(|p| p.end));
    candidates.extend(emergency_at);
    for o in &controls.overrides {
        if o.origin(occ).is_some() {
            candidates.push(o.effective_at());
            candidates.extend(o.cancelled_at);
        }
    }
    let ended = !matches!(status, SessionStatus::Upcoming | SessionStatus::Active);
    let next_transition = if ended {
        None
    } else {
        candidates
            .into_iter()
            .filter(|t| *t > now && *t <= occ.end)
            .min()
    };

    Snapshot {
        status,
        phase,
        restricting,
        release_reason,
        override_state,
        next_transition,
    }
}

#[derive(Debug, Clone, PartialEq, Eq, Serialize, Deserialize)]
#[serde(tag = "type", rename_all = "snake_case")]
pub enum Notice {
    /// Reminder before a session begins.
    SessionStarting {
        at: DateTime<Utc>,
        starts: DateTime<Utc>,
    },
    /// Warning before restrictions return after a break.
    BreakEnding {
        at: DateTime<Utc>,
        resumes: DateTime<Utc>,
    },
    SessionEnded {
        at: DateTime<Utc>,
    },
}

/// Notifications a client should schedule for an occurrence. Kept deliberately
/// few: one reminder, one warning per break, one completion.
pub fn notices(occ: &Occurrence, reminder_lead: Duration, resume_lead: Duration) -> Vec<Notice> {
    let mut out = vec![Notice::SessionStarting {
        at: occ.start - reminder_lead,
        starts: occ.start,
    }];
    for p in occ.phases.iter().filter(|p| p.kind == PhaseKind::Break) {
        let lead = resume_lead.min(p.end - p.start);
        out.push(Notice::BreakEnding {
            at: p.end - lead,
            resumes: p.end,
        });
    }
    out.push(Notice::SessionEnded { at: occ.end });
    out
}
