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

adb logcat -d -s LockdInFocus LockdInTest ActivityTaskManager:W ActivityManager:W > "$OUT/logcat.txt" 2>/dev/null || true
# On-device detection time: from Android's record of the app coming to the front to the
# service asking for the block screen (no adb overhead). Logcat is cleared on reboot.
det=$(cat "$OUT/logcat-before-reboot.txt" "$OUT/logcat.txt" 2>/dev/null | grep -oE 'detect_ms=[0-9]+' | cut -d= -f2 | sort -n)
if [ -n "$det" ]; then
  n=$(echo "$det" | wc -l)
  p50=$(echo "$det" | awk -v n="$n" 'NR==int((n+1)/2)'); p95=$(echo "$det" | awk -v n="$n" 'NR==int(n*0.95+0.999)'); mx=$(echo "$det" | tail -1)
  record detection-latency info "n=$n p50=${p50} ms p95=${p95} ms max=${mx} ms (app to front → block requested, on-device)"
fi
enc() { sed 's/%/%25/g' | tr '\n' '\r' | sed 's/\r/%0A/g'; }
echo "::notice title=Enforcement E2E (EMULATOR ONLY) — $(grep -c ' pass ' "$RESULTS") pass, $(grep -c ' FAIL ' "$RESULTS") fail::$(grep -vE '^  ' "$RESULTS" | enc)"
grep -q ' FAIL ' "$RESULTS" && echo "::error title=Enforcement E2E failures (EMULATOR)::$(grep ' FAIL ' "$RESULTS" | enc)"
cat "$RESULTS"
exit $FAILED
