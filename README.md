# GH Stack for IntelliJ

IntelliJ plugin that wraps the GitHub CLI [`gh stack`](https://github.com/github/gh-stack) extension so you can view and navigate stacked PRs without leaving the IDE.

Your support will help me keep this project going. Thank you!

[![Buy me a coffee](https://img.shields.io/badge/buy%20me%20a%20coffee-ffdd00?style=for-the-badge&logo=buy-me-a-coffee&logoColor=black)](https://payments.cashfree.com/forms/intellij-gh-stack)

## What it does

The plugin talks to `gh` in the project Git root. It does not reimplement stacked PRs.

- Visual view of the current stack (`gh stack view --json`)
- Add a branch on top of the stack (`gh stack add`)
- Check out a layer (`gh stack checkout <branch>`)
- Switch among locally tracked stacks (reads `.git/gh-stack`, then `gh stack checkout`)

IntelliJ allows extra tabs on the Git / Version Control tool window, so the stack UI appears there as **Stack**. The same panel is also available as a dedicated **GH Stack** tool window (bottom stripe).

## Requirements

- IntelliJ IDEA 2024.3 or later
- [GitHub CLI](https://cli.github.com/) on your PATH
- `gh extension install github/gh-stack`
- `gh auth login`

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
