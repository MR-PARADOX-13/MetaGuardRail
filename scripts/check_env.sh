#!/bin/bash
# Phase 2 — Environment checks (no firewall auto-change, no key print)
echo "Java: $(java -version 2>&1 | head -1)"
echo "Gradle: $(./gradlew --version 2>&1 | grep Gradle | head -1)"
echo "Node: $(node -v 2>&1) npm: $(npm -v 2>&1)"
echo "ADB: $(adb devices 2>&1 | head -5)"
if [ -z "$NVIDIA_API_KEY" ]; then echo "NVIDIA_API_KEY: MISSING (set env to enable LG4)"; else echo "NVIDIA_API_KEY: set (not printed)"; fi
if curl -sf http://localhost:8787/health >/dev/null 2>&1; then echo "Proxy: reachable on localhost:8787"; else echo "Proxy: not reachable (start: cd local-proxy && npm start)"; fi
echo "Backend: $(grep backendBaseUrl app/build.gradle.kts | head -1)"
ls -1 benchmark/images/* 2>&1 | head -20; echo "If missing, follow benchmark/README.md (120 public/CC images, no scraping)"
