# Dataset Manifest — Pending Acquisition

## DeepGHS nsfw_detect (HuggingFace deepghs/nsfw_detect)
- License: repository MIT **but image rights not established** — card lists sensitive content, no provenance. **Rights unclear → DO NOT train on images until author confirms redistribution/training rights.**
- Size: 10K–100K images, 5 tags (art/nsfw). No class balance proven.
- Status: **NOT ACQUIRED** — awaiting explicit rights confirmation from maintainer.

## Swiss AI Center NSFW Detector v2.2
- Dataset: 38,500 images (16,500 safe, 22,000 nsfw) 5,500/category; MobileNetV2 95.7% safe/ns, 85–86% subcats.
- Access: research-only request via Swiss AI Center — email sent template in `requests/` (pending response).
- Status: **REQUESTED, NOT GRANTED**

## FGO (PUCPR SecPLab)
- 476k frames (211k obscene, 239k non-obscene, 26k obscene-context) from 1,451 adult videos + UCF101; 10 FPS hash-dedup.
- Access: academic request to all authors, subject `Request for Access – FGO Dataset` — template ready, pending response. Research-only, non-commercial.
- Status: **REQUESTED, NOT GRANTED**

## Normalized Label Plan (Upon Approval)
- Raw → SAFE (neutral, cartoon_neutral, non-obscene), SUGGESTIVE (suggestive, sexy <0.6), EXPLICIT (porn, obscene), HENTAI (hentai, cartoon_sex), REVIEW (borderline 0.4–0.6)
- Hold-out 20% per source, dedup via pHash, integrity SHA256 per file.

## Balancing
Current: Swimwear/fitness under-represented; medical/artistic none. Requires targeted CC0/Copyright-cleared supplement (not scraped).
