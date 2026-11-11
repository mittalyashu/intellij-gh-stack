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

The plugin talks to `gh` in the selected Git repository: the repository of the file open in the editor by default, or a Git root picked in the panel. It does not reimplement stacked PRs.

- Visual view of the current stack as a commit graph (`gh stack view --json`)
- Add a branch on top of the stack (`gh stack add`)
- Check out a layer (`gh stack checkout <branch>`)
- Rebase the stack (`gh stack rebase`, with `--upstack`, `--downstack`, `--no-trunk`), plus continue / abort
- Rebase from a specific layer upward by right-clicking it in the stack list
- Optionally stash uncommitted changes and untracked files around a rebase and restore them on success (Settings | Tools | GH Stack: ask / always / never)
- Prompt to enable `git rerere` before a stack is initialized or rebased, so repeated conflict resolutions are reused
- Switch among locally tracked stacks from the **All stacks** column beside the current stack (reads `<git-common-dir>/gh-stack`, then `gh stack checkout`)
- Prune a dangling local stack from the **All stacks** context menu when all of its branches are gone, so merged stacks stop lingering in local tracking
- Follow the repository of the file selected in the editor, or pin the panel to the project repository, a Git submodule, or any other Git root in the project

The stack UI and the command log that records every `gh` invocation live in a dedicated **GH Stack** tool window (bottom stripe), as the **Stack** and **Console** tabs. The plugin no longer adds tabs to the Git / Version Control tool window.

## Requirements

- IntelliJ IDEA 2024.3 or later
- [GitHub CLI](https://cli.github.com/) on your PATH
- `gh extension install github/gh-stack` (v0.2.0 or later)
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
