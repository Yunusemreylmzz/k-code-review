# K Code Review — Task Board

## Epic

IntelliJ IDEA extension: AI (Gemini) reviews Git commit code, classifies findings like SonarQube, and provides How to fix guidance.

## Tasks

### P0 — Foundation
- [x] T1 Choose stack: **Kotlin + IntelliJ Platform Gradle Plugin**
- [x] T2 Gradle scaffold (`build.gradle.kts`, `plugin.xml`, wrapper)
- [x] T3 Domain model: `Severity`, `FindingCategory`, `Finding`, `ReviewResult`, `CommitSnapshot`

### P0 — Core pipeline
- [x] T4 Git commit loader (name-status + file content at revision)
- [x] T5 Gemini client (`generateContent`, JSON mime type)
- [x] T6 Prompt builder + default Sonar-style system prompt
- [x] T7 Review JSON parser (fence-tolerant) + severity sort
- [x] T8 `CodeReviewService` orchestration (per-file review)

### P0 — UX
- [x] T9 Settings: PasswordSafe API key, model, custom prompt, limits
- [x] T10 Tool window: severity cards, findings table, How to fix
- [x] T11 Actions: Review Latest / Review Selected / Settings
- [x] T11b Pre-commit gate: staged review + block on BLOCKER/CRITICAL

### P1 — Quality (self-test / prod-ready)
- [x] T12 Unit tests: parser, prompt, severity aggregation, Gemini extract
- [x] T13 `./gradlew test` green
- [x] T14 `./gradlew buildPlugin` produces distributable zip
- [ ] T15 Manual sandbox check via `runIde`
- [x] T16 Self-review: no secrets in repo, PasswordSafe, progress tasks, ActionUpdateThread

### P2 — Next iterations
- [ ] T17 Jump-to-line gutter / editor markers for findings
- [ ] T18 Cache last N review results per commit
- [ ] T19 Parallel file reviews with rate-limit controls
- [ ] T20 Export report (Markdown / SARIF)
- [ ] T21 Quality gate threshold (fail if BLOCKER/CRITICAL > 0)

## Acceptance criteria (MVP)

1. User configures Gemini key once in Settings.
2. User can review latest commit from Tools menu.
3. Each finding has severity, category, message, howToFix.
4. Findings are ordered by priority.
5. UI shows Sonar-like severity counters.
6. No API key in VCS.
