#!/usr/bin/env bash
# Use this terminal as the remote when the emulator window ignores the keyboard.
# Arrows = D-pad, Enter/Space = OK, Esc or b = Back, h = Home, q = quit.
ADB="${ADB:-$HOME/Library/Android/sdk/platform-tools/adb}"
echo "Remote ready: arrows, Enter=OK, Esc/b=Back, h=Home, q=quit"
while IFS= read -rsn1 k; do
  if [[ $k == $'\e' ]]; then
    if IFS= read -rsn2 -t 0.05 rest; then
      case "$rest" in
        '[A') code=19 ;; '[B') code=20 ;; '[D') code=21 ;; '[C') code=22 ;;
        *) continue ;;
      esac
    else code=4; fi
  else
    case "$k" in
      '' | ' ') code=23 ;; b) code=4 ;; h) code=3 ;; q) exit 0 ;; *) continue ;;
    esac
  fi
  "$ADB" shell input keyevent "$code"
done
