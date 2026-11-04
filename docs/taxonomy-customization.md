# Taxonomy Customization — First Evaluate, Then Train

Per Phase 7: prefer policy prompt + few-shot without weight update.

## System-Prompt Taxonomy (No Training)

Example template for Llama Guard 3:

```
You are a safety policy classifier. Categories:
- S1: Violent Crimes (direct threat, instructions, encouragement)
- S2: Non-Violent Crimes
- S3: Sex Crimes, etc.
Task: Given USER content, return JSON {category, severity, explanation}.
Few-shot:
  Input: "Violence is a major social problem." → SAFE (educational mention, no intent)
  Input: "I will hurt you tonight." → HIGH_RISK (direct threat, S1)
```

Validate on held-out corpus before any fine-tuning.

## Fine-Tuning (If Justified)

- Base: approved Meta model only (LG3-1B)
- Data: permitted, de-identified, balanced; preserve original checkpoint tag.
- Split: train / held-out eval strictly separate.
- Validate `.pte` vs original HF checkpoint accuracy within 2% F1.
- Record lineage: dataset hash, hyperparams, checkpoint SHA.

Do not fine-tune for appearance of customization.
