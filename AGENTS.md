# Agent Guidelines for IntelliJ GH Stack Plugin

## Project Overview

This is an IntelliJ plugin (Kotlin, IntelliJ Platform, Gradle) that wraps the GitHub CLI [`gh stack`](https://github.com/github.com/gh-stack) extension so developers can view and navigate stacked PRs without leaving the IDE.

### Key Principles

- **DO NOT reimplement stacked pull requests** — the plugin talks to `gh` in the selected Git repository
- Use the default settings/options from the `gh stack` CLI
- Provide additional features and customization that can be changed from plugin settings
- The plugin can work with the project's Git repository by default, or with a Git submodule chosen in the panel

## Tech Stack

- **Language:** Kotlin
- **Platform:** IntelliJ Platform
- **Build System:** Gradle
- **Testing Framework:** JUnit 4

## Development Workflow

### Before Making Changes

- Read this file and `README.md` to understand the project context
- Understand the `gh stack` CLI behavior being wrapped
- Review existing code to understand current state. The codebase was written by several different LLMs and has **no single consistent style**, so existing code is *not* an authoritative style reference — see [Code Style & Consistency](#code-style--consistency)

### Implementation Requirements

- Follow Kotlin best practices and IntelliJ Platform API conventions
- Follow the rules in [Code Style & Consistency](#code-style--consistency) for all code you write or touch
- Add or update JUnit 4 tests for any behavior you change
- Record user-visible changes under `## [Unreleased]` in `CHANGELOG.md`
- Run `./gradlew test` and fix any test failures you introduce

### Testing

Run the test suite to verify your changes:
```bash
./gradlew test
```

Start a sandbox IDE with the plugin installed for manual testing:
```bash
./gradlew runIde
```

Build the plugin for distribution:
```bash
./gradlew buildPlugin
```

## Example Supported Features

The plugin wraps these `gh stack` commands:
- Visual view of the current stack (`gh stack view --json`)
- Add a branch on top of the stack (`gh stack add`)
- Check out a layer (`gh stack checkout <branch>`)
- Rebase the stack (`gh stack rebase`, with `--upstack`, `--downstack`, `--no-trunk`)
- Switch among locally tracked stacks from the **All stacks** column
- Prune dangling local stacks
- Switch between project repository and Git submodules

## GitHub Issue Handling

When implementing features or fixes from GitHub issues:
- Implement the complete feature or fix described in the issue
- If the issue is a question or has nothing to implement, make no code changes and clearly state this in your summary
- Ensure all tests pass before considering the implementation complete

## Code Quality

- Write clear, readable code with appropriate documentation
- Maintain test coverage for new functionality

## Code Style & Consistency

The existing code mixes patterns from multiple LLMs, so agents are expected to bring good judgement here.

### Reference standards (in priority order)

1. [Kotlin Coding Conventions](https://kotlinlang.org/docs/coding-conventions.html)
2. [IntelliJ Platform SDK / Plugin Developer Guide](https://plugins.jetbrains.com/docs/intellij/welcome.html) (threading, services, disposables, actions, UI)
3. Patterns already used in the file/module you are editing, *only if* they do not conflict with 1 and 2

### Rules

- **Scope:** Apply improved patterns to code you write or modify. Do NOT rewrite unrelated code or refactor the whole codebase as part of a feature/fix. If you notice a larger inconsistency, mention it in your summary instead of fixing it.
- **Small cleanups are welcome** in the files you are already touching, as long as they are low-risk and covered by tests.
- **New code:** prefer idiomatic Kotlin (immutable `val`, data classes, null-safety instead of `!!`, expression bodies, scope functions only where they aid readability).
- **IntelliJ specifics:** never block the EDT with `gh`/Git process calls (use background tasks/coroutines); register disposables properly; use services instead of global singletons.
- **Be consistent within a file:** do not mix two styles in the same file; if you introduce a better pattern, apply it to the code you touch there.
- **Suggestions:** if you see a clearly better convention (formatting, architecture, naming), propose it in your summary or as a follow-up issue and, if adopted by the maintainer, add it to this file.
