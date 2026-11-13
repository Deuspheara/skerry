#!/usr/bin/env python3
"""Reads a simulator screen recording of the player sheet being dragged and reports how smoothly it MOVED on screen, frame by frame, from the pixels the
render server really presented (so GPU, video layer and compositing costs show up, which a main-thread timer cannot see).

    python3 apple/scripts/analyze-recording.py /tmp/drag-Debug.mp4

How: the sheet's top edge is tracked down a column inside the sheet (the first row from the top that is not the light page background). While the sheet moves
(that edge strictly between its two rest positions) every presented frame gives one position; with the frame's timestamp that is one speed. A smooth drag has
frames at even intervals and speeds that change gradually; a stutter shows as repeated positions (a frame that did not move), long gaps between frames, or
speed jumps. The recording only holds frames the screen changed (static periods are not written), which is what we want: gaps inside a movement are drops.
"""
import json, subprocess, sys
import numpy as np

path = sys.argv[1]
probe = subprocess.run(["ffprobe", "-v", "error", "-select_streams", "v", "-show_entries", "frame=pts_time", "-of", "json", path], capture_output=True, text=True)
times = [float(f["pts_time"]) for f in json.loads(probe.stdout)["frames"]]
meta = json.loads(subprocess.run(["ffprobe", "-v", "error", "-select_streams", "v", "-show_entries", "stream=width,height", "-of", "json", path], capture_output=True, text=True).stdout)["streams"][0]
W, H = meta["width"], meta["height"]
BAND = 60                       # the sheet's leading edge is within the first 60 px (20 pt) of the screen in every state
raw = subprocess.run(["ffmpeg", "-v", "error", "-i", path, "-vsync", "passthrough", "-vf", f"crop={BAND}:{H}:0:0", "-pix_fmt", "rgb24", "-f", "rawvideo", "-"], capture_output=True).stdout
frames = np.frombuffer(raw, dtype=np.uint8).reshape(-1, H, BAND, 3)
n = min(len(frames), len(times))
frames, times = frames[:n], np.array(times[:n])

def top_edge(frame):
    """Top row of the magenta bar the app draws on the sheet's leading edge while measuring (-frame-monitor); -1 when it is not on screen."""
    f = frame.astype(int)
    mask = (f[:, :, 0] > 200) & (f[:, :, 1] < 70) & (f[:, :, 2] > 200)
    rows = np.where(mask.sum(axis=1) >= 3)[0]
    return int(rows[0]) if len(rows) else -1

edges = np.array([top_edge(f) for f in frames])
valid = edges >= 0
# The sheet is moving while its top edge is between the top of the screen (expanded rest, with the status bar over it) and its docked rest.
moving = valid & (edges > H * 0.03)
docked = np.median(edges[edges > H * 0.5]) if (edges > H * 0.5).any() else H * 0.8
moving &= edges < docked - 6
segments, cur = [], []
for i in range(1, n):
    if moving[i] and (not cur or times[i] - times[cur[-1]] < 0.4):
        cur.append(i)
    else:
        if len(cur) >= 4: segments.append(cur)
        cur = [i] if moving[i] else []
if len(cur) >= 4: segments.append(cur)
if not segments:
    print("no movement segment found; edge values:", sorted(set(edges.tolist()))[:30]); sys.exit(1)

all_dt, dup, speeds_jump, frames_total, reversals, steps_total = [], 0, [], 0, 0, 0
for seg in segments:
    # The recording samples the host display (about twice the app's rate): keep one sample per distinct position, at the time it first appeared.
    keep = [seg[0]]
    for i in seg[1:]:
        if abs(edges[i] - edges[keep[-1]]) >= 1: keep.append(i)
    idx = keep
    t = times[idx]; y = edges[idx].astype(float)
    dt = np.diff(t); dy = np.diff(y)
    v = dy / np.maximum(dt, 1e-6)
    direction = np.sign(y[-1] - y[0]) or 1
    reversals += int(np.sum(np.sign(dy) == -direction)); steps_total += len(dy)
    fast = np.abs(dy) >= 6          # skip the slow start and tail, where one distinct position can legitimately last several frames
    dt, dy_fast = dt[fast[:len(dt)]], dy[fast[:len(dy)]]
    all_dt += dt.tolist(); frames_total += len(dt)
    dup += int(np.sum(dt > 0.025))
    v = v[np.abs(dy) >= 6]
    if len(v) > 2:
        sm = np.abs(np.diff(v)) / (np.median(np.abs(v)) + 1e-6)
        speeds_jump += sm.tolist()
dt = np.array(all_dt)
med = float(np.median(dt))
print(f"steps against the movement's direction (zigzag)={reversals} of {steps_total} ({100*reversals/max(1,steps_total):.1f}%)")
print(f"segments={len(segments)} presented intervals in motion={frames_total} median interval={med*1000:.1f} ms (~{1/med:.0f} fps)")
print(f"p95 interval={np.percentile(dt,95)*1000:.1f} ms, max interval={dt.max()*1000:.1f} ms, intervals > 1.5x median={int(np.sum(dt > 1.5*med))} ({100*np.mean(dt > 1.5*med):.1f}%)")
print(f"gaps > 25 ms between distinct positions (a skipped frame)={dup} of {frames_total} ({100*dup/frames_total:.1f}%)")
if speeds_jump:
    sj = np.array(speeds_jump)
    print(f"speed discontinuity (|dv| / median speed between consecutive frames): p50={np.percentile(sj,50):.2f} p95={np.percentile(sj,95):.2f} max={sj.max():.2f}")
