//! The shape of one session: an alternating sequence of focus periods and breaks.

use serde::{Deserialize, Serialize};

use crate::error::ValidationError;

pub const MIN_FOCUS_MINUTES: u32 = 1;
pub const MAX_FOCUS_MINUTES: u32 = 240;
pub const MIN_BREAK_MINUTES: u32 = 1;
pub const MAX_BREAK_MINUTES: u32 = 120;
/// Hard cap on a single session. Guarantees every occurrence ends.
pub const MAX_SESSION_MINUTES: u32 = 12 * 60;
pub const MAX_SEGMENTS: usize = 41;

#[derive(Debug, Clone, Copy, PartialEq, Eq, Hash, Serialize, Deserialize)]
#[serde(rename_all = "snake_case")]
pub enum PhaseKind {
    Focus,
    Break,
}

impl PhaseKind {
    pub fn label(self) -> &'static str {
        match self {
            PhaseKind::Focus => "focus",
            PhaseKind::Break => "break",
        }
    }
}

#[derive(Debug, Clone, Copy, PartialEq, Eq, Serialize, Deserialize)]
#[serde(deny_unknown_fields)]
pub struct Segment {
    pub kind: PhaseKind,
    pub minutes: u32,
}

#[derive(Debug, Clone, PartialEq, Eq, Serialize, Deserialize)]
#[serde(deny_unknown_fields)]
pub struct SessionPlan {
    pub segments: Vec<Segment>,
}

impl SessionPlan {
    /// A single uninterrupted focus period, e.g. the ten-minute "just start" session.
    pub fn single(minutes: u32) -> Self {
        Self {
            segments: vec![Segment {
                kind: PhaseKind::Focus,
                minutes,
            }],
        }
    }

    /// `rounds` focus periods separated by breaks of `break_minutes`.
    pub fn rounds(rounds: u32, focus_minutes: u32, break_minutes: u32) -> Self {
        let mut segments = Vec::new();
        for i in 0..rounds {
            if i > 0 {
                segments.push(Segment {
                    kind: PhaseKind::Break,
                    minutes: break_minutes,
                });
            }
            segments.push(Segment {
                kind: PhaseKind::Focus,
                minutes: focus_minutes,
            });
        }
        Self { segments }
    }

    pub fn total_minutes(&self) -> u32 {
        self.segments.iter().map(|s| s.minutes).sum()
    }

    pub fn validate(&self) -> Result<(), ValidationError> {
        let segs = &self.segments;
        if segs.is_empty() {
            return Err(ValidationError::EmptyPlan);
        }
        if segs.len() > MAX_SEGMENTS {
            return Err(ValidationError::TooManySegments { max: MAX_SEGMENTS });
        }
        if segs[0].kind != PhaseKind::Focus || segs[segs.len() - 1].kind != PhaseKind::Focus {
            return Err(ValidationError::MustStartAndEndWithFocus);
        }
        for pair in segs.windows(2) {
            if pair[0].kind == pair[1].kind {
                return Err(ValidationError::AdjacentSameKind(pair[0].kind.label()));
            }
        }
        // Sum with overflow protection before checking individual lengths so a
        // hostile payload of huge values is rejected cleanly.
        let mut total: u64 = 0;
        for s in segs {
            match s.kind {
                PhaseKind::Focus
                    if !(MIN_FOCUS_MINUTES..=MAX_FOCUS_MINUTES).contains(&s.minutes) =>
                {
                    return Err(ValidationError::FocusLength {
                        min: MIN_FOCUS_MINUTES,
                        max: MAX_FOCUS_MINUTES,
                        got: s.minutes,
                    });
                }
                PhaseKind::Break
                    if !(MIN_BREAK_MINUTES..=MAX_BREAK_MINUTES).contains(&s.minutes) =>
                {
                    return Err(ValidationError::BreakLength {
                        min: MIN_BREAK_MINUTES,
                        max: MAX_BREAK_MINUTES,
                        got: s.minutes,
                    });
                }
                _ => {}
            }
            total += u64::from(s.minutes);
        }
        if total > u64::from(MAX_SESSION_MINUTES) {
            return Err(ValidationError::TooLong {
                max: MAX_SESSION_MINUTES,
                got: total as u32,
            });
        }
        Ok(())
    }
}
