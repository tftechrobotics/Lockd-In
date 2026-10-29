#!/usr/bin/env bash
# Records the Play "foreground service" demo video on a CI emulator and saves
# evidence. Emulator only: this is NOT evidence about any physical phone.
# Usage: ci/emulator-demo.sh <probe.apk> <target.apk> <out-dir>
set -euo pipefail
PROBE_APK=$1 TARGET_APK=$2 OUT=$3
mkdir -p "$OUT"
P=app.lockdin.probe

adb wait-for-device
adb shell 'while [ "$(getprop sys.boot_completed)" != 1 ]; do sleep 1; done'
adb shell settings put global window_animation_scale 0
adb shell settings put global transition_animation_scale 0
adb shell settings put global animator_duration_scale 0
adb shell input keyevent KEYCODE_WAKEUP
adb shell wm dismiss-keyguard || true

adb install -r "$TARGET_APK"
adb install -r "$PROBE_APK"
# The user grants these on the phone through Settings; pre-granted here so the
# video shows the feature itself.
adb shell appops set $P GET_USAGE_STATS allow
adb shell appops set $P SYSTEM_ALERT_WINDOW allow
adb shell pm grant $P android.permission.POST_NOTIFICATIONS

# Tap the first on-screen element whose text matches (case-insensitive),
# scrolling down up to 8 times to find it.
tap_text() {
  local want=$1 i b
  for i in $(seq 0 8); do
    adb exec-out uiautomator dump /dev/tty 2>/dev/null > "$OUT/ui.xml" || true
    b=$(python3 -I - "$want" "$OUT/ui.xml" <<'PY'
import sys, re
want, xml = sys.argv[1].lower(), open(sys.argv[2], errors="replace").read()
for m in re.finditer(r'<node [^>]*?text="([^"]*)"[^>]*?bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"', xml):
    if want in m.group(1).lower():
        x1, y1, x2, y2 = map(int, m.groups()[1:])
        print((x1 + x2) // 2, (y1 + y2) // 2); break
PY
)
    if [ -n "$b" ]; then adb shell input tap $b; return 0; fi
    adb shell input swipe 540 1600 540 700 300
    sleep 1
  done
  echo "::error::Could not find on-screen text: $want"; return 1
}

adb shell screenrecord --bit-rate 4000000 --time-limit 120 /sdcard/demo.mp4 &
REC=$!
sleep 2
adb shell am start -W -n $P/.MainActivity
sleep 3
tap_text "Run smoke test"
sleep 4
adb shell cmd statusbar expand-notifications; sleep 4
adb shell cmd statusbar collapse; sleep 1
adb shell am start -n app.lockdin.probetarget/.TargetActivity
sleep 6
adb shell screencap -p /sdcard/block.png
adb shell am start -n $P/.MainActivity
sleep 3
tap_text "End now"
sleep 4
adb shell am start -n app.lockdin.probetarget/.TargetActivity
sleep 5
adb shell input keyevent KEYCODE_HOME
sleep 2
kill -INT $REC 2>/dev/null || true
wait $REC || true
sleep 3
adb pull /sdcard/demo.mp4 "$OUT/lockdin-probe-fgs-demo.mp4"
adb pull /sdcard/block.png "$OUT/block-screen.png"
adb shell am start -W -n $P/.MainActivity; sleep 2
adb exec-out uiautomator dump /dev/tty > "$OUT/final-main-screen.xml" 2>/dev/null || true
adb shell dumpsys activity services $P > "$OUT/services-after-end.txt" || true
adb logcat -d > "$OUT/logcat.txt" || true
# Evidence summary: what the probe's own on-screen log shows.
grep -oE '(session_[a-z_]+|blocked_[a-z_]+|cover_[a-z_]+|overlay_[a-z_]+|block_screen_[a-z_]+)' "$OUT/final-main-screen.xml" | sort | uniq -c > "$OUT/events-seen.txt" || true
cat "$OUT/events-seen.txt"
ls -la "$OUT"
