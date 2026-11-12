#!/usr/bin/env python3
"""Prints Macrobenchmark results as Markdown tables.

  benchmark/tools/summarize-benchmarks.py run1.json [run2.json ...]

Startup: min / median / max of timeToInitialDisplayMs and timeToFullDisplayMs per test. Frames: frameCount (median) and
frameDurationCpuMs / frameOverrunMs P50 / P90 / P99 per test. With several files each is one run; columns are labelled by file name."""
import json
import sys
from pathlib import Path

runs = [(Path(a).stem, json.loads(Path(a).read_text())) for a in sys.argv[1:]]
names = sorted({b["name"] for _, d in runs for b in d["benchmarks"]})


def find(data, name):
    return next((b for b in data["benchmarks"] if b["name"] == name), None)


print("### Startup (ms, min / median / max)\n")
print("| Test | Run | timeToInitialDisplay | timeToFullDisplay |")
print("|---|---|---|---|")
for n in names:
    for label, d in runs:
        b = find(d, n)
        if b and "timeToInitialDisplayMs" in b["metrics"]:
            m = b["metrics"]
            full = m.get("timeToFullDisplayMs")
            f = lambda x: f"{x['minimum']:.0f} / {x['median']:.0f} / {x['maximum']:.0f}"
            print(f"| {n} | {label} | {f(m['timeToInitialDisplayMs'])} | {f(full) if full else 'n/a'} |")
print("\n### Frame timing (ms, P50 / P90 / P99)\n")
print("| Test | Run | frames (median) | frameDurationCpuMs | frameOverrunMs |")
print("|---|---|---|---|---|")
for n in names:
    for label, d in runs:
        b = find(d, n)
        if b and "frameDurationCpuMs" in b.get("sampledMetrics", {}):
            s = b["sampledMetrics"]
            g = lambda k: f"{s[k]['P50']:.2f} / {s[k]['P90']:.2f} / {s[k]['P99']:.2f}"
            print(f"| {n} | {label} | {b['metrics']['frameCount']['median']:.0f} | {g('frameDurationCpuMs')} | {g('frameOverrunMs')} |")
