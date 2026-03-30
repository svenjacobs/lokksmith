# GitHub Copilot Instructions

## Commit Messages

Commit messages must follow the [Conventional Commits](https://www.conventionalcommits.org/) specification.

Examples:

- `feat: add new authentication method`
- `fix: resolve crash when token is null`
- `docs: update contributing guide`
- `chore: bump dependency versions`
- `refactor: simplify key derivation logic`
- `test: add unit tests for encryption module`

## Pull Request Titles

The title of a pull request must follow the Conventional Commits format described above and should be written as the intended squash-merge commit message (when using squash-and-merge).

## Code Style

After generating or altering any code, run the following command to reformat it according to the project's code style guidelines:

```
./gradlew spotlessApply
```

## Pull Request Labels

Apply appropriate labels to pull requests. Examples of labels to use:

- `enhancement` – new features or improvements
- `bug` – bug fixes
- `documentation` – documentation changes
- `maintenance` – dependency updates, refactoring, chores

## Testing

All generated or altered code must be covered by unit tests. Add or update unit tests alongside any code changes.
