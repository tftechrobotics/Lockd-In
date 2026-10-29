mod common;

use chrono::{Duration, TimeZone, Utc, Weekday};
use chrono_tz::America::Chicago;
use common::*;
use focus_core::ValidationError;
use focus_core::plan::{PhaseKind, Segment, SessionPlan};
use focus_core::schedule::{Exception, Schedule, ScheduleParseError};
use focus_core::timeline::{DstAdjustment, occurrence_on, occurrences_between};

#[test]
fn brief_example_timeline() {
    let s = once("a", date(2026, 11, 3), time(18, 0), brief_plan());
    s.validate().unwrap();
    let occ = occurrence_on(&s, date(2026, 11, 3)).unwrap().unwrap();
    let p = &occ.phases;
    assert_eq!(p.len(), 3);
    assert_eq!(
        (p[0].kind, p[0].start, p[0].end),
        (
            PhaseKind::Focus,
            chi(2026, 11, 3, 18, 0),
            chi(2026, 11, 3, 18, 40)
        )
    );
    assert_eq!(
        (p[1].kind, p[1].start, p[1].end),
        (
            PhaseKind::Break,
            chi(2026, 11, 3, 18, 40),
            chi(2026, 11, 3, 18, 50)
        )
    );
    assert_eq!(
        (p[2].kind, p[2].start, p[2].end),
        (
            PhaseKind::Focus,
            chi(2026, 11, 3, 18, 50),
            chi(2026, 11, 3, 19, 30)
        )
    );
    assert_eq!(occ.end, chi(2026, 11, 3, 19, 30));
    assert_eq!(occ.dst_adjustment, None);
}

#[test]
fn spring_forward_gap_shifts_later_per_rfc5545() {
    // 2026-03-08: Chicago clocks jump 02:00 CST -> 03:00 CDT. 02:30 does not exist.
    let s = once("a", date(2026, 3, 8), time(2, 30), SessionPlan::single(30));
    let occ = occurrence_on(&s, date(2026, 3, 8)).unwrap().unwrap();
    assert_eq!(occ.start, chi(2026, 3, 8, 3, 30));
    assert_eq!(
        occ.dst_adjustment,
        Some(DstAdjustment::ShiftedForward { minutes: 60 })
    );
    assert_eq!(occ.end - occ.start, Duration::minutes(30));
}

#[test]
fn fall_back_ambiguous_uses_first_and_keeps_elapsed_time() {
    // 2026-11-01: Chicago clocks fall back 02:00 CDT -> 01:00 CST. 01:30 happens twice.
    let s = once(
        "a",
        date(2026, 11, 1),
        time(1, 30),
        SessionPlan::rounds(2, 40, 10),
    );
    let occ = occurrence_on(&s, date(2026, 11, 1)).unwrap().unwrap();
    // First 01:30 is CDT (UTC-5) = 06:30 UTC.
    assert_eq!(
        occ.start,
        Utc.with_ymd_and_hms(2026, 11, 1, 6, 30, 0).unwrap()
    );
    assert_eq!(occ.dst_adjustment, Some(DstAdjustment::AmbiguousUsedFirst));
    // 90 real minutes later, regardless of the clock change.
    assert_eq!(occ.end - occ.start, Duration::minutes(90));
    // Wall clock at end reads 02:00 CST (it passed 02:00 CDT -> 01:00 CST).
    assert_eq!(
        occ.end
            .with_timezone(&Chicago)
            .format("%H:%M %Z")
            .to_string(),
        "02:00 CST"
    );
}

#[test]
fn weekly_expansion_with_skip_and_reschedule() {
    let mut s = weekly(
        "w",
        &[Weekday::Mon, Weekday::Wed],
        date(2026, 11, 2),
        time(18, 0),
        brief_plan(),
    );
    s.exceptions.insert(date(2026, 11, 4), Exception::Skip);
    s.exceptions.insert(
        date(2026, 11, 9),
        Exception::Reschedule {
            start: date(2026, 11, 10).and_time(time(19, 0)),
        },
    );
    s.validate().unwrap();
    let occs = occurrences_between(&s, chi(2026, 11, 1, 0, 0), chi(2026, 11, 15, 0, 0)).unwrap();
    let starts: Vec<_> = occs.iter().map(|o| o.start).collect();
    assert_eq!(
        starts,
        vec![
            chi(2026, 11, 2, 18, 0),
            chi(2026, 11, 10, 19, 0),
            chi(2026, 11, 11, 18, 0)
        ]
    );
    assert_eq!(occs[1].nominal_date, date(2026, 11, 9));
}

#[test]
fn window_includes_session_already_in_progress() {
    let s = once("a", date(2026, 11, 3), time(18, 0), brief_plan());
    let occs = occurrences_between(&s, chi(2026, 11, 3, 19, 0), chi(2026, 11, 3, 20, 0)).unwrap();
    assert_eq!(occs.len(), 1);
    // Ending exactly at the window start is not an overlap.
    let none = occurrences_between(&s, chi(2026, 11, 3, 19, 30), chi(2026, 11, 3, 21, 0)).unwrap();
    assert!(none.is_empty());
}

