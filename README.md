# GH Stack for IntelliJ

IntelliJ plugin that wraps the GitHub CLI [`gh stack`](https://github.com/github/gh-stack) extension so you can view and navigate stacked PRs without leaving the IDE.

Your support will help me keep this project going. Thank you!

[![Buy me a coffee](https://img.shields.io/badge/buy%20me%20a%20coffee-ffdd00?style=for-the-badge&logo=buy-me-a-coffee&logoColor=black)](https://payments.cashfree.com/forms/intellij-gh-stack)

## Motivation

I use `gh stack` heavily every day, across multiple repositories in my organization, and I genuinely felt the need for IDE support. Stacked PR workflows involve a lot of context switching between the terminal and the editor, and I wanted that to be seamless.

Since neither the GitHub team nor JetBrains seems likely to ship something like this in the short term, I decided to build it myself. I use this plugin actively on a daily basis, so it's built around real, everyday workflows rather than hypothetical ones.

## How I built it?

This repository makes heavy use of AI assistance to build features. I'm also still new to Kotlin, so I rely on AI to move faster and learn the language and the IntelliJ Platform APIs as I go.

I review and test what goes in, and the plugin is used daily for real work, but the code may not always be idiomatic Kotlin yet. If you spot something that could be cleaner, issues and PRs are very welcome. See the [Motivation](#motivation) section for why I'm building this in the first place.

## What it does

The plugin talks to `gh` in the project Git root. It does not reimplement stacked PRs.

- Visual view of the current stack (`gh stack view --json`)
- Add a branch on top of the stack (`gh stack add`)
- Check out a layer (`gh stack checkout <branch>`)
- Rebase the stack (`gh stack rebase`, with `--upstack`, `--downstack`, `--no-trunk`), plus continue / abort
- Optionally stash uncommitted changes and untracked files around a rebase and restore them on success (Settings | Tools | GH Stack: ask / always / never)
- Prompt to enable `git rerere` before a stack is initialized or rebased, so repeated conflict resolutions are reused
- Switch among locally tracked stacks (reads `<git-common-dir>/gh-stack`, then `gh stack checkout`)

IntelliJ allows extra tabs on the Git / Version Control tool window, so the stack UI appears there as **Stack**. The same panel is also available as a dedicated **GH Stack** tool window (bottom stripe).

## Requirements

- IntelliJ IDEA 2024.3 or later
- [GitHub CLI](https://cli.github.com/) on your PATH
- `gh extension install github/gh-stack`
- `gh auth login`

If `gh` is not on the IDE's PATH, set the executable explicitly under
**Settings → Tools → GH Stack**. Leave the field empty to auto-detect `gh` on PATH.

## Run from source

Open this repository in IntelliJ (or Cursor) and run:

```bash
./gradlew runIde
```

That starts a sandbox IDE with the plugin installed.

## Install a local build

```bash
./gradlew buildPlugin
```

Then in IntelliJ: **Settings → Plugins → gear → Install Plugin from Disk…** and pick the zip under `build/distributions/`.
