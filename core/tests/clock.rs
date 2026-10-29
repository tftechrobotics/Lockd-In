mod common;

use chrono::{DateTime, Duration, Utc};
use common::*;
use lockdin_core::clock::*;
use lockdin_core::plan::SessionPlan;
use lockdin_core::session::{SessionControls, evaluate};
use lockdin_core::timeline::occurrence_on;
use proptest::prelude::*;

/// Session start: 18:00 Chicago on 3 Nov 2026.
fn t0() -> DateTime<Utc> {
    chi(2026, 11, 3, 18, 0)
}

fn sample(wall_offset_s: i64, boot: &str, since_boot_s: u64) -> ClockSample {
    ClockSample {
        wall: t0() + Duration::seconds(wall_offset_s),
        mono: MonoInstant {
            boot_id: boot.into(),
            since_boot_ms: since_boot_s * 1000,
        },
    }
}

#[test]
fn ordinary_time_passes_normally() {
    let mut c = SessionClock::start(&sample(0, "b1", 1000));
    let r = c.observe(&sample(600, "b1", 1600));
    assert_eq!(r.session_now, t0() + Duration::seconds(600));
    assert_eq!(r.basis, Basis::Monotonic);
    assert_eq!(r.event, None);
}

#[test]
fn small_drift_is_not_reported_as_a_clock_change() {
    let mut c = SessionClock::start(&sample(0, "b1", 1000));
    let r = c.observe(&sample(603, "b1", 1600));
    assert_eq!(r.session_now, t0() + Duration::seconds(600));
    assert_eq!(r.event, None);
}

#[test]
fn moving_the_clock_forward_does_not_shorten_the_session() {
    let mut c = SessionClock::start(&sample(0, "b1", 1000));
    // Ten real minutes pass, but the user moved the clock ahead two hours.
    let r = c.observe(&sample(600 + 7200, "b1", 1600));
    assert_eq!(r.session_now, t0() + Duration::seconds(600));
    assert_eq!(
        r.event,
        Some(ClockEvent::WallClockChanged {
            delta_seconds: 7200
        })
    );
}

#[test]
fn moving_the_clock_back_does_not_extend_the_session() {
    let mut c = SessionClock::start(&sample(0, "b1", 1000));
    let r = c.observe(&sample(600 - 3600, "b1", 1600));
    assert_eq!(r.session_now, t0() + Duration::seconds(600));
    assert_eq!(
        r.event,
        Some(ClockEvent::WallClockChanged {
            delta_seconds: -3600
        })
    );
}

#[test]
fn sleep_counts_as_elapsed_time() {
    // The monotonic clock is sleep-inclusive (elapsedRealtime / QueryInterruptTime).
    let mut c = SessionClock::start(&sample(0, "b1", 1000));
    let r = c.observe(&sample(3 * 3600, "b1", 1000 + 3 * 3600));
    assert_eq!(r.session_now, t0() + Duration::hours(3));
    assert_eq!(r.event, None);
}

#[test]
fn reboot_with_honest_clock_follows_wall_clock() {
    let mut c = SessionClock::start(&sample(0, "b1", 1000));
    c.observe(&sample(600, "b1", 1600));
    // Off for 20 minutes, then 60 s since boot.
    let r = c.observe(&sample(600 + 1200 + 60, "b2", 60));
    assert_eq!(r.session_now, t0() + Duration::seconds(1860));
    assert_eq!(r.basis, Basis::AfterReboot);
    assert_eq!(
        r.event,
        Some(ClockEvent::RebootedWallAhead {
            ahead_seconds: 1200
        })
    );
}

#[test]
fn reboot_with_clock_set_back_uses_provable_lower_bound() {
    let mut c = SessionClock::start(&sample(0, "b1", 1000));
    c.observe(&sample(600, "b1", 1600));
    // Clock now claims an hour *before* the session started.
    let r = c.observe(&sample(-3600, "b2", 90));
    assert_eq!(r.session_now, t0() + Duration::seconds(690));
    assert_eq!(
        r.event,
        Some(ClockEvent::RebootedWallBehind {
            behind_seconds: 4290
        })
    );
}

