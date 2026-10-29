//! Session time that a changed system clock cannot shorten or stretch.
//!
//! The problem: [`crate::session::evaluate`] takes `now`. If clients passed the
//! wall clock, moving the phone or PC clock forward would end focus early, and
//! moving it back would extend the lockout.
//!
//! The rule: while a device stays up, a running session advances by the
//! platform's **monotonic, sleep-inclusive** clock, anchored to the wall clock
//! once at session start:
//!
//! * Android: `SystemClock.elapsedRealtime()` (counts deep sleep). The boot
//!   identity is `Settings.Global.BOOT_COUNT`.
//! * Windows: `QueryInterruptTime` (counts sleep and hibernation). The boot
//!   identity is the last boot time from the kernel, rounded to seconds.
//!
//! A wall-clock change is then detected (and reported), but doesn't move
//! session time.
//!
//! Across a reboot the monotonic clock restarts, so the true time spent off is
//! unknowable from the device alone. The rule there is
//! `effective = max(wall clock, last checkpoint + time since boot)`:
//!
//! * Setting the clock **back** cannot extend a session beyond real elapsed
//!   time, because the lower bound holds. This is the safety direction: no
//!   lockout grows longer than reality.
//! * Setting the clock **forward while the device is off** can end a session
//!   early. This is an accepted limit, like an administrator uninstalling the
//!   app (R2/R17). It is reported, not prevented. Paired devices see a
//!   [`ClockEvent::RebootedWallAhead`].
//!
//! Clients must persist [`SessionClock`] after every observation (it is
//! serializable), so the checkpoint survives a crash or reboot.

use chrono::{DateTime, Duration, Utc};
use serde::{Deserialize, Serialize};

/// Wall-clock differences smaller than this are treated as ordinary drift and
/// NTP corrections, not as a clock change. A target until measured on devices.
pub const DEFAULT_JUMP_TOLERANCE_SECONDS: i64 = 5;

/// A reading of the platform's monotonic, sleep-inclusive clock.
#[derive(Debug, Clone, PartialEq, Eq, Serialize, Deserialize)]
#[serde(deny_unknown_fields)]
pub struct MonoInstant {
    /// Changes whenever the device boots (boot count or boot time).
    pub boot_id: String,
    /// Milliseconds since that boot, including time asleep.
    pub since_boot_ms: u64,
}

/// One simultaneous reading of both clocks, taken by the native client.
#[derive(Debug, Clone, PartialEq, Eq, Serialize, Deserialize)]
#[serde(deny_unknown_fields)]
pub struct ClockSample {
    pub wall: DateTime<Utc>,
    pub mono: MonoInstant,
}

/// Something about the clocks worth logging and reporting to paired devices.
#[derive(Debug, Clone, PartialEq, Eq, Serialize, Deserialize)]
#[serde(tag = "type", rename_all = "snake_case", deny_unknown_fields)]
pub enum ClockEvent {
    /// The wall clock was changed while the device stayed up. Session time was
    /// not affected. Positive = wall clock moved ahead of session time.
    WallClockChanged { delta_seconds: i64 },
    /// The device rebooted and its wall clock reads further ahead than the
    /// minimum possible elapsed time. Either it was off that long, or the
    /// clock was moved forward; the device can't tell which. Session time
    /// follows the wall clock here (see module docs).
    RebootedWallAhead { ahead_seconds: i64 },
    /// The device rebooted and its wall clock reads *earlier* than time that
    /// has provably passed. Session time uses the provable lower bound.
    RebootedWallBehind { behind_seconds: i64 },
    /// The monotonic clock went backwards without a boot-id change. That
    /// shouldn't happen; it's treated like a reboot and reported.
    MonotonicWentBackwards,
}

/// Which rule produced the current session time.
#[derive(Debug, Clone, Copy, PartialEq, Eq, Serialize, Deserialize)]
#[serde(rename_all = "snake_case")]
pub enum Basis {
    /// Advanced by the monotonic clock since the anchor.
    Monotonic,
    /// Just re-anchored after a reboot.
    AfterReboot,
}

