mod common;

use chrono::{Duration, Weekday};
use common::*;
use focus_core::conflicts::find_conflicts;
use focus_core::plan::{PhaseKind, SessionPlan};
use focus_core::session::*;
use focus_core::timeline::{Occurrence, occurrence_on};

fn brief_occ() -> Occurrence {
    let s = once("a", date(2026, 11, 3), time(18, 0), brief_plan());
    occurrence_on(&s, date(2026, 11, 3)).unwrap().unwrap()
}

fn at(h: u32, m: u32) -> chrono::DateTime<chrono::Utc> {
    chi(2026, 11, 3, h, m)
}

fn ovr(phase_index: usize, requested: chrono::DateTime<chrono::Utc>, delay: u32) -> OverrideRecord {
    OverrideRecord {
        id: "o1".into(),
        phase_index,
        requested_at: requested,
        delay_seconds: delay,
        cancelled_at: None,
    }
}

#[test]
fn lifecycle_without_controls() {
    let occ = brief_occ();
    let c = SessionControls::default();

    let s = evaluate(&occ, &c, at(17, 59));
    assert_eq!(
        (s.status, s.restricting, s.next_transition),
        (SessionStatus::Upcoming, false, Some(at(18, 0)))
    );

    let s = evaluate(&occ, &c, at(18, 0));
    assert_eq!(s.status, SessionStatus::Active);
    assert!(s.restricting);
    assert_eq!(s.phase.as_ref().unwrap().kind, PhaseKind::Focus);
    assert_eq!(s.next_transition, Some(at(18, 40)));

    let s = evaluate(&occ, &c, at(18, 45));
    assert!(!s.restricting, "breaks lift restrictions");
    assert_eq!(s.phase.as_ref().unwrap().kind, PhaseKind::Break);
    assert_eq!(s.next_transition, Some(at(18, 50)));

    let s = evaluate(&occ, &c, at(18, 50));
    assert!(s.restricting, "restrictions resume after the break");

    let s = evaluate(&occ, &c, at(19, 30));
    assert_eq!(
        (s.status, s.restricting, s.next_transition),
        (SessionStatus::Completed, false, None)
    );
}

#[test]
fn override_lifts_only_the_current_focus_period_after_delay() {
    let occ = brief_occ();
    let c = SessionControls {
        overrides: vec![ovr(0, at(18, 10), 90)],
        early_end: None,
    };

    let s = evaluate(&occ, &c, at(18, 11));
    assert!(s.restricting, "still restricting during countdown");
    let eff = at(18, 10) + Duration::seconds(90);
    assert_eq!(
        s.override_state,
        OverrideState::Pending { effective_at: eff }
    );
    assert_eq!(s.next_transition, Some(eff));

    let s = evaluate(&occ, &c, eff);
    assert!(!s.restricting);
    assert_eq!(
        s.override_state,
        OverrideState::Active { until: at(18, 40) }
    );

    // The second focus period is unaffected.
    let s = evaluate(&occ, &c, at(18, 55));
    assert!(s.restricting);
    assert_eq!(s.override_state, OverrideState::None);
}

#[test]
fn override_cancelled_during_countdown_has_no_effect() {
    let occ = brief_occ();
    let mut o = ovr(0, at(18, 10), 90);
    o.cancelled_at = Some(at(18, 10) + Duration::seconds(30));
    let c = SessionControls {
        overrides: vec![o],
        early_end: None,
    };
    let s = evaluate(&occ, &c, at(18, 15));
    assert!(s.restricting);
    assert_eq!(s.override_state, OverrideState::None);
}

#[test]
fn override_can_be_cancelled_after_it_takes_effect() {
    let occ = brief_occ();
    let mut o = ovr(0, at(18, 10), 60);
    o.cancelled_at = Some(at(18, 20));
    let c = SessionControls {
        overrides: vec![o],
        early_end: None,
    };
    assert!(!evaluate(&occ, &c, at(18, 15)).restricting);
    assert!(
        evaluate(&occ, &c, at(18, 20)).restricting,
        "user resumed focus"
    );
}

#[test]
fn ineffective_or_misattributed_overrides_are_ignored() {
    let occ = brief_occ();
    // Countdown would end after the period ends.
    let late = SessionControls {
        overrides: vec![ovr(0, at(18, 39), 120)],
        early_end: None,
    };
    assert!(evaluate(&occ, &late, at(18, 39) + Duration::seconds(50)).restricting);
    // Requested during a break.
    let in_break = SessionControls {
        overrides: vec![ovr(1, at(18, 45), 0)],
        early_end: None,
    };
    assert!(evaluate(&occ, &in_break, at(18, 55)).restricting);
    // Names phase 2 but was requested during phase 0: must not pre-authorize a later period.
    let preauth = SessionControls {
        overrides: vec![ovr(2, at(18, 5), 0)],
        early_end: None,
    };
    assert!(evaluate(&occ, &preauth, at(18, 55)).restricting);
    // An out-of-range delay is clamped to the 15-minute maximum (never longer),
    // so garbage input cannot overflow or extend a lock.
    let huge = SessionControls {
        overrides: vec![ovr(0, at(18, 1), u32::MAX)],
        early_end: None,
    };
    assert!(evaluate(&occ, &huge, at(18, 15)).restricting);
    assert!(!evaluate(&occ, &huge, at(18, 16)).restricting);
}

#[test]
fn emergency_release_is_immediate_and_final() {
    let occ = brief_occ();
    let c = SessionControls {
        overrides: vec![],
        early_end: Some(EarlyEnd {
            at: at(18, 5),
            reason: EarlyEndReason::EmergencyRelease,
        }),
    };
    assert!(evaluate(&occ, &c, at(18, 4)).restricting);
    let s = evaluate(&occ, &c, at(18, 5));
    assert_eq!(
        (s.status, s.restricting, s.next_transition),
        (SessionStatus::EndedEarly, false, None)
    );
    assert!(
        !evaluate(&occ, &c, at(18, 55)).restricting,
        "later focus periods do not resume"
    );
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
