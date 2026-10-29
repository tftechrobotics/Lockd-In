#![allow(dead_code)]

use std::collections::BTreeMap;

use chrono::{DateTime, NaiveDate, NaiveTime, TimeZone, Utc, Weekday};
use chrono_tz::America::Chicago;
use focus_core::plan::SessionPlan;
use focus_core::schedule::{Recurrence, Schedule};

pub fn date(y: i32, m: u32, d: u32) -> NaiveDate {
    NaiveDate::from_ymd_opt(y, m, d).unwrap()
}

pub fn time(h: u32, m: u32) -> NaiveTime {
    NaiveTime::from_hms_opt(h, m, 0).unwrap()
}

/// A Chicago local wall-clock time as an instant (must be unambiguous).
pub fn chi(y: i32, mo: u32, d: u32, h: u32, mi: u32) -> DateTime<Utc> {
    Chicago
        .with_ymd_and_hms(y, mo, d, h, mi, 0)
        .single()
        .unwrap()
        .with_timezone(&Utc)
}

pub fn once(id: &str, d: NaiveDate, start: NaiveTime, plan: SessionPlan) -> Schedule {
    Schedule {
        contract_version: focus_core::CONTRACT_VERSION,
        id: id.into(),
        name: "Study".into(),
        time_zone: "America/Chicago".into(),
        start_time: start,
        plan,
        recurrence: Recurrence::Once { date: d },
        exceptions: BTreeMap::new(),
    }
}

pub fn weekly(
    id: &str,
    days: &[Weekday],
    from: NaiveDate,
    start: NaiveTime,
    plan: SessionPlan,
) -> Schedule {
    Schedule {
        contract_version: focus_core::CONTRACT_VERSION,
        id: id.into(),
        name: "Weeknights".into(),
        time_zone: "America/Chicago".into(),
        start_time: start,
        plan,
        recurrence: Recurrence::Weekly {
            days: days.to_vec(),
            starts_on: from,
            ends_on: None,
        },
        exceptions: BTreeMap::new(),
    }
}

/// The brief's example: focus 6:00-6:40, break 6:40-6:50, focus 6:50-7:30.
pub fn brief_plan() -> SessionPlan {
    SessionPlan::rounds(2, 40, 10)
}
