//! Print the timeline for a schedule JSON file, or for the brief's example.
//!
//!   cargo run -p focus-core --example timeline [schedule.json] [date]

use std::str::FromStr;

use chrono::{NaiveDate, NaiveTime};
use chrono_tz::Tz;
use focus_core::plan::SessionPlan;
use focus_core::schedule::{Recurrence, Schedule};
use focus_core::timeline::occurrence_on;

fn main() {
    let args: Vec<String> = std::env::args().skip(1).collect();
    let schedule = match args.first() {
        Some(path) => {
            let json = std::fs::read_to_string(path).expect("read schedule file");
            Schedule::from_json(&json).unwrap_or_else(|e| {
                eprintln!("Rejected: {e}");
                std::process::exit(2)
            })
        }
        None => Schedule {
            contract_version: focus_core::CONTRACT_VERSION,
            id: "example".into(),
            name: "Example from brief".into(),
            time_zone: "America/Chicago".into(),
            start_time: NaiveTime::from_hms_opt(18, 0, 0).unwrap(),
            plan: SessionPlan::rounds(2, 40, 10),
            recurrence: Recurrence::Once {
                date: NaiveDate::from_ymd_opt(2026, 11, 3).unwrap(),
            },
            exceptions: Default::default(),
        },
    };
    let date = match args.get(1) {
        Some(d) => NaiveDate::from_str(d).expect("date as YYYY-MM-DD"),
        None => match &schedule.recurrence {
            Recurrence::Once { date } => *date,
            Recurrence::Weekly { starts_on, .. } => *starts_on,
        },
    };
    let tz = Tz::from_str(&schedule.time_zone).unwrap();
    match occurrence_on(&schedule, date).unwrap() {
        None => println!("{}: no session on {date}", schedule.name),
        Some(occ) => {
            println!("{} — {date} ({})", schedule.name, schedule.time_zone);
            if let Some(adj) = occ.dst_adjustment {
                println!("  note: {adj:?}");
            }
            for p in &occ.phases {
                println!(
                    "  {:<5}  {} – {}",
                    p.kind.label(),
                    p.start.with_timezone(&tz).format("%H:%M %Z"),
                    p.end.with_timezone(&tz).format("%H:%M %Z"),
                );
            }
        }
    }
}
