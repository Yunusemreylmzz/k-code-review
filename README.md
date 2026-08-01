# K Code Review

IntelliJ IDEA plugin that reviews **Git commit** changes with **Gemini AI** and presents **SonarQube-style** findings: severity, category, explanation, and **How to fix**.

## Why Kotlin

Kotlin is the best fit for this plugin:

- Official JetBrains / IntelliJ Platform language
- First-class support in the IntelliJ Plugin Template
- Safer null handling for Git + AI response parsing
- Concise Swing / service code without Java boilerplate

## Features

- Review latest commit or pick a recent commit
- Per-file Gemini analysis with your custom prompt
- Priority classes: `BLOCKER` → `CRITICAL` → `MAJOR` → `MINOR` → `INFO`
- Categories: Bug, Vulnerability, Security Hotspot, Code Smell, Performance, Maintainability
- Tool window dashboard with severity metrics + finding table + How to fix panel
- API key stored in IntelliJ **PasswordSafe** (never committed)

## Pre-commit gate

When you click **Commit**, K Code Review:

1. Reads **staged** files (`git diff --cached`)
2. Reviews them with Gemini
3. **Blocks once** if there is **any** finding (BLOCKER → INFO)
4. Opens the **K Code Review** tool window with details / How to fix
5. Click **Commit again** (same staged changes) to proceed anyway

If you change staged content after a block, the first-click block applies again.

Toggle in **Settings → Tools → K Code Review → Pre-commit review**.

## Setup

1. Open this project in IntelliJ IDEA (with Plugin DevKit / Gradle).
2. Run Gradle task `runIde` to launch a sandbox IDE with the plugin.
3. In the sandbox IDE: **Settings → Tools → K Code Review**
4. Paste your Gemini API key (PasswordSafe) and optionally customize the prompt.
5. Open a Git project → **Tools → K Code Review → Review Latest Commit**
6. Inspect results in the **K Code Review** tool window (bottom).

### Build / test

```bash
./gradlew test
./gradlew buildPlugin
./gradlew runIde
```

## Security note

Do **not** put API keys in source, `gradle.properties`, or git. Configure the key only in IDE settings.

If a key was pasted into chat or a ticket, **rotate it** in Google AI Studio and store the new one in PasswordSafe.

## Architecture

```
Commit (Git4Idea)
   → ChangedFile[] 
   → PromptBuilder (system + per-file user prompt)
   → GeminiClient
   → ReviewParser (JSON → Finding[])
   → ReviewResult (sorted by severity)
   → Tool Window UI
```

## Default prompt

Shipped at `src/main/resources/prompts/default-review-prompt.txt`. Override in Settings.

## License

MIT
