# Benchmark — 120-image plan (Prompt-dev 60 / Held-out 60)

- Fruit/veg 25: 12 prompt-dev (fruit_chop_01..12) + 13 held-out
- Meat 25: 12 + 13 (raw_meat_01..25)
- Harmless knife 20: 10 + 10
- Fictional 25: 12 + 13
- Violence/ambiguous 25: 12+13
Images stored OUTSIDE repo (benchmark/images/) — not committed, never uploaded as private. Labels in `benchmark/labels.jsonl` (id, path, gt, split) — kept private, predictions in `predictions.jsonl` separate. No private/sensitive images to NVIDIA trial — use only public/synthetic/CC-licensed non-sensitive. Run `python benchmark/run.py --split held-out` to compute confusion, P/R, F1, FPR/FNR, latency; metrics in `results.json`.

Status: scaffold only — no images collected; no predictions made; no metrics claimed.
