mod common;

use chrono::{DateTime, Duration, Utc, Weekday};
use common::*;
use focus_core::conflicts::find_conflicts;
use focus_core::plan::{PhaseKind, SessionPlan};
use focus_core::session::*;
use focus_core::timeline::{Occurrence, occurrence_on};

fn brief_occ() -> Occurrence {
    let s = once("a", date(2026, 11, 3), time(18, 0), brief_plan());
    occurrence_on(&s, date(2026, 11, 3)).unwrap().unwrap()
}

fn at(h: u32, m: u32) -> DateTime<Utc> {
    chi(2026, 11, 3, h, m)
}

fn ovr(
    scope: OverrideScope,
    phase_index: usize,
    requested: DateTime<Utc>,
    delay: u32,
) -> OverrideRecord {
    OverrideRecord {
        id: "o1".into(),
        scope,
        phase_index,
        requested_at: requested,
        delay_seconds: delay,
        cancelled_at: None,
    }
}

fn with(overrides: Vec<OverrideRecord>) -> SessionControls {
    SessionControls {
        overrides,
        emergency_recovery: None,
    }
}

use OverrideScope::{CurrentFocusPeriod as Period, RestOfSession as Rest};

#[test]
fn lifecycle_and_release_reasons_without_controls() {
    let occ = brief_occ();
    let c = SessionControls::default();

    let s = evaluate(&occ, &c, at(17, 59));
    assert_eq!(
        (s.status, s.restricting, s.release_reason, s.next_transition),
        (
            SessionStatus::Upcoming,
            false,
            Some(ReleaseReason::NotStarted),
            Some(at(18, 0))
        )
    );

    let s = evaluate(&occ, &c, at(18, 0));
    assert_eq!(
        (s.status, s.restricting, s.release_reason),
        (SessionStatus::Active, true, None)
    );
    assert_eq!(s.phase.as_ref().unwrap().kind, PhaseKind::Focus);
    assert_eq!(s.next_transition, Some(at(18, 40)));

    let s = evaluate(&occ, &c, at(18, 45));
    assert_eq!(
        (s.restricting, s.release_reason),
        (false, Some(ReleaseReason::ScheduledBreak))
    );
    assert_eq!(s.next_transition, Some(at(18, 50)));

    assert!(
        evaluate(&occ, &c, at(18, 50)).restricting,
        "restrictions resume after the break"
    );

    let s = evaluate(&occ, &c, at(19, 30));
    assert_eq!(
        (s.status, s.restricting, s.release_reason, s.next_transition),
        (
            SessionStatus::Completed,
            false,
            Some(ReleaseReason::Completed),
            None
        )
    );
}

#[test]
fn period_override_lifts_only_the_current_focus_period_after_delay() {
    let occ = brief_occ();
    let c = with(vec![ovr(Period, 0, at(18, 10), 90)]);
    let eff = at(18, 10) + Duration::seconds(90);

    let s = evaluate(&occ, &c, at(18, 11));
    assert!(s.restricting, "still restricting during countdown");
    assert_eq!(
        s.override_state,
        OverrideState::Pending {
            scope: Period,
            effective_at: eff
        }
    );
    assert_eq!(s.next_transition, Some(eff));

    let s = evaluate(&occ, &c, eff);
    assert_eq!(
        (s.restricting, s.release_reason),
        (false, Some(ReleaseReason::OverrideCurrentFocusPeriod))
    );
    assert_eq!(
        s.override_state,
        OverrideState::ActiveForFocusPeriod { until: at(18, 40) }
    );

    // Break, then the second focus period restricts again.
    assert_eq!(
        evaluate(&occ, &c, at(18, 45)).release_reason,
        Some(ReleaseReason::ScheduledBreak)
    );
    let s = evaluate(&occ, &c, at(18, 55));
    assert!(s.restricting);
    assert_eq!(s.override_state, OverrideState::None);
}

#[test]
fn session_override_ends_whole_session_after_delay() {
    let occ = brief_occ();
    let c = with(vec![ovr(Rest, 0, at(18, 10), 90)]);
    let eff = at(18, 10) + Duration::seconds(90);
    let s = evaluate(&occ, &c, at(18, 11));
    assert!(s.restricting);
    assert_eq!(
        s.override_state,
        OverrideState::Pending {
            scope: Rest,
            effective_at: eff
        }
    );

    let s = evaluate(&occ, &c, eff);
    assert_eq!(
        (s.status, s.restricting, s.release_reason, s.next_transition),
        (
            SessionStatus::EndedByOverride,
            false,
            Some(ReleaseReason::SessionEndedByOverride),
            None
        )
    );
    assert!(
        !evaluate(&occ, &c, at(18, 55)).restricting,
        "later focus periods do not resume"
    );

    // Requesting to end the session during a break is allowed and also delayed.
    let c = with(vec![ovr(Rest, 1, at(18, 41), 90)]);
    assert_eq!(evaluate(&occ, &c, at(18, 42)).status, SessionStatus::Active);
    assert_eq!(
        evaluate(&occ, &c, at(18, 43)).status,
        SessionStatus::EndedByOverride
    );
}

