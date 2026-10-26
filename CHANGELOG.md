# Changelog

All notable changes to this plugin are documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

## [0.1.0]

Initial release. A thin IntelliJ wrapper around the GitHub CLI
[`gh stack`](https://github.com/github/gh-stack) extension.

### Added

- View the current stacked PR chain in the Git tool window, or in a dedicated **GH Stack** tool window
- Per-layer PR number, current branch, and merged / queued / needs-rebase status
- Check out a specific layer
- Add a branch on top of the stack
- Initialize a new stack
- Rebase the stack (whole, upstack, downstack, or without trunk), plus continue and abort
- Prompt to enable `git rerere` before initializing or rebasing a stack, so repeated conflict resolutions are reused
- Optional stash of uncommitted changes and untracked files around a rebase (ask / always / never)
- Switch between locally tracked stacks
- Settings to configure the `gh` executable path

[Unreleased]: https://github.com/mittalyashu/intellij-gh-stack/compare/v0.1.0...HEAD
[0.1.0]: https://github.com/mittalyashu/intellij-gh-stack/releases/tag/v0.1.0
