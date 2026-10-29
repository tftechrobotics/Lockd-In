//! Lockd In core: platform-neutral logic shared by the Windows and Android clients.
//!
//! This crate owns *what should happen and when*. It never touches the
//! operating system. Native clients own *making it happen* (enforcement) and
//! report back what they observed; [`protection`] turns those reports into the
//! truthful status the UI is allowed to show.
//!
//! Safety invariants enforced here (and covered by tests):
//! * A session can only request restriction while `now` is inside a focus
//!   phase of an accepted occurrence. Outside that window the answer is always
//!   "release".
//! * Every occurrence has a finite end no later than [`plan::MAX_SESSION_MINUTES`]
//!   after it starts, so no schedule can describe an indefinite lockout.
//! * Durations are elapsed real time. Wall-clock (local) times are used only to
//!   anchor the start of an occurrence, resolved with RFC 5545 rules across
//!   daylight-saving transitions.

pub mod conflicts;
pub mod error;
pub mod plan;
pub mod protection;
pub mod schedule;
pub mod session;
pub mod timeline;

pub use error::ValidationError;

/// Version of the serialized contract (schedules, controls, reports). Bump on
/// any breaking change and add a migration in the persistence layer.
pub const CONTRACT_VERSION: u32 = 1;