#[test]
fn window_is_capped_for_untrusted_input() {
    use Weekday::*;
    let s = weekly(
        "w",
        &[Mon, Tue, Wed, Thu, Fri, Sat, Sun],
        date(2020, 1, 1),
        time(9, 0),
        SessionPlan::single(10),
    );
    let occs = occurrences_between(&s, chi(2026, 1, 1, 0, 0), chi(2030, 1, 1, 0, 0)).unwrap();
    assert!(occs.len() <= 63, "got {}", occs.len());
}

#[test]
fn plan_validation_rejects_bad_shapes() {
    use ValidationError::*;
    let seg = |kind, minutes| Segment { kind, minutes };
    let cases: Vec<(Vec<Segment>, ValidationError)> = vec![
        (vec![], EmptyPlan),
        (
            vec![seg(PhaseKind::Break, 5), seg(PhaseKind::Focus, 10)],
            MustStartAndEndWithFocus,
        ),
        (
            vec![seg(PhaseKind::Focus, 10), seg(PhaseKind::Break, 5)],
            MustStartAndEndWithFocus,
        ),
        (
            vec![seg(PhaseKind::Focus, 10), seg(PhaseKind::Focus, 10)],
            AdjacentSameKind("focus"),
        ),
        (
            vec![seg(PhaseKind::Focus, 0)],
            FocusLength {
                min: 1,
                max: 240,
                got: 0,
            },
        ),
        (
            vec![seg(PhaseKind::Focus, 241)],
            FocusLength {
                min: 1,
                max: 240,
                got: 241,
            },
        ),
        (
            vec![
                seg(PhaseKind::Focus, 10),
                seg(PhaseKind::Break, 121),
                seg(PhaseKind::Focus, 10),
            ],
            BreakLength {
                min: 1,
                max: 120,
                got: 121,
            },
        ),
    ];
    for (segments, want) in cases {
        assert_eq!(SessionPlan { segments }.validate(), Err(want));
    }
    // 4 x 240 focus + 3 x 10 break = 990 min > 720
    assert!(matches!(
        SessionPlan::rounds(4, 240, 10).validate(),
        Err(TooLong { .. })
    ));
    assert!(
        SessionPlan::single(10).validate().is_ok(),
        "ten-minute starter session is valid"
    );
}

#[test]
fn schedule_validation() {
    let mut s = once("a", date(2026, 11, 3), time(18, 0), brief_plan());
    s.time_zone = "Mars/Olympus".into();
    assert_eq!(
        s.validate(),
        Err(ValidationError::UnknownTimeZone("Mars/Olympus".into()))
    );

    let mut s = once("bad id!", date(2026, 11, 3), time(18, 0), brief_plan());
    assert_eq!(s.validate(), Err(ValidationError::BadId));
    s.id = "ok".into();
    s.exceptions.insert(date(2026, 11, 4), Exception::Skip);
    assert!(matches!(
        s.validate(),
        Err(ValidationError::ExceptionNotOnScheduledDay(_))
    ));

    let mut w = weekly("w", &[], date(2026, 11, 2), time(18, 0), brief_plan());
    assert_eq!(w.validate(), Err(ValidationError::NoWeekdays));
    w.recurrence = focus_core::schedule::Recurrence::Weekly {
        days: vec![Weekday::Mon],
        starts_on: date(2026, 11, 2),
        ends_on: Some(date(2026, 11, 1)),
    };
    assert_eq!(w.validate(), Err(ValidationError::EndsBeforeStart));

    let mut v = once("a", date(2026, 11, 3), time(18, 0), brief_plan());
    v.contract_version = 99;
    assert_eq!(v.validate(), Err(ValidationError::UnsupportedVersion(99)));
}

#[test]
fn json_round_trip_and_strict_parsing() {
    let mut s = weekly(
        "w",
        &[Weekday::Mon, Weekday::Thu],
        date(2026, 11, 2),
        time(18, 0),
        brief_plan(),
    );
    s.exceptions.insert(date(2026, 11, 5), Exception::Skip);
    let json = serde_json::to_string_pretty(&s).unwrap();
    assert_eq!(Schedule::from_json(&json).unwrap(), s);

    // Unknown fields are rejected rather than silently ignored.
    let tampered = json.replacen("\"name\"", "\"admin\": true, \"name\"", 1);
    assert!(matches!(
        Schedule::from_json(&tampered),
        Err(ScheduleParseError::Malformed(_))
    ));

    // Well-formed but invalid content is rejected with a validation error.
    let invalid = json.replace("\"minutes\": 40", "\"minutes\": 4000");
    assert!(matches!(
        Schedule::from_json(&invalid),
        Err(ScheduleParseError::Invalid(_))
    ));

    // Overflow-sized numbers fail to parse instead of wrapping.
    let overflow = json.replace("\"minutes\": 40", "\"minutes\": 99999999999");
    assert!(Schedule::from_json(&overflow).is_err());
}