#[test]
fn override_cancellation() {
    let occ = brief_occ();
    // Cancelled during countdown: no effect at all.
    let mut o = ovr(Period, 0, at(18, 10), 90);
    o.cancelled_at = Some(at(18, 10) + Duration::seconds(30));
    let s = evaluate(&occ, &with(vec![o]), at(18, 15));
    assert!(s.restricting);
    assert_eq!(s.override_state, OverrideState::None);

    // Cancelled after taking effect: user chose to resume focus.
    let mut o = ovr(Period, 0, at(18, 10), 60);
    o.cancelled_at = Some(at(18, 20));
    let c = with(vec![o]);
    assert!(!evaluate(&occ, &c, at(18, 15)).restricting);
    assert!(evaluate(&occ, &c, at(18, 20)).restricting);

    // A cancelled session override never ends the session.
    let mut o = ovr(Rest, 0, at(18, 10), 90);
    o.cancelled_at = Some(at(18, 11));
    assert_eq!(
        evaluate(&occ, &with(vec![o]), at(18, 30)).status,
        SessionStatus::Active
    );
}

#[test]
fn ineffective_or_misattributed_overrides_are_ignored() {
    let occ = brief_occ();
    // Countdown would end after the period ends.
    let late = with(vec![ovr(Period, 0, at(18, 39), 120)]);
    assert!(evaluate(&occ, &late, at(18, 39) + Duration::seconds(50)).restricting);
    assert!(evaluate(&occ, &late, at(18, 55)).restricting);
    // Period override requested during a break.
    let in_break = with(vec![ovr(Period, 1, at(18, 45), 0)]);
    assert!(evaluate(&occ, &in_break, at(18, 55)).restricting);
    // Names phase 2 but was requested during phase 0: must not pre-authorize a later period.
    let preauth = with(vec![ovr(Period, 2, at(18, 5), 0)]);
    assert!(evaluate(&occ, &preauth, at(18, 55)).restricting);
    // Out-of-range delay is clamped to 15 minutes (never longer).
    let huge = with(vec![ovr(Period, 0, at(18, 1), u32::MAX)]);
    assert!(evaluate(&occ, &huge, at(18, 15)).restricting);
    assert!(!evaluate(&occ, &huge, at(18, 16)).restricting);
}

#[test]
fn emergency_recovery_is_immediate_final_and_distinct() {
    let occ = brief_occ();
    let c = SessionControls {
        overrides: vec![ovr(Rest, 0, at(18, 1), 900)], // a pending countdown is irrelevant
        emergency_recovery: Some(EmergencyRecovery { at: at(18, 5) }),
    };
    assert!(evaluate(&occ, &c, at(18, 4)).restricting);
    let s = evaluate(&occ, &c, at(18, 5));
    assert_eq!(
        (s.status, s.restricting, s.release_reason, s.next_transition),
        (
            SessionStatus::EndedByEmergencyRecovery,
            false,
            Some(ReleaseReason::EmergencyRecovery),
            None
        )
    );
    assert!(!evaluate(&occ, &c, at(18, 55)).restricting);
}

#[test]
fn notices_are_few_and_correctly_timed() {
    let occ = brief_occ();
    let n = notices(&occ, Duration::minutes(5), Duration::seconds(60));
    assert_eq!(
        n,
        vec![
            Notice::SessionStarting {
                at: at(17, 55),
                starts: at(18, 0)
            },
            Notice::BreakEnding {
                at: at(18, 49),
                resumes: at(18, 50)
            },
            Notice::SessionEnded { at: at(19, 30) },
        ]
    );
    // A one-minute break never produces a warning before the break starts.
    let s = once(
        "b",
        date(2026, 11, 3),
        time(18, 0),
        SessionPlan::rounds(2, 10, 1),
    );
    let occ = occurrence_on(&s, date(2026, 11, 3)).unwrap().unwrap();
    for n in notices(&occ, Duration::minutes(5), Duration::seconds(300)) {
        if let Notice::BreakEnding { at: t, .. } = n {
            assert_eq!(t, at(18, 10));
        }
    }
}

#[test]
fn conflicts_detect_overlap_but_not_adjacency() {
    let a = once("a", date(2026, 11, 3), time(18, 0), brief_plan()); // 18:00-19:30
    let adjacent = once(
        "b",
        date(2026, 11, 3),
        time(19, 30),
        SessionPlan::single(30),
    );
    let overlapping = weekly(
        "c",
        &[Weekday::Tue],
        date(2026, 11, 1),
        time(19, 0),
        SessionPlan::single(30),
    );
    let from = chi(2026, 11, 1, 0, 0);
    let to = chi(2026, 11, 8, 0, 0);
    assert!(
        find_conflicts(&[a.clone(), adjacent], from, to)
            .unwrap()
            .is_empty()
    );
    let c = find_conflicts(&[a, overlapping], from, to).unwrap();
    assert_eq!(c.len(), 1);
    assert_eq!(
        (c[0].a.schedule_id.as_str(), c[0].b.schedule_id.as_str()),
        ("a", "c")
    );
}
