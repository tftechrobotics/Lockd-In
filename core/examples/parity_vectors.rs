//! Writes session-evaluation test vectors as JSON, so other implementations
//! (the web app's TypeScript engine) can prove they match this crate exactly.
//!
//!   cargo run -q -p lockdin-core --example parity_vectors > apps/web/src/engine/parity-vectors.json
//!
//! All instants are seconds relative to the session start.

use chrono::{DateTime, Duration, NaiveDate, NaiveTime, Utc};
use lockdin_core::plan::{PhaseKind, Segment, SessionPlan};
use lockdin_core::schedule::{Recurrence, Schedule};
use lockdin_core::session::*;
use lockdin_core::timeline::occurrence_on;
use serde_json::{Value, json};

fn main() {
    let start_date = NaiveDate::from_ymd_opt(2026, 11, 3).unwrap();
    let start_time = NaiveTime::from_hms_opt(18, 0, 0).unwrap();
    let t0: DateTime<Utc> = start_date.and_time(start_time).and_utc();
    let at = |s: i64| t0 + Duration::seconds(s);
    let ovr = |id: &str,
               scope: OverrideScope,
               phase: usize,
               req: i64,
               delay: u32,
               cancel: Option<i64>| OverrideRecord {
        id: id.into(),
        scope,
        phase_index: phase,
        requested_at: at(req),
        delay_seconds: delay,
        cancelled_at: cancel.map(at),
    };
    use OverrideScope::{CurrentFocusPeriod as Period, RestOfSession as Rest};

    let custom = SessionPlan {
        segments: vec![
            Segment {
                kind: PhaseKind::Focus,
                minutes: 25,
            },
            Segment {
                kind: PhaseKind::Break,
                minutes: 5,
            },
            Segment {
                kind: PhaseKind::Focus,
                minutes: 50,
            },
            Segment {
                kind: PhaseKind::Break,
                minutes: 15,
            },
            Segment {
                kind: PhaseKind::Focus,
                minutes: 10,
            },
        ],
    };
    let cases: Vec<(&str, SessionPlan, SessionControls)> = vec![
        (
            "ten_minute_start",
            SessionPlan::single(10),
            SessionControls::default(),
        ),
        (
            "two_rounds_40_10",
            SessionPlan::rounds(2, 40, 10),
            SessionControls::default(),
        ),
        ("custom_cycles", custom, SessionControls::default()),
        (
            "period_override_takes_effect",
            SessionPlan::rounds(2, 40, 10),
            SessionControls {
                overrides: vec![ovr("a", Period, 0, 600, 90, None)],
                emergency_recovery: None,
            },
        ),
        (
            "period_override_cancelled",
            SessionPlan::rounds(2, 40, 10),
            SessionControls {
                overrides: vec![ovr("a", Period, 0, 600, 90, Some(650))],
                emergency_recovery: None,
            },
        ),
        (
            "period_override_too_late",
            SessionPlan::rounds(2, 40, 10),
            SessionControls {
                overrides: vec![ovr("a", Period, 0, 2350, 90, None)],
                emergency_recovery: None,
            },
        ),
        (
            "period_override_wrong_phase_ignored",
            SessionPlan::rounds(2, 40, 10),
            SessionControls {
                overrides: vec![ovr("a", Period, 2, 600, 90, None)],
                emergency_recovery: None,
            },
        ),
        (
            "session_override",
            SessionPlan::rounds(2, 40, 10),
            SessionControls {
                overrides: vec![ovr("a", Rest, 0, 1200, 90, None)],
                emergency_recovery: None,
            },
        ),
        (
            "session_override_in_break",
            SessionPlan::rounds(2, 40, 10),
            SessionControls {
                overrides: vec![ovr("a", Rest, 1, 2500, 90, None)],
                emergency_recovery: None,
            },
        ),
        (
            "delay_clamped_to_15_min",
            SessionPlan::rounds(2, 40, 10),
            SessionControls {
                overrides: vec![ovr("a", Rest, 0, 60, 100_000, None)],
                emergency_recovery: None,
            },
        ),
        (
            "emergency_recovery",
            SessionPlan::rounds(2, 40, 10),
            SessionControls {
                overrides: vec![ovr("a", Rest, 0, 300, 90, None)],
                emergency_recovery: Some(EmergencyRecovery { at: at(330) }),
            },
        ),
    ];

    let mut out = Vec::new();
    for (name, plan, controls) in cases {
        let schedule = Schedule {
            contract_version: lockdin_core::CONTRACT_VERSION,
            id: name.into(),
            name: name.into(),
            time_zone: "UTC".into(),
            start_time,
            plan: plan.clone(),
            recurrence: Recurrence::Once { date: start_date },
            exceptions: Default::default(),
        };
        let occ = occurrence_on(&schedule, start_date).unwrap().unwrap();
        let total = i64::from(plan.total_minutes()) * 60;
        let mut times: Vec<i64> = vec![-60, -1, 0, 1, total - 1, total, total + 60];
        for p in &occ.phases {
            for d in [-1i64, 0, 1] {
                times.push((p.end - t0).num_seconds() + d);
            }
        }
        for o in &controls.overrides {
            for d in [-1i64, 0, 1] {
                times.push((o.effective_at() - t0).num_seconds() + d);
                times.push((o.requested_at - t0).num_seconds() + d);
                if let Some(c) = o.cancelled_at {
                    times.push((c - t0).num_seconds() + d);
                }
            }
        }
        if let Some(e) = &controls.emergency_recovery {
            for d in [-1i64, 0, 1] {
                times.push((e.at - t0).num_seconds() + d);
            }
        }
        times.extend((0..=total).step_by(97));
        times.sort_unstable();
        times.dedup();

        let samples: Vec<Value> = times
            .iter()
            .map(|&t| {
                let s = evaluate(&occ, &controls, at(t));
                let ov = match &s.override_state {
                    OverrideState::None => json!({"type": "none"}),
                    OverrideState::Pending { scope, effective_at } => json!({"type": "pending", "scope": scope, "effective_at": (*effective_at - t0).num_seconds()}),
                    OverrideState::ActiveForFocusPeriod { until } => json!({"type": "active_for_focus_period", "until": (*until - t0).num_seconds()}),
                };
                json!({
                    "t": t,
                    "status": s.status,
                    "restricting": s.restricting,
                    "release_reason": s.release_reason,
                    "phase_index": s.phase.as_ref().map(|p| p.index),
                    "override_state": ov,
                    "next_transition": s.next_transition.map(|n| (n - t0).num_seconds()),
                })
            })
            .collect();

        let controls_json = json!({
            "overrides": controls.overrides.iter().map(|o| json!({
                "id": o.id, "scope": o.scope, "phase_index": o.phase_index,
                "requested_at": (o.requested_at - t0).num_seconds(), "delay_seconds": o.delay_seconds,
                "cancelled_at": o.cancelled_at.map(|c| (c - t0).num_seconds()),
            })).collect::<Vec<_>>(),
            "emergency_recovery": controls.emergency_recovery.as_ref().map(|e| (e.at - t0).num_seconds()),
        });
        out.push(json!({ "name": name, "segments": plan.segments, "controls": controls_json, "samples": samples }));
    }
    println!(
        "{}",
        serde_json::to_string_pretty(
            &json!({ "generated_by": "core/examples/parity_vectors.rs", "cases": out })
        )
        .unwrap()
    );
}
