#!/usr/bin/env python3
"""Per-frame stats from `dumpsys gfxinfo <pkg> framestats` (ms).
  ui  = UI-thread work: input (or, for frames without input such as chat traffic and animations, AnimationStart) -> sync queued
  rt  = RenderThread work (sync -> swap)
  gpu = swap -> GPU completed (on an emulator this is the host GPU and pinned to vsync: not a device measurement)
  vsync->done = intended vsync -> frame completed
The framestats ring buffer keeps only the last 120 frames (~2 s at 60 Hz), so for a longer window use
  framestats.py --watch <seconds>      (samples every second and merges frames by their intended vsync)
and `--input-only` restores the older numbers (frames that handled input only)."""
import os, subprocess, sys

# The TV app by default; `PKG=com.twitchplayer.phone framestats.py ...` measures the phone app.
pkg = os.environ.get("PKG", "com.twitchplayer.tv")
INPUT_ONLY = '--input-only' in sys.argv
SKIPPED = 8  # FrameInfoFlags::SkippedFrame


def sample():
    out = subprocess.run(['adb', 'shell', 'dumpsys', 'gfxinfo', pkg, 'framestats'], capture_output=True, text=True).stdout
    lines = out.splitlines()
    if '---PROFILEDATA---' not in lines:
        return {}, {}
    i = lines.index('---PROFILEDATA---') + 1
    hdr = lines[i].rstrip(',').split(','); i += 1
    cols = {n: k for k, n in enumerate(hdr)}
    rows = {}
    while i < len(lines) and lines[i] != '---PROFILEDATA---':
        f = lines[i].rstrip(',').split(',')
        if len(f) == len(hdr) and f[0].lstrip('-').isdigit():
            r = [int(x) for x in f]
            if not r[0] & SKIPPED:
                rows[r[cols['IntendedVsync']]] = r
        i += 1
    return rows, cols


rows, C = sample()
if '--watch' in sys.argv:
    import time
    end = time.time() + float(sys.argv[sys.argv.index('--watch') + 1])
    while time.time() < end:
        time.sleep(1.0)
        more, C = sample()
        rows.update(more)
rows = [rows[k] for k in sorted(rows)]
if not rows:
    sys.exit("no frames")


def pct(v, p):
    v = sorted(v); k = (len(v) - 1) * p; lo = int(k); hi = min(lo + 1, len(v) - 1)
    return v[lo] + (v[hi] - v[lo]) * (k - lo)


g = lambda r, n: r[C[n]]
ui = [(g(r, 'SyncQueued') - (g(r, 'HandleInputStart') or g(r, 'AnimationStart'))) / 1e6
      for r in rows if g(r, 'HandleInputStart') > 0 or (not INPUT_ONLY and g(r, 'AnimationStart') > 0)]
rt = [(g(r, 'SwapBuffers') - g(r, 'SyncStart')) / 1e6 for r in rows]
tot = [(g(r, 'FrameCompleted') - g(r, 'IntendedVsync')) / 1e6 for r in rows]
gpu = [(g(r, 'GpuCompleted') - g(r, 'SwapBuffers')) / 1e6 for r in rows if 'GpuCompleted' in C and g(r, 'GpuCompleted') > g(r, 'SwapBuffers')]


def line(n, v):
    return f"{n:6s} p50={pct(v,.5):5.2f} p90={pct(v,.9):5.2f} p95={pct(v,.95):5.2f} p99={pct(v,.99):5.2f} max={max(v):5.2f}" if v else n + ' n/a'


missed = sum(1 for r in rows if g(r, 'FrameCompleted') > g(r, 'FrameDeadline'))
span = (g(rows[-1], 'IntendedVsync') - g(rows[0], 'IntendedVsync')) / 1e9
print(f"frames={len(rows)} over {span:.1f}s ({len(rows)/max(span,1e-9):.0f}/s)  missedDeadline={missed} ({100*missed/max(1,len(rows)):.1f}%)  "
      f"ui>5ms={sum(1 for t in ui if t>5)}  ui>6.9ms(144Hz)={sum(1 for t in ui if t>6.94)}  ui>16.7ms={sum(1 for t in ui if t>16.7)}")
print(line('ui', ui)); print(line('rt', rt)); print(line('gpu', gpu)); print(line('vsync->done', tot))
