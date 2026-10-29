//! Property tests for the safety invariants listed in lib.rs.

mod common;

use chrono::{Duration, NaiveDate, NaiveTime};
use common::*;
use focus_core::plan::{MAX_SESSION_MINUTES, PhaseKind, Segment, SessionPlan};
use focus_core::schedule::Recurrence;
use focus_core::session::{
    EmergencyRecovery, OverrideRecord, OverrideScope, SessionControls, evaluate,
};
use focus_core::timeline::occurrence_on;
use proptest::prelude::*;

fn plan_strategy() -> impl Strategy<Value = SessionPlan> {
    (1usize..6, prop::collection::vec((1u32..=120, 1u32..=30), 6)).prop_map(|(rounds, lens)| {
        let mut segments = Vec::new();
        for (i, (f, b)) in lens.into_iter().take(rounds).enumerate() {
            if i > 0 {
                segments.push(Segment {
                    kind: PhaseKind::Break,
                    minutes: b,
                });
            }
            segments.push(Segment {
                kind: PhaseKind::Focus,
                minutes: f,
            });
        }
        SessionPlan { segments }
    })
}

const ZONES: &[&str] = &[
    "America/Chicago",
    "Europe/London",
    "Australia/Lord_Howe",
    "Asia/Kolkata",
    "UTC",
];

proptest! {
    #![proptest_config(ProptestConfig::with_cases(512))]

    #[test]
    fn invariants_hold(
        plan in plan_strategy(),
        zone in prop::sample::select(ZONES),
        day in 0i64..730,
        minute_of_day in 0u32..1440,
        probe_offset in -120i64..900,
        override_at in prop::option::of((any::<bool>(), 0usize..11, 0i64..300, 0u32..1200)),
        emergency_at in prop::option::of(0i64..900),
    ) {
        let d = NaiveDate::from_ymd_opt(2026, 1, 1).unwrap() + Duration::days(day);
        let t = NaiveTime::from_hms_opt(minute_of_day / 60, minute_of_day % 60, 0).unwrap();
        let mut s = once("p", d, t, plan);
        s.time_zone = zone.to_string();
        s.recurrence = Recurrence::Once { date: d };
        prop_assert!(s.validate().is_ok());
        let occ = occurrence_on(&s, d).unwrap().unwrap();

        // Finite and contiguous.
        prop_assert!(occ.end - occ.start <= Duration::minutes(i64::from(MAX_SESSION_MINUTES)));
        prop_assert_eq!(occ.phases.first().unwrap().start, occ.start);
        prop_assert_eq!(occ.phases.last().unwrap().end, occ.end);
        for w in occ.phases.windows(2) { prop_assert_eq!(w[0].end, w[1].start); }

        let mut controls = SessionControls::default();
        if let Some((whole, idx, mins, delay)) = override_at {
            controls.overrides.push(OverrideRecord {
                id: "o".into(), phase_index: idx,
                scope: if whole { OverrideScope::RestOfSession } else { OverrideScope::CurrentFocusPeriod },
                requested_at: occ.start + Duration::minutes(mins), delay_seconds: delay, cancelled_at: None,
            });
        }
        if let Some(m) = emergency_at {
            controls.emergency_recovery = Some(EmergencyRecovery { at: occ.start + Duration::minutes(m) });
        }

        let now = occ.start + Duration::minutes(probe_offset);
        let snap = evaluate(&occ, &controls, now);

        // Restriction only ever inside a focus phase of the occurrence.
        if snap.restricting {
            prop_assert!(occ.contains(now));
            prop_assert_eq!(occ.phase_at(now).unwrap().kind, PhaseKind::Focus);
            if let Some(e) = &controls.emergency_recovery { prop_assert!(now < e.at); }
            prop_assert_eq!(snap.release_reason, None);
        }
        // Every "not restricting" answer says why.
        if !snap.restricting { prop_assert!(snap.release_reason.is_some()); }
        // Never restricting outside the occurrence.
        if now < occ.start || now >= occ.end { prop_assert!(!snap.restricting); }
        // Timers always move forward and never past the end.
        if let Some(n) = snap.next_transition { prop_assert!(n > now && n <= occ.end); }
        // Without controls, the answer is exactly "in a focus phase".
        let plain = evaluate(&occ, &SessionControls::default(), now);
        prop_assert_eq!(plain.restricting, occ.phase_at(now).is_some_and(|p| p.kind == PhaseKind::Focus));
    }
}
