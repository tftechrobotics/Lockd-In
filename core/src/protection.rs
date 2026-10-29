//! Truthful per-device protection status for the UI and paired devices.
//!
//! Rules:
//! * Only a *recent* report from the device itself can say anything definite.
//! * A device we have not heard from recently is **Unknown** — neither
//!   "protected" nor "stopped". Its last known report is shown as history.
//! * "Protected" needs a recent report that the device applied the current
//!   policy version *and* passed its enforcement self-check.
//! * Freshness thresholds are targets until measured on real devices (M1/M5).

use chrono::{DateTime, Duration, Utc};
use serde::{Deserialize, Serialize};

/// How recent a report must be to count. A target, not yet measured.
pub const DEFAULT_FRESHNESS_SECONDS: i64 = 120;
/// Tolerated clock difference before a "future" report is distrusted.
pub const CLOCK_TOLERANCE_SECONDS: i64 = 5;

/// What a device's enforcement layer observed about itself at report time.
#[derive(Debug, Clone, PartialEq, Eq, Serialize, Deserialize)]
#[serde(tag = "type", rename_all = "snake_case", deny_unknown_fields)]
pub enum Enforcement {
    /// All required capabilities present and the enforcer self-check passed.
    Verified,
    /// Running, but something required is missing (e.g. a permission was revoked).
    Degraded { reason: String },
    /// Not enforcing at all (agent stopped, capability absent).
    NotEnforcing { reason: String },
}

/// One status message sent by a device about itself.
#[derive(Debug, Clone, PartialEq, Eq, Serialize, Deserialize)]
#[serde(deny_unknown_fields)]
pub struct StatusReport {
    pub reported_at: DateTime<Utc>,
    pub applied_version: u64,
    pub enforcement: Enforcement,
}

#[derive(Debug, Clone, PartialEq, Eq, Serialize, Deserialize)]
#[serde(deny_unknown_fields)]
pub struct DeviceReport {
    pub device_id: String,
    pub device_name: String,
    /// Policy version this device should be running.
    pub desired_version: u64,
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub last_report: Option<StatusReport>,
}

#[derive(Debug, Clone, PartialEq, Eq, Serialize, Deserialize)]
#[serde(tag = "type", rename_all = "snake_case")]
pub enum DeviceProtection {
    Protected,
    /// In contact, but has not applied the current schedule yet.
    NotYetApplied {
        applied_version: u64,
    },
    Degraded {
        reason: String,
    },
    NotEnforcing {
        reason: String,
    },
    /// No recent report (offline, asleep, or never connected). Not assumed
    /// protected, not assumed stopped.
    Unknown {
        last_report: Option<StatusReport>,
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
    /// True if any device's status is `Unknown`.
    pub any_unknown: bool,
}

pub fn device_protection(
    r: &DeviceReport,
    now: DateTime<Utc>,
    freshness: Duration,
) -> DeviceProtection {
    let Some(rep) = &r.last_report else {
        return DeviceProtection::Unknown { last_report: None };
    };
    let fresh = rep.reported_at <= now + Duration::seconds(CLOCK_TOLERANCE_SECONDS)
        && now - rep.reported_at <= freshness;
    if !fresh {
        return DeviceProtection::Unknown {
            last_report: Some(rep.clone()),
        };
    }
    if rep.applied_version < r.desired_version {
        return DeviceProtection::NotYetApplied {
            applied_version: rep.applied_version,
        };
    }
    match &rep.enforcement {
        Enforcement::Verified => DeviceProtection::Protected,
        Enforcement::Degraded { reason } => DeviceProtection::Degraded {
            reason: reason.clone(),
        },
        Enforcement::NotEnforcing { reason } => DeviceProtection::NotEnforcing {
            reason: reason.clone(),
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
    let any_unknown = devices
        .iter()
        .any(|d| matches!(d.protection, DeviceProtection::Unknown { .. }));
    GroupStatus {
        devices,
        all_protected,
        any_unknown,
    }
}
