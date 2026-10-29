//! What a session wants *right now*: the desired-policy half of
//! "desired policy / device acknowledgment / observed enforcement".
//!
//! `evaluate` is a pure function of (occurrence, controls, now). Clients call
//! it on every timer, wake-up, clock change and incoming sync message, and
//! never derive state from a per-second counter.

use chrono::{DateTime, Duration, Utc};
use serde::{Deserialize, Serialize};

use crate::plan::PhaseKind;
use crate::timeline::{Occurrence, Phase};

/// Default countdown before an intentional override takes effect.
pub const DEFAULT_OVERRIDE_DELAY_SECONDS: u32 = 90;
pub const MAX_OVERRIDE_DELAY_SECONDS: u32 = 15 * 60;
/// Default warning lead before restrictions resume after a break.
pub const DEFAULT_RESUME_WARNING_SECONDS: i64 = 60;

/// A delayed, intentional lift of restrictions for the rest of one focus period.
/// It never affects later focus periods or later sessions.
#[derive(Debug, Clone, PartialEq, Eq, Serialize, Deserialize)]
#[serde(deny_unknown_fields)]
pub struct OverrideRecord {
    pub id: String,
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

    /// Only an override requested during the focus phase it names counts.
    fn applies_to<'a>(&self, occ: &'a Occurrence) -> Option<&'a Phase> {
        let phase = occ.phases.get(self.phase_index)?;
        (phase.kind == PhaseKind::Focus
            && phase.start <= self.requested_at
            && self.requested_at < phase.end)
            .then_some(phase)
    }
}

#[derive(Debug, Clone, Copy, PartialEq, Eq, Serialize, Deserialize)]
#[serde(rename_all = "snake_case")]
pub enum EarlyEndReason {
    /// Immediate release for emergencies or broken enforcement. Always available.
    EmergencyRelease,
    /// The user ended the whole session after the override countdown.
    EndedByUser,
}

#[derive(Debug, Clone, PartialEq, Eq, Serialize, Deserialize)]
#[serde(deny_unknown_fields)]
pub struct EarlyEnd {
    pub at: DateTime<Utc>,
    pub reason: EarlyEndReason,
}

/// User actions recorded against one occurrence.
#[derive(Debug, Clone, Default, PartialEq, Eq, Serialize, Deserialize)]
#[serde(deny_unknown_fields)]
pub struct SessionControls {
    #[serde(default)]
    pub overrides: Vec<OverrideRecord>,
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub early_end: Option<EarlyEnd>,
}

#[derive(Debug, Clone, Copy, PartialEq, Eq, Serialize, Deserialize)]
#[serde(rename_all = "snake_case")]
pub enum SessionStatus {
    Upcoming,
    Active,
    Completed,
    EndedEarly,
}

#[derive(Debug, Clone, PartialEq, Eq, Serialize, Deserialize)]
#[serde(tag = "type", rename_all = "snake_case")]
pub enum OverrideState {
    None,
    /// Countdown running; restrictions lift at `effective_at` unless cancelled.
    Pending {
        effective_at: DateTime<Utc>,
    },
    /// Restrictions are lifted until the end of this focus period.
    Active {
        until: DateTime<Utc>,
    },
}

#[derive(Debug, Clone, PartialEq, Eq, Serialize, Deserialize)]
pub struct Snapshot {
    pub status: SessionStatus,
    pub phase: Option<Phase>,
    /// The single bit enforcement acts on.
    pub restricting: bool,
    pub override_state: OverrideState,
    /// Next instant at which this snapshot may change. Clients set a timer for it.
    pub next_transition: Option<DateTime<Utc>>,
}

/// Desired state of `occ` at `now`.
pub fn evaluate(occ: &Occurrence, controls: &SessionControls, now: DateTime<Utc>) -> Snapshot {
    let early_end = controls.early_end.as_ref().filter(|e| e.at < occ.end);
    let ended_early = early_end.is_some_and(|e| e.at <= now);

    let status = if ended_early {
        SessionStatus::EndedEarly
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

    let mut override_state = OverrideState::None;
    if let Some(p) = phase.as_ref().filter(|p| p.kind == PhaseKind::Focus) {
        for o in &controls.overrides {
            if o.applies_to(occ).map(|ph| ph.index) != Some(p.index) || o.cancelled_by(now) {
                continue;
            }
            let eff = o.effective_at();
            if eff >= p.end {
                continue; // countdown outlasts the period; nothing to lift
            }
            if eff <= now {
                override_state = OverrideState::Active { until: p.end };
                break;
            }
            override_state = OverrideState::Pending { effective_at: eff };
        }
    }

    let restricting = status == SessionStatus::Active
        && phase.as_ref().is_some_and(|p| p.kind == PhaseKind::Focus)
        && !matches!(override_state, OverrideState::Active { .. });

    // Candidate instants where the answer can change.
    let mut candidates: Vec<DateTime<Utc>> = vec![occ.start, occ.end];
    candidates.extend(occ.phases.iter().map(|p| p.end));
    if let Some(e) = early_end {
        candidates.push(e.at);
    }
    for o in &controls.overrides {
        if o.applies_to(occ).is_some() {
            candidates.push(o.effective_at());
            if let Some(c) = o.cancelled_at {
                candidates.push(c);
            }
        }
    }
    let next_transition = if matches!(status, SessionStatus::Completed | SessionStatus::EndedEarly)
    {
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
