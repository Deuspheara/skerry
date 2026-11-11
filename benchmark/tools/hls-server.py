#!/usr/bin/env python3
"""Fake Twitch-style HLS live stream for the emulator (debug build, demo mode).

Serves /live.m3u8 as a sliding live playlist over looping 2 s segments from SEG_DIR (s0.ts..sN.ts, see the ffmpeg line in
docs/NATIVE_PLAYER.md), with real wall-clock EXT-X-PROGRAM-DATE-TIME tags (so ExoPlayer can compute the latency) and a
stitched-ad EXT-X-DATERANGE covering the first 20 s of every minute (so the ad chip can be checked).

  python3 benchmark/tools/hls-server.py SEG_DIR [port] [skew_seconds]

The emulator's clock can differ from the host's: pass (device time - host time) as skew_seconds so the tags match the
device's clock (latency and the ad countdown are computed against it).
  adb shell settings put global twitchtv_demo_hls http://10.0.2.2:8099/live.m3u8
"""
import http.server, os, sys, time
from datetime import datetime, timezone

SEG_DIR = sys.argv[1]
PORT = int(sys.argv[2]) if len(sys.argv) > 2 else 8099
SKEW = int(sys.argv[3]) if len(sys.argv) > 3 else 0
N = len([f for f in os.listdir(SEG_DIR) if f.startswith("s") and f.endswith(".ts")])
T0 = (int(time.time()) + SKEW) // 2 * 2 - 600  # sequence 0 started ten minutes ago
WINDOW = 6

def iso(t):
    return datetime.fromtimestamp(t, timezone.utc).strftime("%Y-%m-%dT%H:%M:%S.000Z")

def playlist():
    now = time.time() + SKEW
    last = int((now - T0) // 2) - 1          # newest complete segment
    first = last - WINDOW + 1
    lines = ["#EXTM3U", "#EXT-X-VERSION:3", "#EXT-X-TARGETDURATION:2", f"#EXT-X-MEDIA-SEQUENCE:{first}",
             f"#EXT-X-DISCONTINUITY-SEQUENCE:{max(0, (first - 1) // N)}"]
    ad_start = int(now) // 60 * 60
    if ad_start + 20 > T0 + first * 2:
        lines.append(f'#EXT-X-DATERANGE:ID="stitched-ad-{ad_start}",CLASS="twitch-stitched-ad",START-DATE="{iso(ad_start)}",DURATION=20.000')
    for q in range(first, last + 1):
        if q % N == 0 and q > first:
            lines.append("#EXT-X-DISCONTINUITY")
        lines.append(f"#EXT-X-PROGRAM-DATE-TIME:{iso(T0 + q * 2)}")
        lines.append("#EXTINF:2.000,")
        lines.append(f"s{q % N}.ts?q={q}")
    return "\n".join(lines) + "\n"

class H(http.server.BaseHTTPRequestHandler):
    def do_GET(self):
        path = self.path.split("?")[0]
        if path == "/live.m3u8":
            body, kind = playlist().encode(), "application/vnd.apple.mpegurl"
        elif path.startswith("/s") and path.endswith(".ts") and os.path.exists(SEG_DIR + path):
            body, kind = open(SEG_DIR + path, "rb").read(), "video/mp2t"
        else:
            self.send_response(404); self.end_headers(); return
        self.send_response(200); self.send_header("Content-Type", kind); self.send_header("Content-Length", str(len(body)))
        self.send_header("Cache-Control", "no-cache"); self.end_headers(); self.wfile.write(body)
    def log_message(self, *a): pass

http.server.ThreadingHTTPServer(("0.0.0.0", PORT), H).serve_forever()
