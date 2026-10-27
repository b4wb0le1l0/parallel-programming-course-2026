#!/usr/bin/env python3
"""Compile and run the required experiments sequentially, keeping their raw output."""
from datetime import datetime
from pathlib import Path
import json
import os
import re
import subprocess
import time

ROOT = Path(__file__).resolve().parent
java_home = os.environ.get("JAVA_HOME")
JAVA = str(Path(java_home) / "bin/java") if java_home else "java"
JAVAC = str(Path(java_home) / "bin/javac") if java_home else "javac"
RESULTS = ROOT.parent / "results" / datetime.now().strftime("%Y%m%d-%H%M%S")
RESULTS.mkdir(parents=True)
records = []

def run(label, main, *args, expect_error=False):
    command = [JAVA, "-Duser.language=en", "-Duser.country=US", "-cp", str(ROOT / "out"),
               "lab2." + main, *map(str, args)]
    started = time.monotonic()
    result = subprocess.run(command, text=True, capture_output=True, timeout=1200)
    elapsed = time.monotonic() - started
    text = result.stdout + result.stderr
    (RESULTS / (label + ".txt")).write_text(text)
    records.append(dict(label=label, command=command, seconds=elapsed,
                        returncode=result.returncode, output=text))
    (RESULTS / "runs.json").write_text(json.dumps(records, indent=2))
    print(label + ": " + text.strip().replace("\n", " | "), flush=True)
    if (result.returncode != 0) != expect_error:
        raise RuntimeError("Unexpected exit status: " + label)
    return text

subprocess.run([JAVAC, "--release", "21", "-d", str(ROOT / "out"),
                *map(str, sorted((ROOT / "src/lab2").glob("*.java")))], check=True)
run("checks", "SchedulerCheck")
for version, window in [("v1", "none"), ("v1", "yield"), ("v2", "none")]:
    for repeat in range(1, 4):
        run(f"stress-{version}-{window}-{repeat}", "Stress", 10000, window, version)
for target in ["withdraw-v0", "withdraw-v1", "transfer"]:
    run(target + "-first", "Main", target, "first", "log")
run("transfer-deadlock", "Main", "transfer", "replay", "AB", "log")
run("invalid-replay", "Main", "withdraw-v1", "replay", "AABB", expect_error=True)
random = run("randoms", "Main", "withdraw-v1", "randoms", 1000)
trace = re.search(r"first failing seed \d+: ([A-Z]+)", random).group(1)
run("random-replay", "Main", "withdraw-v1", "replay", trace, "log")

def explore(target, strategy, *args):
    label = target + "-" + strategy + "".join("-" + str(arg) for arg in args)
    text = run(label, "Main", target, strategy, *args)
    match = re.search(r"first failing: ([A-Z]+)", text)
    if match:
        run(label + "-replay", "Main", target, "replay", match.group(1), "log")

for target in ["withdraw-v1", "withdraw-v2", "withdraw-v0", "transfer"]:
    explore(target, "dfs")
for target, bounds in [("withdraw-v1", range(3)), ("transfer", range(2))]:
    for bound in bounds:
        explore(target, "bounded", bound)
for variant in ["nostep3", "plain", "acqrel", "ok"]:
    explore("collector-" + variant + "-1x1", "dfs")
for count in [2, 3, 4]:
    explore("collector-nostep3-1x" + str(count), "dfs")
for bound in range(4):
    explore("collector-nostep3-2x1", "bounded", bound)
run("checkpoint", "Main", "collector-ok-2x1", "bounded", 2)
for mode in ["plain", "acqrel", "volatile", "naive"]:
    for repeat in range(1, 4):
        rounds, cells = (200000, 1) if mode == "naive" else (2000, 10000)
        run(f"memory-{mode}-{repeat}", "MiniStress", mode, rounds, cells)
run("batch-small", "MiniStress", "acqrel", 200000, 100)
run("batch-large", "MiniStress", "acqrel", 200, 100000)
print("Results: " + str(RESULTS), flush=True)
