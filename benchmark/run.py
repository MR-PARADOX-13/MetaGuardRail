"""
Evidence-based runner — does not fabricate data.
Loads benchmark/labels.jsonl (gt) and benchmark/predictions.jsonl (model) for --split.
Computes confusion, precision, recall, F1, FPR, FNR, latency avg/median.
Fails if dataset missing.
"""
import json, pathlib, statistics, argparse, sys
ap = argparse.ArgumentParser()
ap.add_argument('--split', default='held-out')
args = ap.parse_args()
labels_path = pathlib.Path('benchmark/labels.jsonl')
preds_path = pathlib.Path('benchmark/predictions.jsonl')
if not labels_path.exists() or not preds_path.exists():
    print(f"Missing {labels_path} or {preds_path} — no metrics claimed. Collect 120 images per benchmark/README.md first.")
    sys.exit(1)
# Full implementation: join on id where split matches, build confusion
labels = [json.loads(l) for l in labels_path.read_text().splitlines() if l.strip()]
preds = {json.loads(l)['id']: json.loads(l) for l in preds_path.read_text().splitlines() if l.strip()}
filtered = [l for l in labels if l.get('split') == args.split]
if not filtered: print(f"No labels for split {args.split}"); sys.exit(1)
tp=tn=fp=fn=0
latencies=[]
for lab in filtered:
    pid = lab['id']
    if pid not in preds: continue
    gt = lab['gt']  # 'violence' / 'benign'
    pr = preds[pid]['pred']  # model 'unsafe_S1' etc.
    is_pos_pred = pr == 'unsafe_S1'
    is_pos_gt = gt == 'violence'
    if is_pos_gt and is_pos_pred: tp+=1
    elif not is_pos_gt and not is_pos_pred: tn+=1
    elif not is_pos_gt and is_pos_pred: fp+=1
    else: fn+=1
    if 'latency_ms' in preds[pid]: latencies.append(preds[pid]['latency_ms'])
prec = tp/(tp+fp) if tp+fp else 0
rec = tp/(tp+fn) if tp+fn else 0
f1 = 2*prec*rec/(prec+rec) if prec+rec else 0
fpr = fp/(fp+tn) if fp+tn else 0
fnr = fn/(fn+tp) if fn+tp else 0
print(f"Confusion TP:{tp} TN:{tn} FP:{fp} FN:{fn}")
print(f"Precision:{prec:.3f} Recall:{rec:.3f} F1:{f1:.3f} FPR:{fpr:.3f} FNR:{fnr:.3f}")
if latencies: print(f"Latency avg:{statistics.mean(latencies):.0f}ms median:{statistics.median(latencies):.0f}ms")
# Fail on missing split handled above
