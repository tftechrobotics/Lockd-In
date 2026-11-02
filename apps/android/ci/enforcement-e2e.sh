#!/usr/bin/env bash
# End-to-end blocking test of the REAL Lockd In app on a CI emulator (milestone M1.5–M1.10,
# environment E-emu). Drives a 2-round session (4 min focus, 2 min break, 4 min focus) that
# blocks the harmless probe target app, and checks: block screen over the target (20 trials),
# essentials never covered, permission loss reported, process kill, break release, focus
# resume, reboot mid-session, release at the end, and that the target is never killed.
#
# EMULATOR ONLY. Latencies include adb round-trips, so they are upper bounds, not phone data.
# Usage: ci/enforcement-e2e.sh <app-debug.apk> <target.apk> <out-dir>
set -uo pipefail
APP_APK=$1 TARGET_APK=$2 OUT=$3
mkdir -p "$OUT"
A=app.lockdin
T=app.lockdin.probetarget
BLOCK="$A/.enforce.BlockActivity"
RESULTS="$OUT/results.txt"
: > "$RESULTS"
FAILED=0

now_ms() { date +%s%3N; }
note() { echo "$*" | tee -a "$RESULTS"; }
# Results are collected and reported as two annotations at the end (GitHub shows at most
# 10 annotations of each kind per step).
record() { # record <id> <pass|FAIL|info> <detail>
  note "$1 $2 $3"
  [ "$2" = FAIL ] && FAILED=1
  return 0
}

boot_wait() {
  adb wait-for-device
  adb shell 'while [ "$(getprop sys.boot_completed)" != 1 ]; do sleep 1; done'
  sleep 3
  adb shell input keyevent KEYCODE_WAKEUP
  adb shell wm dismiss-keyguard >/dev/null 2>&1 || true
  adb shell input keyevent 82 >/dev/null 2>&1 || true
}

# The activity currently resumed on top, as package/class.
top() {
  adb shell dumpsys activity activities 2>/dev/null \
    | grep -m1 -E 'topResumedActivity=|mResumedActivity:' \
    | grep -oE '[a-zA-Z][a-zA-Z0-9_.]*/[a-zA-Z0-9_.$]+' | head -1
}

# wait_top <regex> <timeout-ms>: prints latency in ms, or "timeout(<top>)" and returns 1.
wait_top() {
  local start t; start=$(now_ms)
  while :; do
    t=$(top)
    if [[ "$t" =~ $1 ]]; then echo $(( $(now_ms) - start )); return 0; fi
    if (( $(now_ms) - start > $2 )); then echo "timeout($t)"; return 1; fi
    sleep 0.15
  done
}

launch_target() { adb shell am start -n "$T/.TargetActivity" >/dev/null 2>&1; }
home() { adb shell input keyevent KEYCODE_HOME; sleep 1; }
ctl() { adb shell am broadcast -f 0x20 -a app.lockdin.debug.CONTROL -n "$A/app.lockdin.debug.TestControl" "$@" >/dev/null; }
service_up() { adb shell dumpsys activity services "$A" 2>/dev/null | grep -q 'FocusService'; }
notif_has() { adb shell dumpsys notification --noredact 2>/dev/null | grep -q "$1"; }
until_s() { local wait=$(( T0 + $1 - $(date +%s) )); (( wait > 0 )) && sleep "$wait"; }

# One trial: open the target, expect the block screen. Prints latency or timeout.
trial_blocked() { launch_target; local r; r=$(wait_top "${BLOCK//./\\.}" "${1:-5000}"); local rc=$?; echo "$r"; home; return $rc; }
# Open the target, expect it to stay in front (no block screen) for 3 s.
trial_open() {
  launch_target
  local r; r=$(wait_top "^$T/" 5000) || { echo "not-on-top($r)"; home; return 1; }
  sleep 3
  local t; t=$(top); home
  [[ "$t" == "$T/"* ]] && { echo "open"; return 0; } || { echo "covered-later($t)"; return 1; }
}

