#!/bin/bash
# Memory over a long stream: heavy chat traffic on the demo data for <minutes>, a meminfo sample every 30 s.
#   chat-memory.sh [minutes=5]        (needs the "benchmark" build installed; see chat-load.sh)
PKG=com.twitchplayer.tv
MIN=${1:-5}
adb shell pm clear $PKG >/dev/null
adb shell settings put global twitchtv_demo_chat_heavy 1
adb shell am force-stop $PKG; adb shell am start -n $PKG/.MainActivity >/dev/null; sleep 5
adb shell input keyevent 23; sleep 4
echo "t(s)  PSS(MB)  JavaHeap(MB)  NativeHeap(MB)  Graphics(MB)"
for ((i = 0; i <= MIN * 2; i++)); do
  adb shell dumpsys meminfo $PKG | awk -v t=$((i * 30)) '
    /Java Heap:/ {j=$3} /Native Heap:/ {n=$3} /Graphics:/ {g=$2} /TOTAL PSS:/ {p=$3}
    END {printf "%4d  %7.1f  %11.1f  %13.1f  %12.1f\n", t, p/1024, j/1024, n/1024, g/1024}'
  [ $i -lt $((MIN * 2)) ] && sleep 30
done
