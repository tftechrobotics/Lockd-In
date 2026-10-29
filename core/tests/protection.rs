use chrono::{DateTime, Duration, TimeZone, Utc};
use focus_core::protection::*;

fn now() -> DateTime<Utc> {
    Utc.with_ymd_and_hms(2026, 11, 3, 0, 0, 0).unwrap()
}

fn device(report: Option<(i64, u64, Enforcement)>) -> DeviceReport {
    DeviceReport {
        device_id: "d".into(),
        device_name: "Phone".into(),
        desired_version: 7,
        last_report: report.map(|(secs_ago, applied, enforcement)| StatusReport {
            reported_at: now() - Duration::seconds(secs_ago),
            applied_version: applied,
            enforcement,
        }),
    }
}

fn p(r: DeviceReport) -> DeviceProtection {
    device_protection(&r, now(), Duration::seconds(DEFAULT_FRESHNESS_SECONDS))
}

#[test]
fn protected_needs_fresh_report_current_version_and_verified() {
    assert_eq!(
        p(device(Some((10, 7, Enforcement::Verified)))),
        DeviceProtection::Protected
    );
    assert_eq!(
        p(device(Some((10, 6, Enforcement::Verified)))),
        DeviceProtection::NotYetApplied { applied_version: 6 }
    );
    assert_eq!(
        p(device(Some((
            10,
            7,
            Enforcement::Degraded {
                reason: "Usage access revoked".into()
            }
        )))),
        DeviceProtection::Degraded {
            reason: "Usage access revoked".into()
        }
    );
    assert_eq!(
        p(device(Some((
            10,
            7,
            Enforcement::NotEnforcing {
                reason: "Agent stopped".into()
            }
        )))),
        DeviceProtection::NotEnforcing {
            reason: "Agent stopped".into()
        }
    );
}

#[test]
fn disconnected_peer_is_unknown_not_protected_or_stopped() {
    // Last report said "protected", but it is 15 minutes old.
    let old = p(device(Some((900, 7, Enforcement::Verified))));
    assert!(matches!(
        old,
        DeviceProtection::Unknown {
            last_report: Some(_)
        }
    ));
    // Last report said "not enforcing", but it is old: still unknown, not "stopped".
    let old_bad = p(device(Some((
        900,
        7,
        Enforcement::NotEnforcing { reason: "x".into() },
    ))));
    assert!(matches!(old_bad, DeviceProtection::Unknown { .. }));
    // Never heard from.
    assert_eq!(
        p(device(None)),
        DeviceProtection::Unknown { last_report: None }
    );
    // A report "from the future" (bad clock) is not trusted.
    assert!(matches!(
        p(device(Some((-3600, 7, Enforcement::Verified)))),
        DeviceProtection::Unknown { .. }
    ));
}

#[test]
fn group_is_protected_only_when_every_device_is() {
    let f = Duration::seconds(DEFAULT_FRESHNESS_SECONDS);
    let good = device(Some((5, 7, Enforcement::Verified)));
    let offline = device(Some((900, 7, Enforcement::Verified)));

    let both = group_status(&[good.clone(), good.clone()], now(), f);
    assert!(both.all_protected && !both.any_unknown);
    let mixed = group_status(&[good, offline], now(), f);
    assert!(!mixed.all_protected && mixed.any_unknown);
    assert!(
        !group_status(&[], now(), f).all_protected,
        "no devices is not 'protected'"
    );
}
