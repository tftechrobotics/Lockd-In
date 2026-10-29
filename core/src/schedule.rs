//! A schedule says *when* a plan runs: once, or weekly, with per-day exceptions.
//!
//! Times are stored as local wall-clock time plus an IANA time zone, because
//! that is what the user means ("weekdays at 6:00 pm"). Conversion to absolute
//! instants happens in [`crate::timeline`].

use std::collections::BTreeMap;
use std::str::FromStr;

use chrono::{Datelike, NaiveDate, NaiveDateTime, NaiveTime, Weekday};
use chrono_tz::Tz;
use serde::{Deserialize, Serialize};

use crate::error::ValidationError;
use crate::plan::SessionPlan;

#[derive(Debug, Clone, PartialEq, Eq, Serialize, Deserialize)]
#[serde(tag = "type", rename_all = "snake_case", deny_unknown_fields)]
pub enum Recurrence {
    /// Runs one time, on `date`.
    Once { date: NaiveDate },
    /// Runs on each listed weekday from `starts_on` through `ends_on` (inclusive).
    Weekly {
        days: Vec<Weekday>,
        starts_on: NaiveDate,
        #[serde(default, skip_serializing_if = "Option::is_none")]
        ends_on: Option<NaiveDate>,
    },
}

/// A change to one occurrence, keyed by the date it would normally run.
#[derive(Debug, Clone, PartialEq, Eq, Serialize, Deserialize)]
#[serde(tag = "type", rename_all = "snake_case", deny_unknown_fields)]
pub enum Exception {
    /// Do not run on this date.
    Skip,
    /// Run at a different local date/time instead.
    Reschedule { start: NaiveDateTime },
}

#[derive(Debug, Clone, PartialEq, Eq, Serialize, Deserialize)]
#[serde(deny_unknown_fields)]
pub struct Schedule {
    pub contract_version: u32,
    pub id: String,
    pub name: String,
    /// IANA zone name, e.g. "America/Chicago".
    pub time_zone: String,
    pub start_time: NaiveTime,
    pub plan: SessionPlan,
    pub recurrence: Recurrence,
    #[serde(default, skip_serializing_if = "BTreeMap::is_empty")]
    pub exceptions: BTreeMap<NaiveDate, Exception>,
}

impl Schedule {
    pub fn tz(&self) -> Result<Tz, ValidationError> {
        Tz::from_str(&self.time_zone)
            .map_err(|_| ValidationError::UnknownTimeZone(self.time_zone.clone()))
    }

    /// True if the recurrence (ignoring exceptions) produces an occurrence on `date`.
    pub fn is_scheduled_on(&self, date: NaiveDate) -> bool {
        match &self.recurrence {
            Recurrence::Once { date: d } => *d == date,
            Recurrence::Weekly {
                days,
                starts_on,
                ends_on,
            } => {
                date >= *starts_on
                    && ends_on.is_none_or(|e| date <= e)
                    && days.contains(&date.weekday())
            }
        }
    }

    pub fn validate(&self) -> Result<(), ValidationError> {
        if self.contract_version != crate::CONTRACT_VERSION {
            return Err(ValidationError::UnsupportedVersion(self.contract_version));
        }
        let id_ok = (1..=64).contains(&self.id.len())
            && self
                .id
                .chars()
                .all(|c| c.is_ascii_alphanumeric() || c == '-' || c == '_');
        if !id_ok {
            return Err(ValidationError::BadId);
        }
        let name_len = self.name.trim().chars().count();
        if !(1..=80).contains(&name_len) {
            return Err(ValidationError::BadName);
        }
        self.tz()?;
        self.plan.validate()?;
        if let Recurrence::Weekly {
            days,
            starts_on,
            ends_on,
        } = &self.recurrence
        {
            if days.is_empty() {
                return Err(ValidationError::NoWeekdays);
            }
            if ends_on.is_some_and(|e| e < *starts_on) {
                return Err(ValidationError::EndsBeforeStart);
            }
        }
        for date in self.exceptions.keys() {
            if !self.is_scheduled_on(*date) {
                return Err(ValidationError::ExceptionNotOnScheduledDay(
                    date.to_string(),
                ));
            }
        }
        Ok(())
    }

    /// Parse and validate JSON received at a trust boundary.
    pub fn from_json(json: &str) -> Result<Self, ScheduleParseError> {
        let s: Schedule =
            serde_json::from_str(json).map_err(|e| ScheduleParseError::Malformed(e.to_string()))?;
        s.validate().map_err(ScheduleParseError::Invalid)?;
        Ok(s)
    }
}

#[derive(Debug, Clone, PartialEq, Eq, thiserror::Error)]
pub enum ScheduleParseError {
    #[error("malformed schedule: {0}")]
    Malformed(String),
    #[error(transparent)]
    Invalid(ValidationError),
}
