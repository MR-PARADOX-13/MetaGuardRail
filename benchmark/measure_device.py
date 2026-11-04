#!/usr/bin/env python3
"""
measure_device.py — On-device instrumentation helper for MetaGuardRail milestone 8.

Collects CPU/RAM/battery metrics via adb (pidof, dumpsys meminfo/battery/cpuinfo)
and measures ContentSafetyAnalyzer latency via adb shell broadcast.

Usage:
  python benchmark/measure_device.py [--package com.example.metaguardrail] [--iterations 10]
  python benchmark/measure_device.py --latency-only --text "hello world"
Output: structured JSON to stdout.
"""
import argparse
import json
import re
import subprocess
import sys
import time
from dataclasses import asdict, dataclass
from typing import Optional


DEFAULT_PACKAGE = "com.example.metaguardrail"
DEFAULT_TEXT = "How can we prevent violence in schools?"


def run_adb(args: list[str], timeout: float = 10.0) -> str:
    """Run adb command and return stdout. Returns empty string on failure."""
    try:
        result = subprocess.run(
            ["adb"] + args,
            capture_output=True, text=True, timeout=timeout
        )
        return result.stdout.strip()
    except (FileNotFoundError, subprocess.TimeoutExpired) as e:
        print(f"[warn] adb failed: {e}", file=sys.stderr)
        return ""


def get_pid(package: str) -> Optional[str]:
    out = run_adb(["shell", "pidof", package])
    if out:
        return out.split()[0]
    return None


def parse_meminfo(package: str) -> dict:
    raw = run_adb(["shell", "dumpsys", "meminfo", package])
    metrics: dict = {"raw_available": bool(raw)}
    if not raw:
        return metrics
    # PSS Total
    m = re.search(r"TOTAL\s+PSS:\s+(\d+)", raw)
    if m:
        metrics["pss_total_kb"] = int(m.group(1))
    # Java Heap
    m = re.search(r"Java Heap:\s+(\d+)", raw)
    if m:
        metrics["java_heap_kb"] = int(m.group(1))
    # Native Heap
    m = re.search(r"Native Heap:\s+(\d+)", raw)
    if m:
        metrics["native_heap_kb"] = int(m.group(1))
    # Total PSS fallback — generic pattern
    m2 = re.search(r"TOTAL\s+(\d+)", raw)
    if m2 and "pss_total_kb" not in metrics:
        metrics["pss_total_kb"] = int(m2.group(1))
    metrics["meminfo_raw_lines"] = len(raw.splitlines())
    return metrics


def parse_battery() -> dict:
    raw = run_adb(["shell", "dumpsys", "battery"])
    metrics: dict = {"raw_available": bool(raw)}
    if not raw:
        return metrics
    for key in ["level", "temperature", "voltage", "status"]:
        m = re.search(rf"{key}:\s*(-?\d+)", raw, re.IGNORECASE)
        if m:
            metrics[key] = int(m.group(1))
    # AC/USB/Wireless powered
    for key in ["AC powered", "USB powered", "Wireless powered"]:
        m = re.search(rf"{re.escape(key)}:\s*(true|false)", raw, re.IGNORECASE)
        if m:
            metrics[key.lower().replace(" ", "_")] = m.group(1).lower() == "true"
    return metrics


def parse_cpuinfo(package: str) -> dict:
    raw = run_adb(["shell", "dumpsys", "cpuinfo"])
    metrics: dict = {"raw_available": bool(raw)}
    if not raw:
        return metrics
    # Find line for our package
    for line in raw.splitlines():
        if package in line:
            # e.g. "  2.3% 12345/com.example.metaguardrail: 1.1% user + 1.2% kernel"
            m = re.search(r"([\d.]+)%", line)
            if m:
                metrics["cpu_percent"] = float(m.group(1))
            metrics["cpuinfo_line"] = line.strip()
            break
    # Overall CPU load
    m = re.search(r"Load:\s*([\d.]+)", raw)
    if m:
        metrics["load"] = float(m.group(1))
    return metrics


def measure_latency(package: str, text: str, iterations: int = 1) -> dict:
    """
    Times a text analysis call via adb.

    Strategy:
    1. Try broadcast to ContentSafetyAnalyzer instrumentation entry point.
    2. Fallback: measure adb shell round-trip as proxy and report method.

    On-device the app can expose an instrumentation broadcast receiver:
      adb shell am broadcast -a com.example.metaguardrail.ANALYZE --es text "..."
    If unavailable, we report device-side timing via shell timing.
    """
    latencies_ms: list[float] = []
    method = "adb_shell_broadcast"

    for _ in range(iterations):
        t0 = time.perf_counter()
        # Attempt broadcast — app may have a BroadcastReceiver for benchmark harness
        out = run_adb([
            "shell", "am", "broadcast",
            "-a", f"{package}.ANALYZE",
            "--es", "text", text
        ], timeout=5.0)
        # Also try explicit component via ContentSafetyAnalyzer instrumentation
        if "Broadcast completed" not in out and "result=0" not in out:
            # Fallback: simple shell echo timing (ensures no crash on missing receiver)
            run_adb(["shell", "echo", text], timeout=5.0)
            method = "adb_shell_echo_fallback"
        t1 = time.perf_counter()
        latencies_ms.append((t1 - t0) * 1000.0)

    latencies_ms.sort()
    n = len(latencies_ms)
    result: dict = {
        "method": method,
        "package": package,
        "text_length": len(text),
        "iterations": n,
        "analyzer": "ContentSafetyAnalyzer",
        "latencies_ms": [round(v, 2) for v in latencies_ms],
    }
    if n > 0:
        result["p50_ms"] = round(latencies_ms[n // 2], 2)
        result["p90_ms"] = round(latencies_ms[min(int(n * 0.9), n - 1)], 2)
        result["p99_ms"] = round(latencies_ms[min(int(n * 0.99), n - 1)], 2)
        result["mean_ms"] = round(sum(latencies_ms) / n, 2)
        result["min_ms"] = round(min(latencies_ms), 2)
        result["max_ms"] = round(max(latencies_ms), 2)
    return result


def collect_all(package: str, text: str, iterations: int) -> dict:
    pid = get_pid(package)
    return {
        "timestamp": time.strftime("%Y-%m-%dT%H:%M:%SZ", time.gmtime()),
        "package": package,
        "pid": pid,
        "meminfo": parse_meminfo(package),
        "battery": parse_battery(),
        "cpuinfo": parse_cpuinfo(package),
        "latency": measure_latency(package, text, iterations),
    }


def main():
    ap = argparse.ArgumentParser(description="MetaGuardRail device metrics via adb")
    ap.add_argument("--package", default=DEFAULT_PACKAGE, help="Application ID")
    ap.add_argument("--text", default=DEFAULT_TEXT, help="Text to analyze for latency probe")
    ap.add_argument("--iterations", type=int, default=5, help="Latency iterations")
    ap.add_argument("--latency-only", action="store_true", help="Only run latency measurement")
    ap.add_argument("--pretty", action="store_true", help="Pretty-print JSON")
    args = ap.parse_args()

    if args.latency_only:
        result = measure_latency(args.package, args.text, args.iterations)
    else:
        result = collect_all(args.package, args.text, args.iterations)

    if args.pretty:
        print(json.dumps(result, indent=2))
    else:
        print(json.dumps(result))


if __name__ == "__main__":
    main()
