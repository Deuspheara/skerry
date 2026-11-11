#!/bin/bash
# Chat under sustained traffic on the demo data: opens the player of the first followed channel (chat visible),
# lets it warm up, then measures <seconds> of steady state: per-frame stats, CPU per thread, memory.
#   [PRE_KEYS="23 20 ..."] [LOAD_KEYS="19 19 20 20"] chat-load.sh <label> [seconds=20] [rate=heavy|normal] [apk]
# heavy = ~16 messages/s with 1-4 emotes each (half animated); normal = ~1 message/s. Needs the "benchmark" build:
#   ./gradlew :app:assembleBenchmark   (installed here from app/build/outputs/apk/benchmark unless [apk] is given)
PKG=com.twitchplayer.tv
HERE="$(cd "$(dirname "$0")" && pwd)"
LABEL=${1:-run}; SECS=${2:-20}; RATE=${3:-heavy}
APK=${4:-"$HERE/../../app/build/outputs/apk/benchmark/app-benchmark.apk"}
adb install -r -g "$APK" >/dev/null || exit 1
adb shell pm clear $PKG >/dev/null
adb shell settings put global twitchtv_demo_chat_heavy $([ "$RATE" = heavy ] && echo 1 || echo 0)
adb shell am force-stop $PKG; adb shell am start -n $PKG/.MainActivity >/dev/null; sleep 5
adb shell input keyevent 23; sleep 4                       # OK on the first card: player, chat visible
# Optional UI set-up before measuring, e.g. PRE_KEYS="22 22 22 22 22 22 23" opens the chat options (22 = right, 23 = OK).
for k in $PRE_KEYS; do adb shell input keyevent $k; sleep 0.4; done
sleep 20                                                    # warm-up: JIT, image decoding, caches
PID=$(adb shell pidof $PKG)
adb shell dumpsys gfxinfo $PKG reset >/dev/null
# Optional input during the measured window, repeated for its whole length (~5 keys/s: `input` starts a process per key).
if [ -n "$LOAD_KEYS" ]; then
  adb shell "end=\$((\$(date +%s) + $SECS)); while [ \$(date +%s) -lt \$end ]; do for k in $LOAD_KEYS; do input keyevent \$k; sleep 0.1; done; done" >/dev/null 2>&1 &
  KEYS_PID=$!
fi
adb shell "top -H -p $PID -b -n 2 -d $SECS" > /tmp/chat-load-top.$$ 2>/dev/null &
TOP_PID=$!
echo "== $LABEL ($RATE, ${SECS}s)"
python3 -I "$HERE/framestats.py" --watch "$SECS"
wait $TOP_PID; [ -n "$KEYS_PID" ] && wait $KEYS_PID
adb shell dumpsys gfxinfo $PKG | grep -E "Total frames rendered|Janky frames:|50th percentile|90th percentile|99th percentile"
echo "-- CPU per thread (% of one core, second sample)"
python3 -I - /tmp/chat-load-top.$$ <<'PY'
import sys, re
blocks = open(sys.argv[1]).read().split('Tasks:')
last = blocks[-1].splitlines()
rows = []
for l in last:
    f = l.split()
    if len(f) >= 12 and f[0].isdigit():
        try: rows.append((float(f[8].replace(",", ".")), f[11]))
        except ValueError: pass
rows.sort(reverse=True)
tot = sum(r[0] for r in rows)
for cpu, name in rows[:6]: print(f"  {cpu:5.1f}  {name}")
print(f"  {tot:5.1f}  total (all threads)")
PY
echo "-- memory"
adb shell dumpsys meminfo $PKG | grep -E "TOTAL PSS|Java Heap|Native Heap|Graphics:|Total PSS by" | head -6
rm -f /tmp/chat-load-top.$$