#[derive(Debug, Clone, PartialEq, Eq)]
pub struct ClockReading {
    /// The `now` to pass to [`crate::session::evaluate`].
    pub session_now: DateTime<Utc>,
    pub basis: Basis,
    pub event: Option<ClockEvent>,
}

/// Per-session clock state. Persist it after every [`SessionClock::observe`].
#[derive(Debug, Clone, PartialEq, Eq, Serialize, Deserialize)]
#[serde(deny_unknown_fields)]
pub struct SessionClock {
    anchor_wall: DateTime<Utc>,
    anchor_mono: MonoInstant,
    /// Latest session time ever returned. Session time never goes backwards.
    checkpoint: DateTime<Utc>,
    jump_tolerance_seconds: i64,
}

impl SessionClock {
    /// Anchor at session start. The wall clock is trusted once, here.
    pub fn start(sample: &ClockSample) -> Self {
        Self::with_tolerance(sample, Duration::seconds(DEFAULT_JUMP_TOLERANCE_SECONDS))
    }

    pub fn with_tolerance(sample: &ClockSample, tolerance: Duration) -> Self {
        Self {
            anchor_wall: sample.wall,
            anchor_mono: sample.mono.clone(),
            checkpoint: sample.wall,
            jump_tolerance_seconds: tolerance.num_seconds().max(0),
        }
    }

    /// Latest session time returned (or the start time).
    pub fn checkpoint(&self) -> DateTime<Utc> {
        self.checkpoint
    }

    /// Turn a fresh clock sample into session time, updating the anchor after
    /// a reboot. Session time never decreases between calls.
    pub fn observe(&mut self, sample: &ClockSample) -> ClockReading {
        let same_boot = sample.mono.boot_id == self.anchor_mono.boot_id;
        if same_boot && sample.mono.since_boot_ms >= self.anchor_mono.since_boot_ms {
            let elapsed = sample.mono.since_boot_ms - self.anchor_mono.since_boot_ms;
            let session_now = add(self.anchor_wall, elapsed);
            let session_now = session_now.max(self.checkpoint);
            self.checkpoint = session_now;
            let delta = (sample.wall - session_now).num_seconds();
            let event = (delta.abs() > self.jump_tolerance_seconds).then_some(
                ClockEvent::WallClockChanged {
                    delta_seconds: delta,
                },
            );
            return ClockReading {
                session_now,
                basis: Basis::Monotonic,
                event,
            };
        }

        // New boot (or a monotonic clock that went backwards): at least
        // `since_boot` has passed since the last checkpoint.
        let lower_bound = add(self.checkpoint, sample.mono.since_boot_ms);
        let session_now = sample.wall.max(lower_bound);
        let event = if !same_boot {
            let diff = (sample.wall - lower_bound).num_seconds();
            if diff > self.jump_tolerance_seconds {
                Some(ClockEvent::RebootedWallAhead {
                    ahead_seconds: diff,
                })
            } else if diff < -self.jump_tolerance_seconds {
                Some(ClockEvent::RebootedWallBehind {
                    behind_seconds: -diff,
                })
            } else {
                None
            }
        } else {
            Some(ClockEvent::MonotonicWentBackwards)
        };
        self.anchor_wall = session_now;
        self.anchor_mono = sample.mono.clone();
        self.checkpoint = session_now;
        ClockReading {
            session_now,
            basis: Basis::AfterReboot,
            event,
        }
    }
}

/// `t + ms`, saturating at the latest representable time instead of panicking
/// on absurd inputs from a misbehaving client.
fn add(t: DateTime<Utc>, ms: u64) -> DateTime<Utc> {
    i64::try_from(ms)
        .ok()
        .and_then(Duration::try_milliseconds)
        .and_then(|d| t.checked_add_signed(d))
        .unwrap_or(DateTime::<Utc>::MAX_UTC)
}
