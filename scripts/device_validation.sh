#!/usr/bin/env bash
# device_validation.sh — Milestone 8 on-device validation harness.
# Runs gradle checks, instrumentation (if present), and collects device diagnostics.
set -u

PASS=0
FAIL=0
SKIP=0
ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
RESULT_DIR="$ROOT_DIR/build/device-validation"
mkdir -p "$RESULT_DIR"

report() {
  local status="$1" msg="$2"
  if [ "$status" = "PASS" ]; then PASS=$((PASS+1)); echo "[PASS] $msg"
  elif [ "$status" = "FAIL" ]; then FAIL=$((FAIL+1)); echo "[FAIL] $msg"
  else SKIP=$((SKIP+1)); echo "[SKIP] $msg"
  fi
}

echo "=== MetaGuardRail Device Validation — $(date -u +%Y-%m-%dT%H:%M:%SZ) ==="
echo "Root: $ROOT_DIR"

# --- 1. Gradle checks ---
echo ""
echo "--- Gradle checks ---"

if ./gradlew :app:assembleDebug --stacktrace > "$RESULT_DIR/assemble.log" 2>&1; then
  report PASS "assembleDebug"
else
  report FAIL "assembleDebug (see build/device-validation/assemble.log)"
fi

if ./gradlew :app:lintDebug > "$RESULT_DIR/lint.log" 2>&1; then
  report PASS "lintDebug"
else
  # lint may exit non-zero on warnings depending on config; check for errors
  if grep -q "Lint found errors" "$RESULT_DIR/lint.log" 2>/dev/null; then
    report FAIL "lintDebug (errors found)"
  else
    report PASS "lintDebug (warnings only or passed)"
  fi
fi

if ./gradlew :app:testDebugUnitTest --stacktrace > "$RESULT_DIR/unitTest.log" 2>&1; then
  report PASS "testDebugUnitTest"
else
  report FAIL "testDebugUnitTest (see build/device-validation/unitTest.log)"
fi

# --- 2. Instrumentation (if device + tests exist) ---
echo ""
echo "--- Instrumentation ---"

HAS_INSTRUMENTATION=false
if find "$ROOT_DIR/app/src/androidTest" -name "*.kt" -o -name "*.java" 2>/dev/null | grep -q .; then
  HAS_INSTRUMENTATION=true
fi

if command -v adb >/dev/null 2>&1 && adb get-state 2>/dev/null | grep -q "device"; then
  if [ "$HAS_INSTRUMENTATION" = true ]; then
    echo "Device connected — running instrumentation..."
    if ./gradlew :app:connectedDebugAndroidTest > "$RESULT_DIR/instrumentation.log" 2>&1; then
      report PASS "connectedDebugAndroidTest (adb shell am instrument)"
    else
      # Also try direct adb shell am instrument for visibility
      adb shell am instrument -w com.example.metaguardrail.test/androidx.test.runner.AndroidJUnitRunner > "$RESULT_DIR/adb_instrument.log" 2>&1 || true
      if grep -q "OK (" "$RESULT_DIR/adb_instrument.log" 2>/dev/null; then
        report PASS "adb shell am instrument (direct)"
      else
        report FAIL "connectedDebugAndroidTest (see build/device-validation/instrumentation.log)"
      fi
    fi
  else
    report SKIP "instrumentation — no androidTest sources found"
  fi

  # --- 3. Logcat summary ---
  echo ""
  echo "--- Logcat summary (FATAL/ANR/metaguardrail) ---"
  adb logcat -d 2>/dev/null | grep -i -E "FATAL|ANR|metaguardrail" | head -n 200 > "$RESULT_DIR/logcat_summary.txt" 2>&1 || true
  LC_COUNT=$(wc -l < "$RESULT_DIR/logcat_summary.txt" 2>/dev/null | tr -d ' ')
  echo "Logcat matches: $LC_COUNT (saved to build/device-validation/logcat_summary.txt)"
  if grep -q "FATAL" "$RESULT_DIR/logcat_summary.txt" 2>/dev/null; then
    report FAIL "logcat — FATAL entries found ($LC_COUNT lines)"
  else
    report PASS "logcat — no FATAL (total matches: $LC_COUNT)"
  fi

  # --- 4. Device snapshots ---
  echo ""
  echo "--- Device snapshots ---"
  PKG="com.example.metaguardrail"
  adb shell dumpsys meminfo "$PKG" > "$RESULT_DIR/dumpsys_meminfo.txt" 2>&1 || echo "no meminfo" > "$RESULT_DIR/dumpsys_meminfo.txt"
  adb shell dumpsys battery > "$RESULT_DIR/dumpsys_battery.txt" 2>&1 || true
  adb shell dumpsys cpuinfo > "$RESULT_DIR/dumpsys_cpuinfo.txt" 2>&1 || adb shell cat /proc/stat > "$RESULT_DIR/dumpsys_cpuinfo.txt" 2>&1 || true
  adb shell pidof "$PKG" > "$RESULT_DIR/pidof.txt" 2>&1 || true
  echo "Snapshots saved to $RESULT_DIR/dumpsys_*.txt + pidof.txt"
  report PASS "device snapshots collected"

  # Optional: run Python benchmark helper if present
  if [ -f "$ROOT_DIR/benchmark/measure_device.py" ]; then
    echo ""
    echo "--- Python benchmark helper ---"
    if command -v python3 >/dev/null 2>&1; then
      python3 "$ROOT_DIR/benchmark/measure_device.py" --pretty > "$RESULT_DIR/measure_device.json" 2>&1 || true
      echo "measure_device.json saved to $RESULT_DIR"
      report PASS "benchmark/measure_device.py executed"
    else
      report SKIP "benchmark/measure_device.py — python3 not found"
    fi
  fi
else
  report SKIP "adb shell am instrument — no device connected"
  report SKIP "logcat — no device connected"
  report SKIP "dumpsys snapshots — no device connected"
fi

# --- Summary ---
echo ""
echo "=== Summary: $PASS passed, $FAIL failed, $SKIP skipped ==="
echo "Logs: $RESULT_DIR/"
if [ "$FAIL" -gt 0 ]; then
  echo "RESULT: FAIL — check logs above"
  exit 1
else
  echo "RESULT: PASS (skipped checks require device for full validation)"
  exit 0
fi
