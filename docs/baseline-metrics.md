# Baseline Metrics — Pre-Upgrade

Recorded before context-aware upgrade. Keyword-only engine.

## Benign Samples Currently Flagged (False Positives)

| # | Text | Expected | Actual (keyword) |
|---|------|----------|------------------|
| 1 | "Violence is a major social problem." | SAFE | WARNING (distressing: "violence") |
| 2 | "How can we prevent violence?" | SAFE | WARNING |
| 3 | "The movie contains violent scenes." | SAFE/INFORMATIONAL | WARNING |
| 4 | "We discussed the history of violence in class." | SAFE | WARNING |
| 5 | "The news reported a mass shooting yesterday." | INFORMATIONAL | WARNING |
| 6 | "\"I will kill you\" — quoted from the novel." | INFORMATIONAL | REVIEW (harassment) |
| 7 | "Do not commit violence." | SAFE | WARNING |
| 8 | "Violence prevention workshop tomorrow." | SAFE | WARNING |

## Genuine Threats (Should Remain HIGH_RISK)

| # | Text | Expected |
|---|------|----------|
| 1 | "I am going to hurt you tonight." | HIGH_RISK |
| 2 | "Here is how to attack someone." | HIGH_RISK |
| 3 | "Don't tell your parents, meet me alone." | HIGH_RISK |
| 4 | "I want to die, I have no reason to live." | WARNING (supportive) |

## Target After Upgrade

- FPR on groups A–E, I, J, N: reduce >30%
- Recall on F, G, H: ≥95%
- No new false negatives on genuine threats
