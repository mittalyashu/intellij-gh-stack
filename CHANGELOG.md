# Changelog

All notable changes to this plugin are documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Added

- List every tracked stack beside the current stack in the **Stack** panel, so switching stacks no longer needs the **All stacks** dialog
- Prune a dangling local stack from the **All stacks** context menu when all of its branches are gone, so merged stacks stop lingering in local tracking

### Fixed

- Stop using the deprecated `Disposer.isDisposed` API in the **Stack** and **Console** panels

## [0.1.0]

Initial release. A thin IntelliJ wrapper around the GitHub CLI
[`gh stack`](https://github.com/github/gh-stack) extension.

### Added

- View the current stacked PR chain in a dedicated **GH Stack** tool window
- Per-layer PR number, current branch, and merged / queued / needs-rebase status
- Check out a specific layer
- Copy the selected branch name from the stack list with Ctrl/Command+C
- Explain every panel action with a tooltip, including the `gh stack` command it runs in a code block
- Add a branch on top of the stack
- Initialize a new stack
- Rebase the stack (whole, upstack, downstack, or without trunk), plus continue and abort
- Rebase from a specific layer upward by right-clicking it in the stack list
- Prompt to enable `git rerere` before initializing or rebasing a stack, so repeated conflict resolutions are reused
- Optional stash of uncommitted changes and untracked files around a rebase (ask / always / never)
- Switch between locally tracked stacks
- Repository selector in the panel to switch between the project repository and its Git submodules
- Settings to configure the `gh` executable path
- Show the detected `gh` CLI and `gh stack` extension versions in the settings panel
- Log every `gh` command with its output and exit code in the **Console** tab of the **GH Stack** tool window

[Unreleased]: https://github.com/mittalyashu/intellij-gh-stack/compare/v0.1.0...HEAD
[0.1.0]: https://github.com/mittalyashu/intellij-gh-stack/releases/tag/v0.1.0