# find_text <text>: prints the centre of the first on-screen node whose text or description is <text>.
find_text() {
  local f=/tmp/ui.xml xy
  for _ in 1 2 3 4 5 6; do
    adb exec-out uiautomator dump /dev/tty > "$f" 2>/dev/null
    xy=$(python3 - "$1" "$f" <<'PY'
import re, sys
t, x = sys.argv[1], open(sys.argv[2], errors="ignore").read()
for n in re.findall(r"<node [^>]*>", x):
    if f'text="{t}"' in n or f'content-desc="{t}"' in n:
        a = list(map(int, re.search(r'bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"', n).groups()))
        print((a[0] + a[2]) // 2, (a[1] + a[3]) // 2); break
PY
)
    [ -n "$xy" ] && { echo "$xy"; return 0; }
    sleep 1
  done
  return 1
}
tap_text() { local xy; xy=$(find_text "$1") || return 1; adb shell input tap $xy; }
intro_count() { adb logcat -d -s LockdInIntro:I 2>/dev/null | grep -c "$1"; }
anim_scale() { for k in window_animation_scale transition_animation_scale animator_duration_scale; do adb shell settings put global $k "$1"; done; }

# Manual start sequence (R42): plays once per start pressed in the app; the shackle shuts only
# after blocking is confirmed; never replayed by reopening, rotation or a blocked app; a timer-only
# start keeps the shackle open; Remove animations replaces the turn with a fade.
manual_start() { # manual_start <button text>
  adb shell am start -W -n "$A/.MainActivity" >/dev/null; sleep 2
  tap_text "New session" || return 1
  sleep 1.5
  tap_text "$1"
}
# Study audio (R48). Built-in audio: starts with a fresh focus period, keeps playing when a
# blocked app is covered, stops on emergency recovery. Music allowance: background playback
# doesn't count, one warning near the end, then the music app is covered without stopping its
# sound (the probe target's tone pauses only if something takes audio focus).
music_used() { adb shell run-as $A cat shared_prefs/lockdin.xml 2>/dev/null | grep -oE 'musicUsedMs&quot;:[0-9]+' | grep -oE '[0-9]+$' | head -1; }
study_playing() { adb shell dumpsys media_session 2>/dev/null | grep -A12 'lockdin-study' | grep -qE 'state=PlaybackState \{state=(3|PLAYING)'; }
target_log() { adb shell run-as $T cat files/target.jsonl 2>/dev/null; }
audio_phase() {
  ctl --es cmd finish; adb logcat -c; sleep 1
  ctl --es cmd start --ei focus 4 --ei break 1 --ei rounds 1 --es block "$T" --es audio RAIN
  adb shell am start -W -n "$A/.MainActivity" >/dev/null; sleep 6; home
  adb logcat -d -s LockdInAudio:I | grep -q "auto_start phase=0" && record R48-audio-autostart pass "Rain started with the focus period" || record R48-audio-autostart FAIL "no auto_start in log"
  study_playing && record R48-audio-background pass "media session playing with Lockd In in the background" || record R48-audio-background FAIL "media session not playing ($(adb shell dumpsys media_session | grep -A6 lockdin-study | tr -s ' ' | head -4 | tr '\n' ' '))"
  adb shell dumpsys notification --noredact 2>/dev/null | grep -q 'MediaStyle\|android.mediaSession' && record R48-audio-notification pass "media-style session notification" || record R48-audio-notification FAIL "no media notification"
  r=$(trial_blocked 10000) && { study_playing && record R48-audio-survives-cover pass "built-in audio kept playing through a block screen" || record R48-audio-survives-cover FAIL "built-in audio stopped by the block screen"; } || record R48-audio-survives-cover FAIL "target not covered ($r)"
  ctl --es cmd emergency; sleep 5
  study_playing && record R48-audio-stops FAIL "still playing after emergency recovery" || record R48-audio-stops pass "faded out and stopped on emergency recovery"

  # Music allowance: target as the chosen music app, 1 minute, 20 s already used (40 s left).
  ctl --es cmd finish; sleep 1
  adb shell run-as $T rm -f files/target.jsonl 2>/dev/null
  ctl --es cmd start --ei focus 6 --ei break 1 --ei rounds 1 --es music LIMITED --es musicApps "$T" --ei allowance 1 --el musicUsed 20000
  sleep 3
  launch_target; wait_top "^$T/" 5000 >/dev/null
  tap_text "Start / stop quiet tone" || tap_text "START / STOP QUIET TONE"
  sleep 4; home; sleep 2
  u1=$(music_used); sleep 12; u2=$(music_used)
  if [ -n "$u1" ] && [ "$u1" = "$u2" ] && [ "$u1" -gt 20000 ]; then record R48-allowance-background pass "foreground use counted (${u1} ms), 12 s of background playback didn't ($u2 ms)"
  else record R48-allowance-background FAIL "used before=$u1 after=$u2 (expected >20000 and unchanged)"; fi
  launch_target
  seen=""; start=$(now_ms)
  while (( $(now_ms) - start < 30000 )); do notif_has "seconds of music browsing left" && { seen=$(( $(now_ms) - start )); break; }; sleep 0.5; done
  [ -n "$seen" ] && record R48-allowance-warning pass "warning ${seen} ms after returning to the app" || record R48-allowance-warning FAIL "no 30-second warning"
  r=$(wait_top "${BLOCK//./\\.}" 45000) && record R48-allowance-cover pass "music app covered when the allowance ran out (${r} ms)" || record R48-allowance-cover FAIL "not covered: $r"
  find_text "Music browsing time is up" >/dev/null && record R48-allowance-text pass "block screen says music browsing time is up" || record R48-allowance-text FAIL "music wording not shown"
  sleep 3
  tl=$(target_log)
  if echo "$tl" | grep -q '"tone_started"' && ! echo "$tl" | grep -q 'lost_audio_focus\|"tone_stopped"'; then record R48-allowance-keeps-playing pass "the music app's sound kept playing under the block screen (no audio-focus loss)"
  else record R48-allowance-keeps-playing FAIL "tone stopped or never started: $(echo "$tl" | grep -oE '"evt":"[a-z_]+"' | tail -5 | tr '\n' ' ')"; fi
  # Other blocked apps still get the normal cover (audio focus requested): checked by the
  # main trials above; the exception is limited to chosen music apps whose time is up.
  home; ctl --es cmd emergency; sleep 2
  adb shell am force-stop $T
}

intro_phase() {
  ctl --es cmd finish; ctl --es cmd rules --es block "$T"
  anim_scale 1
  adb shell am start -W -n "$A/.MainActivity" >/dev/null; sleep 2
  tap_text "New session" || record intro-ui FAIL "couldn't find New session"
  sleep 1.5
  p0=$(intro_count "played session="); c0=$(intro_count "shackle_closed"); p1=$p0
  if xy=$(find_text "Start focus"); then
    adb shell screenrecord --time-limit 5 --bit-rate 8000000 /sdcard/start-sequence.mp4 & rec=$!
    sleep 1; adb shell input tap $xy
    wait $rec; adb pull /sdcard/start-sequence.mp4 "$OUT/start-sequence.mp4" >/dev/null 2>&1
    adb shell screencap -p /sdcard/s.png; adb pull /sdcard/s.png "$OUT/lock-light.png" >/dev/null 2>&1
    p1=$(intro_count "played session="); c1=$(intro_count "shackle_closed")
    closed=$(adb logcat -d -s LockdInIntro:I | grep shackle_closed | tail -1 | grep -oE 'after_ms=[0-9]+' | cut -d= -f2)
    [ $((p1 - p0)) = 1 ] && grep -q 'reduced=false' <(adb logcat -d -s LockdInIntro:I | grep played | tail -1) \
      && record intro-manual-plays pass "played once on Start focus" || record intro-manual-plays FAIL "played $((p1 - p0)) times"
    [ $((c1 - c0)) = 1 ] && record intro-shackle-after-verify pass "shackle shut ${closed} ms after Start (after the turn and the service's PROTECTING report)" \
      || record intro-shackle-after-verify FAIL "shackle did not shut"
    service_up && record intro-enforcement pass "blocking service running" || record intro-enforcement FAIL "service not running"
  else
    record intro-manual-plays FAIL "couldn't find Start focus"
  fi
  # Reopening, rotation and a blocked app must not replay it.
  home; adb shell am start -W -n "$A/.MainActivity" >/dev/null; sleep 2
  adb shell settings put system accelerometer_rotation 0
  adb shell settings put system user_rotation 1; sleep 2; adb shell settings put system user_rotation 0; sleep 2
  trial_blocked >/dev/null; adb shell am start -W -n "$A/.MainActivity" >/dev/null; sleep 2
  # Owner report: after the block screen, later manual starts didn't turn the dial. Leave the
  # block screen with its own "Return to work" button, as a person would.
  launch_target; wait_top "${BLOCK//./\\.}" 6000 >/dev/null && { tap_text "Return to work" || note "  no Return to work button"; }; sleep 2
  n=$(adb shell dumpsys activity activities | grep -oE 'ActivityRecord\{[0-9a-f]+ u0 app\.lockdin/\.MainActivity' | sort -u | wc -l | tr -d ' ')
  [ "$n" -le 1 ] && record intro-single-main pass "one main screen after Return to work ($n)" || record intro-single-main FAIL "$n main screens after Return to work"
  p2=$(intro_count "played session=")
  [ "$p2" = "$p1" ] && record intro-no-replay pass "no replay after reopening, rotation and a blocked app" || record intro-no-replay FAIL "replayed $((p2 - p1)) times"
  # Dark theme recording (same sequence).
  ctl --es cmd finish; adb shell cmd uimode night yes; sleep 2
  adb shell am start -W -n "$A/.MainActivity" >/dev/null; sleep 2
  if tap_text "New session" && sleep 1.5 && xy=$(find_text "Start focus"); then
    adb shell screenrecord --time-limit 5 --bit-rate 8000000 /sdcard/dark.mp4 & rec=$!
    sleep 1; adb shell input tap $xy; wait $rec
    adb pull /sdcard/dark.mp4 "$OUT/start-sequence-dark.mp4" >/dev/null 2>&1
    adb shell screencap -p /sdcard/s.png; adb pull /sdcard/s.png "$OUT/lock-dark.png" >/dev/null 2>&1
  fi
  adb shell cmd uimode night no; p2=$(intro_count "played session=")
  # Timer only (and a later start after the block screen): the turn plays, the shackle stays open.
  ctl --es cmd finish; ctl --es cmd rules; sleep 1
  c2=$(intro_count "shackle_closed")
  if manual_start "Start timer"; then
    sleep 4
    [ "$(intro_count "played session=")" = $((p2 + 1)) ] && [ "$(intro_count "shackle_closed")" = "$c2" ] \
      && record intro-timer-only pass "timer-only start played the turn; shackle stayed open" || record intro-timer-only FAIL "shackle shut or no sequence"
  else record intro-timer-only FAIL "couldn't start a timer"; fi
  # Remove animations: no turn, a fade instead.
  ctl --es cmd finish; ctl --es cmd rules --es block "$T"; anim_scale 0; sleep 1
  if manual_start "Start focus"; then
    sleep 3
    adb logcat -d -s LockdInIntro:I | grep played | tail -1 | grep -q 'reduced=true' \
      && record intro-reduced-motion pass "Remove animations: fade only" || record intro-reduced-motion FAIL "turn not replaced"
  else record intro-reduced-motion FAIL "couldn't start"; fi
  ctl --es cmd finish; home
}

ce_phase() {
  # Calendar (C): a local calendar with one event; Lockd In reads it through Android's provider.
  adb shell pm grant $A android.permission.READ_CALENDAR || true
  # The whole command is quoted for the device shell ('&' in the URI must not reach it unquoted).
  adb shell "content insert --uri 'content://com.android.calendar/calendars?caller_is_syncadapter=true&account_name=ci&account_type=LOCAL' --bind account_name:s:ci --bind account_type:s:LOCAL --bind name:s:CI --bind calendar_displayName:s:CI --bind calendar_access_level:i:700 --bind ownerAccount:s:ci --bind visible:i:1 --bind sync_events:i:1 --bind calendar_timezone:s:UTC" 2>&1 | head -2
  cal=$(adb shell "content query --uri content://com.android.calendar/calendars --projection _id --where \"account_name='ci'\"" | grep -oE '_id=[0-9]+' | head -1 | cut -d= -f2)
  note "  CI calendar id: ${cal:-none}"
  s0=$(( ($(date +%s) + 7200) * 1000 )); e0=$(( s0 + 3600000 ))
  adb shell "content insert --uri content://com.android.calendar/events --bind calendar_id:i:${cal:-1} --bind title:s:CIExam --bind dtstart:l:$s0 --bind dtend:l:$e0 --bind eventTimezone:s:UTC" 2>&1 | head -2
  adb logcat -c; ctl --es cmd calendar; sleep 2
  line=$(adb logcat -d -s LockdInTest:I | grep "calendar permitted" | tail -1)
  echo "$line" | grep -q "titles=.*CIExam" && record C-calendar-read pass "device calendar read: ${line#*permitted=}" || record C-calendar-read FAIL "event not read: ${line:-no log}"
  # Location routines (E): arriving at a saved place (emulator location) gives a reminder.
  adb shell pm grant $A android.permission.ACCESS_FINE_LOCATION || true
  adb shell pm grant $A android.permission.ACCESS_COARSE_LOCATION || true
  adb shell pm grant $A android.permission.ACCESS_BACKGROUND_LOCATION || true
  adb shell settings put secure location_mode 3 || true
  ctl --es cmd finish
  adb emu geo fix -88.30 40.00 >/dev/null 2>&1; sleep 5
  adb logcat -c
  ctl --es cmd place --es lat 40.1106 --es lon -88.2073; sleep 5
  adb shell appops set $A android:mock_location allow || true
  ctl --es cmd mockloc --es lat 40.00 --es lon -88.30; sleep 8
  seen=""
  for _ in $(seq 1 30); do
    adb emu geo fix -88.2073 40.1106 >/dev/null 2>&1
    ctl --es cmd mockloc --es lat 40.1106 --es lon -88.2073
    adb logcat -d -s LockdInPlaces:I | grep -q "place_arrive CIPlace" && { seen=1; break; }
    sleep 5
  done
  pl=$(adb logcat -d -s LockdInPlaces:I | tail -5 | tr '\n' ' ')
  [ -n "$seen" ] && record E-place-arrive pass "arrival noticed: $(adb logcat -d -s LockdInPlaces:I | grep place_arrive | tail -1 | sed 's/.*place_arrive //')" \
    || record E-place-arrive FAIL "no arrival within 150 s on the emulator ($pl)"
  notif_has "at CIPlace" && record E-place-remind pass "reminder posted" || record E-place-remind FAIL "no reminder notification"
  note "  $(adb logcat -d -s LockdInTest:I | grep mockloc | tail -1)"
  # App logic alone (no geofence delivery): arrival in the window posts the reminder once a day.
  ctl --es cmd finish; adb shell cmd notification cancel_all >/dev/null 2>&1 || adb shell service call notification 1 >/dev/null 2>&1
  adb shell "run-as $A true" >/dev/null 2>&1
  ctl --es cmd place --es lat 40.1106 --es lon -88.2073; sleep 2
  adb logcat -c; ctl --es cmd arrive; sleep 3
  adb logcat -d -s LockdInPlaces:I | grep -qE "place_arrive CIPlace: (app.lockdin.engine.Arrival\$Remind|.*Remind)|already today" \
    && record E-place-logic pass "$(adb logcat -d -s LockdInPlaces:I | grep place_arrive | tail -1 | sed 's/.*place_arrive //')" \
    || record E-place-logic FAIL "$(adb logcat -d -s LockdInPlaces:I | tail -2 | tr '\n' ' ')"
}

plan_phase() {
  # ---------------- Milestone A: planned sessions ----------------
  ctl --es cmd finish
  adb shell appops set $A SCHEDULE_EXACT_ALARM allow
  # Phone clock: plan for the start of the next-but-one minute (schedules are minute-precise).
  ph=$(adb shell date +%H:%M:%S | tr -d '\r'); h=${ph%%:*}; m=${ph#*:}; m=${m%%:*}; sec=${ph##*:}
  mins=$((10#$h * 60 + 10#$m + 2)); T1=$(printf '%02d:%02d' $(( (mins / 60) % 24 )) $(( mins % 60 )))
  mins2=$((mins + 1)); T2=$(printf '%02d:%02d' $(( (mins2 / 60) % 24 )) $(( mins2 % 60 )))
  wait1=$(( (2 * 60 - 10#$sec) ))
  ctl --es cmd plan --es at "$T1" --es title ManualPlan --ez auto false --es block "$T"
  ctl --es cmd plan --es at "$T2" --es title AutoPlan --ez auto true --es block "$T" --ei focus 2
  note "  planned $T1 (manual) and $T2 (automatic) at phone time $ph"
  sleep $(( wait1 + 15 ))
  notif_has "ManualPlan starts now" && record A-start-reminder pass "'starts now' notice with Start now for a non-automatic plan" \
    || record A-start-reminder FAIL "no 'starts now' notice at $T1"
  service_up && record A-manual-not-started FAIL "a session started without the person" || record A-manual-not-started pass "nothing started without the person"
  sleep 60
  service_up && record A-auto-start pass "automatic plan started the blocking service at $T2" || record A-auto-start FAIL "service not running after $T2"
  notif_has "AutoPlan started" && record A-auto-notice pass "AutoPlan started notice" || record A-auto-notice FAIL "no start notice"
  t=$(top); [[ "$t" == "$A/"* ]] && record A-auto-not-onscreen FAIL "app came to the front ($t)" || record A-auto-not-onscreen pass "app stayed in the background ($t)"
  pa=$(intro_count "played session="); adb shell am start -W -n "$A/.MainActivity" >/dev/null; sleep 3; home
  [ "$(intro_count "played session=")" = "$pa" ] && record A-auto-no-sequence pass "opening the app during a scheduled session didn't play the start sequence" \
    || record A-auto-no-sequence FAIL "start sequence played for a scheduled session"
  r=$(trial_blocked 6000) && record A-auto-blocks pass "target covered during the automatic session (${r} ms)" || record A-auto-blocks FAIL "$r"
  ctl --es cmd emergency; sleep 3
  # Diagnostics for this phase (annotations are the only way to read CI output from here).
  echo "::notice title=Plan diagnostics (EMULATOR)::$( (adb shell dumpsys alarm | grep -B1 -A4 'app.lockdin' | head -30; adb logcat -d -s LockdInPlan LockdInTest AndroidRuntime:E | tail -40) | sed 's/%/%25/g' | tr '\n' '\r' | sed 's/\r/%0A/g' | cut -c1-6000)"
}

boot_wait
adb shell settings put global window_animation_scale 0
adb shell settings put global transition_animation_scale 0
adb shell settings put global animator_duration_scale 0
adb install -r "$TARGET_APK" >/dev/null
adb install -r "$APP_APK" >/dev/null
# On a phone the user grants these in Settings during onboarding; pre-granted here.
adb shell appops set $A GET_USAGE_STATS allow
adb shell appops set $A SYSTEM_ALERT_WINDOW allow
adb shell pm grant $A android.permission.POST_NOTIFICATIONS || true
adb logcat -c
if [ "${ONLY:-}" = site ]; then
  # Diagnostics for website blocking in Chrome only, with Accessibility as the only permission
  # (as in the full run's Accessibility phase).
  adb shell am start -W -n "$A/.MainActivity" >/dev/null; sleep 2; home
  adb shell appops set $A GET_USAGE_STATS deny
  adb shell appops set $A SYSTEM_ALERT_WINDOW deny
  adb shell settings put secure enabled_accessibility_services "$A/$A.enforce.FocusAccessibilityService"
  adb shell settings put secure accessibility_enabled 1; sleep 4
  ctl --es cmd start --ei focus 4 --ei break 1 --ei rounds 1 --es block "$T" --es sites example.com
  adb shell am start -W -n "$A/.MainActivity" >/dev/null; sleep 3; home
  adb shell am set-debug-app --persistent com.android.chrome >/dev/null 2>&1 || true
  adb shell "echo 'chrome --disable-fre --no-default-browser-check --no-first-run' > /data/local/tmp/chrome-command-line"
  adb shell am force-stop com.android.chrome
  adb shell am start -a android.intent.action.VIEW -d "https://www.example.com/" com.android.chrome >/dev/null 2>&1
  r=$(wait_top "${BLOCK//./\\.}" 15000) && record a11y-site-block pass "covered (${r} ms)" || record a11y-site-block FAIL "$r"
  adb exec-out uiautomator dump /dev/tty > "$OUT/chrome.xml" 2>/dev/null
  ids=$(grep -oE 'resource-id="[^"]*(url|location|omnibox|toolbar)[^"]*"[^>]*' "$OUT/chrome.xml" | sed -E 's/ (class|package|checkable|checked|clickable|enabled|focusable|long-clickable|password|scrollable|selected|bounds|index|NAF|drawing-order|hint)="[^"]*"//g' | head -15)
  echo "::notice title=Chrome diagnostics (EMULATOR)::$( (adb shell dumpsys package com.android.chrome | grep -m1 versionName; echo "$ids"; adb logcat -d -s LockdInFocus ActivityTaskManager:W | tail -40) | sed 's/%/%25/g' | tr '\n' '\r' | sed 's/\r/%0A/g' | cut -c1-6000)"
  cat "$RESULTS"; exit $FAILED
fi
# The first-run introduction is covered by UI tests; the E2E drives the main screens.
adb shell am start -W -n "$A/.MainActivity" >/dev/null; sleep 2
ctl --es cmd onboarded; home
if [ "${ONLY:-}" = ce ]; then
  ce_phase
  cat "$RESULTS"; echo "::notice title=Calendar and places (EMULATOR)::$(tr '\n' ' ' < "$RESULTS")"
  exit $FAILED
fi
if [ "${ONLY:-}" = audio ]; then
  audio_phase
  echo "::notice title=Study audio log (EMULATOR)::$(adb logcat -d -s LockdInFocus:I LockdInAudio:I | grep -E 'music|audio|play|cover' | tail -40 | sed 's/%/%25/g' | tr '\n' '\r' | sed 's/\r/%0A/g' | cut -c1-6000)"
  cat "$RESULTS"; echo "::notice title=Study audio (EMULATOR)::$(tr '\n' ' ' < "$RESULTS")"
  exit $FAILED
fi
if [ "${ONLY:-}" = intro ]; then
  adb shell am start -W -n "$A/.MainActivity" >/dev/null; sleep 2; home
  intro_phase
  plan_phase
  cat "$RESULTS"; grep -q " FAIL " "$RESULTS" && echo "::error title=Start sequence (EMULATOR)::$(grep ' FAIL ' "$RESULTS" | tr '\n' ' ')"
  echo "::notice title=Start sequence (EMULATOR)::$(tr '\n' ' ' < "$RESULTS")"
  exit $FAILED
fi
if [ "${ONLY:-}" = plan ]; then
  adb shell am start -W -n "$A/.MainActivity" >/dev/null; sleep 2; home
  plan_phase
  cat "$RESULTS"; grep -q " FAIL " "$RESULTS" && echo "::error title=Plan phase (EMULATOR)::$(grep ' FAIL ' "$RESULTS" | tr '\n' ' ')"
  echo "::notice title=Plan phase (EMULATOR)::$(tr '\n' ' ' < "$RESULTS")"
  exit $FAILED
fi

# Start: open the app once (leaves the "stopped" install state), start the session through
# the debug-only control, then bring the app to the front so it starts the service.
adb shell am start -W -n "$A/.MainActivity" >/dev/null; sleep 2
ctl --es cmd start --ei focus 4 --ei break 2 --ei rounds 2 --es block "$T"
T0=$(date +%s)
adb shell am start -W -n "$A/.MainActivity" >/dev/null; sleep 3
adb shell screencap -p /sdcard/app-running.png; adb pull /sdcard/app-running.png "$OUT/" >/dev/null 2>&1
home
if service_up; then record M1.11-android-service pass "FocusService running after start"; else record M1.11-android-service FAIL "FocusService not running after start"; fi
seen=""; for _ in $(seq 1 15); do notif_has "Focusing until" && { seen=1; break; }; sleep 1; done
[ -n "$seen" ] && record session-notification pass "ongoing notification shown" || record session-notification FAIL "no ongoing session notification within 15 s"

# M1.5: 20 trials.
pass=0; lat=()
for i in $(seq 1 20); do
  r=$(trial_blocked 5000) && { pass=$((pass+1)); lat+=("$r"); }
  note "  trial $i: $r"
  if [ "$i" = 1 ]; then
    launch_target; wait_top "${BLOCK//./\\.}" 5000 >/dev/null
    adb shell screencap -p /sdcard/block.png; adb pull /sdcard/block.png "$OUT/block-screen.png" >/dev/null 2>&1
    adb exec-out uiautomator dump /dev/tty > "$OUT/block-screen.xml" 2>/dev/null || true
    home
  fi
done
sorted=$(printf '%s\n' "${lat[@]:-}" | sort -n)
p50=$(echo "$sorted" | awk '{a[NR]=$1} END{print a[int((NR+1)/2)]}')
max=$(echo "$sorted" | tail -1)
[ "$pass" = 20 ] && record M1.5-block-screen pass "20/20 trials; start→block on top p50 ${p50} ms, max ${max} ms (incl. adb)" \
                  || record M1.5-block-screen FAIL "$pass/20 trials covered"
grep -q 'Return to work' "$OUT/block-screen.xml" && grep -q 'blocked during focus' "$OUT/block-screen.xml" \
  && record block-screen-content pass "task, timing and Return to work shown" || record block-screen-content FAIL "block screen text missing"

# M1.10: the target was covered, not killed.
[ -n "$(adb shell pidof $T)" ] && record M1.10-not-killed pass "target process still alive after 20 covers" \
                               || record M1.10-not-killed FAIL "target process is gone"

# M1.6: Settings stays usable during focus.
adb shell am start -a android.settings.SETTINGS >/dev/null; sleep 3
t=$(top); home
[[ "$t" != *BlockActivity* ]] && record M1.6-essentials pass "Settings opened during focus ($t)" || record M1.6-essentials FAIL "Settings was covered"

# M1.9: usage access revoked → reported within 10 s, nothing claimed; restored → blocking again.
adb shell appops set $A GET_USAGE_STATS deny
start=$(now_ms); seen=""
while (( $(now_ms) - start < 10000 )); do notif_has "Blocking paused" && { seen=$(( $(now_ms) - start )); break; }; sleep 0.5; done
[ -n "$seen" ] && record M1.9-permission-loss pass "'Blocking paused' notification after ${seen} ms" || record M1.9-permission-loss FAIL "no notification within 10 s"
adb shell appops set $A GET_USAGE_STATS allow; sleep 3
r=$(trial_blocked 10000) && record M1.9-permission-restored pass "blocking resumed (${r} ms)" || record M1.9-permission-restored FAIL "$r"

# M1.8 (part): process killed → Android restarts the service → blocking resumes.
if adb root >/dev/null 2>&1; then
  sleep 3; adb wait-for-device
  pid=$(adb shell pidof $A)
  adb shell kill -9 "$pid"; killed=$(now_ms); back=""
  while (( $(now_ms) - killed < 45000 )); do
    sleep 2
    r=$(trial_blocked 2500) && { back=$(( $(now_ms) - killed )); break; }
  done
  [ -n "$back" ] && record M1.8-process-kill pass "blocking back ${back} ms after kill -9" \
                 || record M1.8-process-kill FAIL "blocking did not resume within 45 s of kill -9 (see logcat)"
  # Keep the rest of the test independent of this result: opening the app restarts the service.
  [ -z "$back" ] && { adb shell am start -W -n "$A/.MainActivity" >/dev/null; sleep 2; home; }
else
  record M1.8-process-kill info "adb root unavailable; not tested"
fi

# M1.7: break releases.
until_s $(( 4 * 60 + 5 ))
ok=0; for i in 1 2 3; do r=$(trial_open) && ok=$((ok+1)); note "  break trial $i: $r"; done
[ "$ok" = 3 ] && record M1.7-break-release pass "target opened normally 3/3 during the break" || record M1.7-break-release FAIL "$ok/3 opened during the break"
notif_has "Break until" && record break-notification pass "notification says Break until" || record break-notification FAIL "notification not updated for the break"

# M1.7: focus resumes after the break.
until_s $(( 6 * 60 + 5 ))
r=$(trial_blocked 6000) && record M1.7-focus-resumes pass "blocked again in round 2 (${r} ms)" || record M1.7-focus-resumes FAIL "$r"

# M1.8 (part): reboot mid-session → blocking resumes after boot.
adb logcat -d -s LockdInFocus LockdInTest > "$OUT/logcat-before-reboot.txt" 2>/dev/null || true
adb reboot; rebooted=$(now_ms)
boot_wait
back=""
while (( $(now_ms) - rebooted < 150000 )); do
  r=$(trial_blocked 3000) && { back=$(( $(now_ms) - rebooted )); break; }
  sleep 2
done
[ -n "$back" ] && record M1.8-reboot pass "blocking back ${back} ms after adb reboot (incl. boot)" || record M1.8-reboot FAIL "not blocking after reboot"

# Release at the end.
until_s $(( 10 * 60 + 10 ))
r=$(trial_open) && record M1.7-end-release pass "target opens normally after the session" || record M1.7-end-release FAIL "$r"
sleep 2
service_up && record service-stopped FAIL "FocusService still running after the end" || record service-stopped pass "FocusService stopped after the end"
notif_has "Focusing until\|Break until" && record notification-cleared FAIL "session notification still shown" || record notification-cleared pass "session notification removed"

# ---------------- Accessibility mode (owner decision 2026-11-01) ----------------
adb logcat -d -s LockdInFocus LockdInTest > "$OUT/logcat-usage-phase.txt" 2>/dev/null || true
adb logcat -c
# Accessibility alone must be enough: take the usage-access permissions away first.
adb shell appops set $A GET_USAGE_STATS deny
adb shell appops set $A SYSTEM_ALERT_WINDOW deny
adb shell settings put secure enabled_accessibility_services "$A/$A.enforce.FocusAccessibilityService"
adb shell settings put secure accessibility_enabled 1
sleep 4
ctl --es cmd finish
ctl --es cmd start --ei focus 4 --ei break 1 --ei rounds 1 --es block "$T" --es sites example.com
T0=$(date +%s)
adb shell am start -W -n "$A/.MainActivity" >/dev/null; sleep 3; home
pass=0
for i in $(seq 1 10); do r=$(trial_blocked 5000) && pass=$((pass+1)); note "  a11y trial $i: $r"; done
# Owner report 2026-11-01: opening a blocked app three times quickly let the third through.
# Re-open straight after leaving the block screen, with no pause.
rapid=0
for i in $(seq 1 8); do
  launch_target
  r=$(wait_top "${BLOCK//./\\.}" 4000) && rapid=$((rapid+1))
  adb shell input keyevent KEYCODE_HOME
done
[ "$rapid" = 8 ] && record a11y-rapid-reopen pass "8/8 quick re-opens covered" || record a11y-rapid-reopen FAIL "$rapid/8 quick re-opens covered"
[ "$pass" = 10 ] && record a11y-block-screen pass "10/10 covered with only Accessibility on" || record a11y-block-screen FAIL "$pass/10 covered with only Accessibility on"

# Website blocking in Chrome, if this emulator image has it.
if adb shell pm path com.android.chrome >/dev/null 2>&1; then
  adb shell am set-debug-app --persistent com.android.chrome >/dev/null 2>&1 || true
  adb shell "echo 'chrome --disable-fre --no-default-browser-check --no-first-run' > /data/local/tmp/chrome-command-line"
  adb shell am force-stop com.android.chrome
  adb shell am start -a android.intent.action.VIEW -d "https://www.example.com/" com.android.chrome >/dev/null 2>&1
  r=$(wait_top "${BLOCK//./\\.}" 15000) && record a11y-site-block pass "example.com covered in Chrome (${r} ms after opening)" \
    || { record a11y-site-block FAIL "example.com not covered in Chrome: $r"
         adb shell screencap -p /sdcard/c.png; adb pull /sdcard/c.png "$OUT/chrome-fail.png" >/dev/null 2>&1
         adb exec-out uiautomator dump /dev/tty 2>/dev/null | grep -oE 'text="[^"]+"' | head -20 | tr '\n' ' ' > "$OUT/chrome-texts.txt"
         echo "::notice title=Chrome screen texts (EMULATOR)::$(cat "$OUT/chrome-texts.txt" | sed 's/%/%25/g' | cut -c1-3000)"
         echo "::notice title=Site-block diagnostics (EMULATOR)::$( (adb shell dumpsys activity activities | grep -E 'topResumed|ResumedActivity|BlockActivity' | head -8; adb logcat -d -s LockdInFocus | tail -40) | sed 's/%/%25/g' | tr '\n' '\r' | sed 's/\r/%0A/g' | cut -c1-6000)"; }
  home
  adb shell am start -a android.intent.action.VIEW -d "https://www.iana.org/" com.android.chrome >/dev/null 2>&1; sleep 6
  t=$(top); home
  [[ "$t" != *BlockActivity* ]] && record a11y-other-site-open pass "an unblocked site stayed open ($t)" || record a11y-other-site-open FAIL "an unblocked site was covered"
else
  record a11y-site-block info "Chrome isn't on this emulator image; site blocking not tested"
fi

# Turning Accessibility off with no fallback permissions: reported, not claimed.
adb shell settings delete secure enabled_accessibility_services
seen=""; start=$(now_ms)
while (( $(now_ms) - start < 12000 )); do notif_has "Blocking paused" && { seen=$(( $(now_ms) - start )); break; }; sleep 0.5; done
[ -n "$seen" ] && record a11y-off-reported pass "'Blocking paused' ${seen} ms after Accessibility was turned off" || record a11y-off-reported FAIL "no notice within 12 s"
# Fallback: usage access + display over other apps keep blocking without Accessibility.
adb shell appops set $A GET_USAGE_STATS allow
adb shell appops set $A SYSTEM_ALERT_WINDOW allow
sleep 3
r=$(trial_blocked 10000) && record a11y-fallback-usage pass "usage access took over (${r} ms)" || record a11y-fallback-usage FAIL "$r"
# Emergency recovery releases immediately.
ctl --es cmd emergency; sleep 3
r=$(trial_open) && record emergency-release pass "target opens right after emergency recovery" || record emergency-release FAIL "$r"


intro_phase
audio_phase
plan_phase
ce_phase
# History (roadmap step 2): sessions left above are saved and survive the process being killed.
adb shell am force-stop $A; sleep 2
n=$(adb shell run-as $A cat no_backup/history.v1.json 2>/dev/null | grep -o '"outcome"' | wc -l | tr -d ' ')
[ "${n:-0}" -ge 3 ] && record B-history-persists pass "$n finished sessions in history after force-stop" || record B-history-persists FAIL "history has ${n:-0} entries after force-stop"

adb logcat -d -s LockdInFocus LockdInTest LockdInPlan ActivityTaskManager:W ActivityManager:W > "$OUT/logcat.txt" 2>/dev/null || true
# On-device detection time: from Android's record of the app coming to the front to the
# service asking for the block screen (no adb overhead). Logcat is cleared on reboot.
det=$(cat "$OUT/logcat-before-reboot.txt" "$OUT/logcat-usage-phase.txt" 2>/dev/null | grep -v 'source=a11y' | grep -oE 'detect_ms=[0-9]+' | cut -d= -f2 | sort -n)
deta=$(grep 'source=a11y kind=app' "$OUT/logcat.txt" 2>/dev/null | grep -oE 'detect_ms=[0-9]+' | cut -d= -f2 | sort -n)
if [ -n "$deta" ]; then
  n=$(echo "$deta" | wc -l)
  record a11y-detection-latency info "n=$n p50=$(echo "$deta" | awk -v n="$n" 'NR==int((n+1)/2)') ms max=$(echo "$deta" | tail -1) ms (window change → block requested, on-device)"
fi
if [ -n "$det" ]; then
  n=$(echo "$det" | wc -l)
  p50=$(echo "$det" | awk -v n="$n" 'NR==int((n+1)/2)'); p95=$(echo "$det" | awk -v n="$n" 'NR==int(n*0.95+0.999)'); mx=$(echo "$det" | tail -1)
  record usage-detection-latency info "n=$n p50=${p50} ms p95=${p95} ms max=${mx} ms (app to front → block requested, on-device)"
fi
enc() { sed 's/%/%25/g' | tr '\n' '\r' | sed 's/\r/%0A/g'; }
echo "::notice title=Enforcement E2E (EMULATOR ONLY) — $(grep -c ' pass ' "$RESULTS") pass, $(grep -c ' FAIL ' "$RESULTS") fail::$(grep -vE '^  ' "$RESULTS" | enc)"
grep -q ' FAIL ' "$RESULTS" && echo "::error title=Enforcement E2E failures (EMULATOR)::$(grep ' FAIL ' "$RESULTS" | enc)"
cat "$RESULTS"
exit $FAILED
