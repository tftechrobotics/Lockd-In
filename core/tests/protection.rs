use chrono::{Duration, TimeZone, Utc};
use focus_core::protection::*;

fn report(
    acked: Option<u64>,
    acked_secs_ago: Option<i64>,
    enforcement: Enforcement,
) -> DeviceReport {
    let now = Utc.with_ymd_and_hms(2026, 11, 3, 0, 0, 0).unwrap();
    DeviceReport {
        device_id: "d".into(),
        device_name: "Phone".into(),
        desired_version: 7,
        acked_version: acked,
        acked_at: acked_secs_ago.map(|s| now - Duration::seconds(s)),
        enforcement,
    }
}

#[test]
fn protection_requires_current_ack_fresh_ack_and_fresh_verification() {
    let now = Utc.with_ymd_and_hms(2026, 11, 3, 0, 0, 0).unwrap();
    let f = Duration::seconds(DEFAULT_FRESHNESS_SECONDS);
    let verified = |ago: i64| Enforcement::Verified {
        checked_at: now - Duration::seconds(ago),
    };

    let p = |r: DeviceReport| device_protection(&r, now, f);
    assert_eq!(
        p(report(Some(7), Some(10), verified(10))),
        DeviceProtection::Protected
    );
    assert_eq!(
        p(report(Some(6), Some(10), verified(10))),
        DeviceProtection::NotYetReceived
    );
    assert_eq!(
        p(report(None, None, verified(10))),
        DeviceProtection::NotYetReceived
    );
    assert!(matches!(
        p(report(Some(7), Some(600), verified(10))),
        DeviceProtection::Stale { .. }
    ));
    assert!(matches!(
        p(report(Some(7), Some(10), verified(600))),
        DeviceProtection::Stale { .. }
    ));
    assert!(matches!(
        p(report(Some(7), Some(10), Enforcement::Unknown)),
        DeviceProtection::Stale { .. }
    ));
    // A timestamp from the future (bad clock) is not trusted as fresh.
    assert!(matches!(
        p(report(Some(7), Some(-3600), verified(10))),
        DeviceProtection::Stale { .. }
    ));
    assert_eq!(
        p(report(
            Some(7),
            Some(10),
            Enforcement::Degraded {
                checked_at: now,
                reason: "Usage access revoked".into()
            }
        )),
        DeviceProtection::Degraded {
            reason: "Usage access revoked".into()
        }
    );
}

#[test]
fn group_is_protected_only_when_every_device_is() {
    let now = Utc.with_ymd_and_hms(2026, 11, 3, 0, 0, 0).unwrap();
    let f = Duration::seconds(DEFAULT_FRESHNESS_SECONDS);
    let good = report(Some(7), Some(5), Enforcement::Verified { checked_at: now });
    let offline = report(
        Some(7),
        Some(900),
        Enforcement::Verified {
            checked_at: now - Duration::seconds(900),
        },
    );

    assert!(group_status(&[good.clone(), good.clone()], now, f).all_protected);
    assert!(!group_status(&[good, offline], now, f).all_protected);
    assert!(
        !group_status(&[], now, f).all_protected,
        "no devices is not 'protected'"
    );
}
