# Contributing

Thank you for your interest in contributing! ❤️

All contributions are welcome and appreciated. Please review the relevant sections below before
making your first contribution. This helps maintainers and ensures a smooth experience for everyone.

!!! tip
    If you like this project but don't have time or can't contribute code, you can still support us
    by:

    - Starring the project
    - Sharing it on social media or in blog posts
    - Mentioning it in your project's README
    - Talking about it at meetups or with friends and colleagues

## Questions

If you have a question, please open a [discussion](https://github.com/svenjacobs/lokksmith/discussions)
or check the existing topics. Please do not use issues for questions and discussions as they are
reserved for [bug reports](#reporting-bugs).

## Suggestions & Feature Requests

Before submitting a suggestion, read the [documentation](https://lokksmith.dev) carefully and find
out if the functionality is already covered, maybe by an individual configuration. To suggest a new
feature or improvement, please [open an issue](https://github.com/svenjacobs/lokksmith/issues/new)
and describe your idea clearly.

## Reporting Bugs

Before reporting a bug please ensure that you can reproduce the issue with the latest version
`{{ version }}` of Lokksmith. If this is the case, please [open an issue](https://github.com/svenjacobs/lokksmith/issues/new)
with the following information:

- Environment
    - Device manufacturer and model
    - Device's operating system (Android, iOS)
    - Operating system version
- Browser used for authentication flows
- Steps to reproduce
- Expected and actual behavior
- Screenshots and / or video recordings
- Minimal sample project that reproduces the issue
- Relevant logs (stack trace)

## Contributing Code

By contributing to this project, you confirm that you are the original author of your contributions,
have the necessary rights to submit them, and agree that your contributions may be distributed under
the project license.

### Setup

- Use the latest stable version of [Android Studio](https://developer.android.com/studio/) for
  development.
- Optional: Install the latest LTS version of [NodeJS](https://nodejs.org/).
    - Run `npm install` in the root folder of the project to set up Git commit hooks.

!!! info
    [Commit messages](#commit-messages) are verified via Continuous Integration. Therefor we suggest setting up the
    commit hooks to catch errors early.

### Rules

- Document your code.
- Ensure your code is covered by unit tests.
- If you use a LLM for code generation, **clearly state this** in your pull request.
  All generated code must be **thoroughly reviewed by humans**.

### Styleguide

#### Code

- Follow the [Kotlin Coding Conventions](https://kotlinlang.org/docs/coding-conventions.html).
- Use spaces with an indentation of 4 characters.

#### Commit Messages

- Use the [Conventional Commits](https://www.conventionalcommits.org/) specification.
- Write commit messages in present tense, imperative mood (e.g., `feat: add abc to xyz`).

<h2>Attribution</h2>
This guide is based on the [contributing.md generator](https://contributing.md/generator)!

*[LLM]: Large Language Model
