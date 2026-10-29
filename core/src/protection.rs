//! Truthful protection status: combines what each device *acknowledged* with
//! what it *observed* about its own enforcement. The UI may only claim a
//! device is protected when both are recent and positive.

use chrono::{DateTime, Duration, Utc};
use serde::{Deserialize, Serialize};

/// How recent an acknowledgment / enforcement check must be to count.
pub const DEFAULT_FRESHNESS_SECONDS: i64 = 120;

/// What a device's enforcement layer last observed about itself.
#[derive(Debug, Clone, PartialEq, Eq, Serialize, Deserialize)]
#[serde(tag = "type", rename_all = "snake_case", deny_unknown_fields)]
pub enum Enforcement {
    /// All required capabilities present and the enforcer self-check passed.
    Verified { checked_at: DateTime<Utc> },
    /// Running, but something is missing (e.g. a permission was revoked).
    Degraded {
        checked_at: DateTime<Utc>,
        reason: String,
    },
    /// Cannot enforce at all on this device right now.
    Unavailable { reason: String },
    /// No report received yet.
    Unknown,
}

#[derive(Debug, Clone, PartialEq, Eq, Serialize, Deserialize)]
#[serde(deny_unknown_fields)]
pub struct DeviceReport {
    pub device_id: String,
    pub device_name: String,
    /// Policy version this device should be running.
    pub desired_version: u64,
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub acked_version: Option<u64>,
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub acked_at: Option<DateTime<Utc>>,
    pub enforcement: Enforcement,
}

#[derive(Debug, Clone, PartialEq, Eq, Serialize, Deserialize)]
#[serde(tag = "type", rename_all = "snake_case")]
pub enum DeviceProtection {
    Protected,
    /// The device has not confirmed the current schedule yet.
    NotYetReceived,
    /// We have not heard from the device recently; it may be offline.
    Stale {
        last_heard: Option<DateTime<Utc>>,
    },
    Degraded {
        reason: String,
    },
    Unavailable {
        reason: String,
    },
}

#[derive(Debug, Clone, PartialEq, Eq, Serialize, Deserialize)]
pub struct DeviceStatus {
    pub device_id: String,
    pub device_name: String,
    pub protection: DeviceProtection,
}

#[derive(Debug, Clone, PartialEq, Eq, Serialize, Deserialize)]
pub struct GroupStatus {
    pub devices: Vec<DeviceStatus>,
    /// True only if there is at least one device and every device is `Protected`.
    pub all_protected: bool,
}

pub fn device_protection(
    r: &DeviceReport,
    now: DateTime<Utc>,
    freshness: Duration,
) -> DeviceProtection {
    let fresh = |t: DateTime<Utc>| t <= now + Duration::seconds(5) && now - t <= freshness;
    match r.acked_version {
        Some(v) if v >= r.desired_version => {}
        _ => return DeviceProtection::NotYetReceived,
    }
    if !r.acked_at.is_some_and(fresh) {
        return DeviceProtection::Stale {
            last_heard: r.acked_at,
        };
    }
    match &r.enforcement {
        Enforcement::Verified { checked_at } if fresh(*checked_at) => DeviceProtection::Protected,
        Enforcement::Verified { checked_at } => DeviceProtection::Stale {
            last_heard: Some(*checked_at),
        },
        Enforcement::Degraded { reason, .. } => DeviceProtection::Degraded {
            reason: reason.clone(),
        },
        Enforcement::Unavailable { reason } => DeviceProtection::Unavailable {
            reason: reason.clone(),
        },
        Enforcement::Unknown => DeviceProtection::Stale {
            last_heard: r.acked_at,
        },
    }
}

pub fn group_status(
    reports: &[DeviceReport],
    now: DateTime<Utc>,
    freshness: Duration,
) -> GroupStatus {
    let devices: Vec<DeviceStatus> = reports
        .iter()
        .map(|r| DeviceStatus {
            device_id: r.device_id.clone(),
            device_name: r.device_name.clone(),
            protection: device_protection(r, now, freshness),
        })
        .collect();
    let all_protected = !devices.is_empty()
        && devices
            .iter()
            .all(|d| d.protection == DeviceProtection::Protected);
    GroupStatus {
        devices,
        all_protected,
    }
}
