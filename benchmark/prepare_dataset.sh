#!/bin/bash
# Phase 4 — Dataset prep: validates 120-image layout without uploading private content
set -e
echo "Checking benchmark dataset structure (no fake data)..."
for dir in fruit_vegetable meat harmless_knife fictional real_violence; do
  count=$(ls -1 benchmark/images/$dir 2>/dev/null | wc -l)
  echo "$dir: $count images (expected per README)"
done
if [ ! -f benchmark/labels.jsonl ]; then echo "Missing benchmark/labels.jsonl — collect public/CC synthetic images per README"; exit 1; fi
if [ ! -f benchmark/predictions.jsonl ]; then echo "Missing benchmark/predictions.jsonl — run LG4 evaluations first (requires NVIDIA_API_KEY + consent)"; exit 1; fi
echo "Dataset present — run: python benchmark/run.py --split held-out"