#[test]
fn after_reboot_the_new_boot_is_the_anchor() {
    let mut c = SessionClock::start(&sample(0, "b1", 1000));
    c.observe(&sample(-3600, "b2", 90)); // session_now = t0 + 90
    let r = c.observe(&sample(-3600 + 7200, "b2", 150)); // forward jump after reboot
    assert_eq!(r.session_now, t0() + Duration::seconds(150));
    assert!(matches!(r.event, Some(ClockEvent::WallClockChanged { .. })));
}

#[test]
fn monotonic_going_backwards_is_reported_and_never_rewinds() {
    let mut c = SessionClock::start(&sample(0, "b1", 1000));
    c.observe(&sample(600, "b1", 1600));
    let r = c.observe(&sample(610, "b1", 5));
    assert_eq!(r.event, Some(ClockEvent::MonotonicWentBackwards));
    assert!(r.session_now >= t0() + Duration::seconds(605));
}

#[test]
fn absurd_monotonic_values_saturate_instead_of_panicking() {
    let mut c = SessionClock::start(&sample(0, "b1", 0));
    let r = c.observe(&ClockSample {
        wall: t0(),
        mono: MonoInstant {
            boot_id: "b1".into(),
            since_boot_ms: u64::MAX,
        },
    });
    assert!(r.session_now > t0());
}

#[test]
fn state_round_trips_through_json() {
    let mut c = SessionClock::start(&sample(0, "b1", 1000));
    c.observe(&sample(600, "b1", 1600));
    let json = serde_json::to_string(&c).unwrap();
    let back: SessionClock = serde_json::from_str(&json).unwrap();
    assert_eq!(back, c);
    assert!(
        serde_json::from_str::<SessionClock>(
            &json.replace("\"checkpoint\"", "\"extra\":1,\"checkpoint\"")
        )
        .is_err()
    );
}

#[test]
fn forward_clock_jump_does_not_end_focus_early_end_to_end() {
    // 25-minute focus, 5-minute break, 25-minute focus, starting at t0.
    let s = once(
        "a",
        date(2026, 11, 3),
        time(18, 0),
        SessionPlan::rounds(2, 25, 5),
    );
    let occ = occurrence_on(&s, date(2026, 11, 3)).unwrap().unwrap();
    let controls = SessionControls {
        overrides: vec![],
        emergency_recovery: None,
    };
    let mut c = SessionClock::start(&sample(0, "b1", 1000));
    // Ten real minutes in, the clock is moved ahead three hours.
    let r = c.observe(&sample(600 + 3 * 3600, "b1", 1600));
    assert!(evaluate(&occ, &controls, r.session_now).restricting);
    // Using the raw wall clock would have released.
    assert!(!evaluate(&occ, &controls, t0() + Duration::seconds(600 + 3 * 3600)).restricting);
}

proptest! {
    /// While the device stays up, session time depends only on the monotonic
    /// clock and never decreases, whatever the wall clock says.
    #[test]
    fn same_boot_session_time_ignores_wall_clock(
        steps in prop::collection::vec((0u64..7200, -86_400i64..86_400), 1..40)
    ) {
        let mut c = SessionClock::start(&sample(0, "b", 0));
        let mut mono = 0u64;
        let mut prev = t0();
        for (advance, wall_offset) in steps {
            mono += advance;
            let r = c.observe(&sample(wall_offset, "b", mono));
            prop_assert_eq!(r.session_now, t0() + Duration::seconds(mono as i64));
            prop_assert!(r.session_now >= prev);
            prev = r.session_now;
        }
    }

    /// Across reboots session time never decreases and is never earlier than
    /// the time that provably passed.
    #[test]
    fn reboots_never_rewind_or_undercount(
        boots in prop::collection::vec((0u64..7200, -86_400i64..86_400), 1..20)
    ) {
        let mut c = SessionClock::start(&sample(0, "b0", 0));
        let mut provable = 0i64;
        let mut prev = t0();
        for (i, (since_boot, wall_offset)) in boots.into_iter().enumerate() {
            provable += since_boot as i64;
            let r = c.observe(&sample(wall_offset, &format!("b{}", i + 1), since_boot));
            prop_assert!(r.session_now >= prev);
            prop_assert!(r.session_now >= t0() + Duration::seconds(provable));
            prev = r.session_now;
        }
    }
}
