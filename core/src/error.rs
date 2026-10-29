use thiserror::Error;

/// Reasons a schedule or plan is rejected at a trust boundary (UI input,
/// persisted data, or a message from a paired device).
#[derive(Debug, Clone, PartialEq, Eq, Error)]
pub enum ValidationError {
    #[error("a session needs at least one focus period")]
    EmptyPlan,
    #[error("a session must begin and end with a focus period")]
    MustStartAndEndWithFocus,
    #[error("two {0} periods are next to each other; combine them into one")]
    AdjacentSameKind(&'static str),
    #[error("focus periods must be between {min} and {max} minutes (got {got})")]
    FocusLength { min: u32, max: u32, got: u32 },
    #[error("breaks must be between {min} and {max} minutes (got {got})")]
    BreakLength { min: u32, max: u32, got: u32 },
    #[error("a session can last at most {max} minutes in total (got {got})")]
    TooLong { max: u32, got: u32 },
    #[error("too many periods in one session (max {max})")]
    TooManySegments { max: usize },
    #[error("unknown time zone: {0}")]
    UnknownTimeZone(String),
    #[error("a weekly schedule needs at least one weekday")]
    NoWeekdays,
    #[error("the schedule ends before it begins")]
    EndsBeforeStart,
    #[error("an exception is dated {0}, which is not a scheduled day")]
    ExceptionNotOnScheduledDay(String),
    #[error("schedule id must be 1-64 characters of letters, digits, '-' or '_'")]
    BadId,
    #[error("schedule name must be 1-80 characters")]
    BadName,
    #[error("unsupported contract version {0}")]
    UnsupportedVersion(u32),
}
