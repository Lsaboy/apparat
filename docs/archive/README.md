# Archive: plans, audits and change logs (up to v1.1.0)

Kept for reference if problems turn up in the release. Nothing here is current: the live
description of the app and its roadmap is `docs/PROJECT_GUIDE.md`.

Old names appear throughout: the app was called "Digital Pocket Knife", the package was
`com.omniforge.app` and launcher shortcuts used `dpk://`. Since v1.1.0 they are "Apparat",
`com.omniforge.apparat` and `apparat://`.

| File | What it is |
|---|---|
| `FIX_PLAN.md` | The original ordered fix plan (Phase 1 bugs → PDF → everything else), written 2026-10-16. |
| `AUDIT_2026-10-17.md` | Claude's code audit (B#/T# issues), with the status table updated as each fix landed. Most complete record of what was fixed, when, and in which commit. |
| `SCREEN_AUDIT_2026-10-17.md` | Gemini's per-screen audit (S#/TR# issues). |
| `CLAUDE_HANDOVER.md` | Gemini's handover to Claude after Batches 1–11 (historical). |
| `GEMINI_CHANGES.md` | Gemini's detailed change log for Batches 1–11 and its audit fixes. |

Git history has every change; `git log --oneline` from `dd093c3` back covers this period.
