//! Overlap detection between schedules. Two sessions on the same device group
//! may not run at the same time; the UI must resolve the conflict before saving.

use chrono::{DateTime, Utc};
use serde::{Deserialize, Serialize};

use crate::error::ValidationError;
use crate::schedule::Schedule;
use crate::timeline::{Occurrence, occurrences_between};

#[derive(Debug, Clone, PartialEq, Eq, Serialize, Deserialize)]
pub struct Conflict {
    pub a: Occurrence,
    pub b: Occurrence,
}

/// Every pair of overlapping occurrences in `[from, to)`.
pub fn find_conflicts(
    schedules: &[Schedule],
    from: DateTime<Utc>,
    to: DateTime<Utc>,
) -> Result<Vec<Conflict>, ValidationError> {
    let mut all = Vec::new();
    for s in schedules {
        all.extend(occurrences_between(s, from, to)?);
    }
    all.sort_by_key(|o| o.start);
    let mut out = Vec::new();
    for (i, a) in all.iter().enumerate() {
        for b in &all[i + 1..] {
            if b.start >= a.end {
                break; // sorted by start: nothing later can overlap `a`
            }
            // Includes two occurrences of the same schedule (e.g. a reschedule
            // moved onto another day's session).
            out.push(Conflict {
                a: a.clone(),
                b: b.clone(),
            });
        }
    }
    Ok(out)
}
