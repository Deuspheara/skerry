#!/usr/bin/env python3
"""Control for analyze-recording.py: how a plain list scrolls in a simulator recording. Reports, per distinct presented frame during the scroll, the vertical shift
against the previous distinct frame, and how many shifts go against the scroll's direction (a zigzag)."""
import json, subprocess, sys
import numpy as np
path = sys.argv[1]
frames_json = json.loads(subprocess.run(["ffprobe", "-v", "error", "-select_streams", "v", "-show_entries", "frame=pts_time", "-of", "json", path], capture_output=True, text=True).stdout)["frames"]
times = np.array([float(f["pts_time"]) for f in frames_json])
meta = json.loads(subprocess.run(["ffprobe", "-v", "error", "-select_streams", "v", "-show_entries", "stream=width,height", "-of", "json", path], capture_output=True, text=True).stdout)["streams"][0]
W, H = meta["width"], meta["height"]
raw = subprocess.run(["ffmpeg", "-v", "error", "-i", path, "-vsync", "passthrough", "-vf", f"crop=200:{H}:{int(W*0.35)}:0,scale=100:{H//2}", "-pix_fmt", "gray", "-f", "rawvideo", "-"], capture_output=True).stdout
h2 = H // 2
frames = np.frombuffer(raw, dtype=np.uint8).reshape(-1, h2, 100)
n = min(len(frames), len(times)); frames, times = frames[:n], times[:n]
prof = frames.astype(float).mean(axis=2)
shifts, ts = [], []
last = 0
for i in range(1, n):
    a, b = prof[last, 80:h2-80], prof[i]
    if np.abs(prof[i] - prof[last]).mean() < 0.4: continue
    best, bs = 1e18, 0
    for s in range(-60, 61):
        lo, hi = 80 + s, h2 - 80 + s
        if lo < 0 or hi > h2: continue
        sad = np.abs(b[lo:hi] - a).mean()
        if sad < best: best, bs = sad, s
    if best < 12 and bs != 0:
        shifts.append(bs); ts.append(times[i])
    last = i
shifts = np.array(shifts)
print("distinct scroll steps:", len(shifts))
if len(shifts):
    dom = np.sign(np.sum(shifts))
    print("against the dominant direction:", int(np.sum(np.sign(shifts) == -dom)), f"({100*np.mean(np.sign(shifts) == -dom):.1f}%)")
    print("first 40 shifts:", shifts[:40].tolist())
