//! Turns schedules into concrete occurrences with absolute phase boundaries.
//!
//! Rules:
//! * The occurrence start is local wall-clock time resolved in the schedule's
//!   zone using RFC 5545 semantics (what Google Calendar uses):
//!   - a time that does not exist (spring-forward gap) is interpreted with the
//!     UTC offset in effect *before* the gap, so 02:30 becomes 03:30;
//!   - a time that occurs twice (fall-back overlap) uses the first occurrence.
//! * Phase lengths are elapsed real time. A 40-minute focus period is 40 real
//!   minutes even if the clock jumps during it.

use chrono::{DateTime, Duration, LocalResult, NaiveDate, NaiveDateTime, Offset, TimeZone, Utc};
use chrono_tz::Tz;
use serde::{Deserialize, Serialize};

use crate::error::ValidationError;
use crate::plan::PhaseKind;
use crate::schedule::{Exception, Schedule};

/// Note attached when the requested local start had to be adjusted.
#[derive(Debug, Clone, Copy, PartialEq, Eq, Serialize, Deserialize)]
#[serde(tag = "type", rename_all = "snake_case")]
pub enum DstAdjustment {
    /// The requested time did not exist; the session starts this many minutes later on the clock.
    ShiftedForward { minutes: i64 },
    /// The requested time happened twice; the first one is used.
    AmbiguousUsedFirst,
}

#[derive(Debug, Clone, PartialEq, Eq, Serialize, Deserialize)]
pub struct Phase {
    pub index: usize,
    pub kind: PhaseKind,
    pub start: DateTime<Utc>,
    pub end: DateTime<Utc>,
}

#[derive(Debug, Clone, PartialEq, Eq, Serialize, Deserialize)]
pub struct Occurrence {
    pub schedule_id: String,
    /// The date this occurrence belongs to in the recurrence (before any reschedule).
    pub nominal_date: NaiveDate,
    pub start: DateTime<Utc>,
    pub end: DateTime<Utc>,
    pub phases: Vec<Phase>,
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub dst_adjustment: Option<DstAdjustment>,
}

impl Occurrence {
    pub fn phase_at(&self, now: DateTime<Utc>) -> Option<&Phase> {
        self.phases.iter().find(|p| p.start <= now && now < p.end)
    }

    pub fn contains(&self, now: DateTime<Utc>) -> bool {
        self.start <= now && now < self.end
    }
}

/// Resolve a local wall-clock time in `tz` to an instant (RFC 5545 rules).
pub fn resolve_local(tz: Tz, local: NaiveDateTime) -> (DateTime<Utc>, Option<DstAdjustment>) {
    match tz.from_local_datetime(&local) {
        LocalResult::Single(t) => (t.with_timezone(&Utc), None),
        LocalResult::Ambiguous(a, b) => {
            let first = a.with_timezone(&Utc).min(b.with_timezone(&Utc));
            (first, Some(DstAdjustment::AmbiguousUsedFirst))
        }
        LocalResult::None => {
            // Use the offset in force before the gap. Real-world gaps are far
            // shorter than 6 hours, so 6 hours earlier is safely before it.
            let before = match tz.from_local_datetime(&(local - Duration::hours(6))) {
                LocalResult::Single(t) => t.offset().fix(),
                LocalResult::Ambiguous(a, _) => a.offset().fix(),
                // Unreachable for IANA data; fall back to the offset at the UTC reading.
                LocalResult::None => tz.offset_from_utc_datetime(&local).fix(),
            };
            let utc = (local - Duration::seconds(i64::from(before.local_minus_utc()))).and_utc();
            let actual_local = utc.with_timezone(&tz).naive_local();
            let minutes = (actual_local - local).num_minutes();
            (utc, Some(DstAdjustment::ShiftedForward { minutes }))
        }
    }
}

/// Build the occurrence for one recurrence date, honoring exceptions.
/// Returns `None` if the date is not scheduled or is skipped.
pub fn occurrence_on(
    schedule: &Schedule,
    nominal_date: NaiveDate,
) -> Result<Option<Occurrence>, ValidationError> {
    if !schedule.is_scheduled_on(nominal_date) {
        return Ok(None);
    }
    let tz = schedule.tz()?;
    let local_start = match schedule.exceptions.get(&nominal_date) {
        Some(Exception::Skip) => return Ok(None),
        Some(Exception::Reschedule { start }) => *start,
        None => nominal_date.and_time(schedule.start_time),
    };
    let (start, dst_adjustment) = resolve_local(tz, local_start);
    let mut phases = Vec::with_capacity(schedule.plan.segments.len());
    let mut cursor = start;
    for (index, seg) in schedule.plan.segments.iter().enumerate() {
        let end = cursor + Duration::minutes(i64::from(seg.minutes));
        phases.push(Phase {
            index,
            kind: seg.kind,
            start: cursor,
            end,
        });
        cursor = end;
    }
    Ok(Some(Occurrence {
        schedule_id: schedule.id.clone(),
        nominal_date,
        start,
        end: cursor,
        phases,
        dst_adjustment,
    }))
}

/// All occurrences of `schedule` that overlap `[from, to)`, sorted by start.
///
/// The schedule must already be validated. The window is capped at 62 days to
/// bound work on untrusted input.
pub fn occurrences_between(
    schedule: &Schedule,
    from: DateTime<Utc>,
    to: DateTime<Utc>,
) -> Result<Vec<Occurrence>, ValidationError> {
    let tz = schedule.tz()?;
    let to = to.min(from + Duration::days(62));
    if to <= from {
        return Ok(Vec::new());
    }
    // Nominal dates whose occurrence could overlap the window: a session lasts
    // at most 12h and a reschedule can move it, so widen the search generously
    // and also include every rescheduled exception explicitly.
    let first = from.with_timezone(&tz).date_naive() - Duration::days(2);
    let last = to.with_timezone(&tz).date_naive() + Duration::days(1);
    let mut dates: Vec<NaiveDate> = first.iter_days().take_while(|d| *d <= last).collect();
    for d in schedule.exceptions.keys() {
        if !dates.contains(d) {
            dates.push(*d);
        }
    }
    let mut out = Vec::new();
    for d in dates {
        if let Some(occ) = occurrence_on(schedule, d)? {
            if occ.start < to && occ.end > from {
                out.push(occ);
            }
        }
    }
    out.sort_by_key(|o| o.start);
    Ok(out)
}
