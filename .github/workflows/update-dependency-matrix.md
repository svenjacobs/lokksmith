---
on:
  release:
    types: [published]

permissions:
  contents: read
  pull-requests: read

tools:
  github:
    toolsets: [context, repos]
  edit:

network: defaults

safe-outputs:
  create-pull-request:
    labels: [documentation]
    base-branch: main

---

# Update Dependency Matrix

This workflow runs after a release is published and updates
`site/docs/dependency-matrix.md` with the dependency versions
used in that release.

## Context

- The release tag is available as `${{ github.event.release.tag_name }}` (e.g. `v0.13.0`).
- The version number without the leading `v` is the Lokksmith version (e.g. `0.13.0`).
- The authoritative source of dependency versions for each release is
  `lib/gradle/libs.versions.toml` in the repository.

## Commit conventions

Every commit created during this workflow — including the automatic initial
"Initial plan" commit and the final PR commit — **must** follow the
[Conventional Commits specification](https://www.conventionalcommits.org/en/v1.0.0/).

The format is: `<type>[optional scope]: <description>`

- Use `chore(docs)` as the type+scope for all commits in this workflow.
- Example: `chore(docs): update dependency matrix for v0.13.0`
- The initial "Initial plan" commit message must also use this format,
  e.g. `chore(docs): initial plan for updating dependency matrix`.

## Instructions

1. Read `lib/gradle/libs.versions.toml` from the repository.

2. Extract the following version values from the `[versions]` section:
   - `kotlin` → **Kotlin** column
   - `kotlinx-coroutines` → **Coroutines** column
   - `kotlinx-serialization` → **Serialization** column
   - `ktor` → **Ktor** column
   - `compose-multiplatform` → **Compose Multiplatform** column

3. Strip the leading `v` from `${{ github.event.release.tag_name }}` to get the
   Lokksmith version (e.g. `v0.13.0` → `0.13.0`).

4. Read `site/docs/dependency-matrix.md`.

5. Update the **lokksmith-core** table:
   - Check the last row. If every dependency column value matches the new
     release, extend the version range in the existing row
     (e.g. `0.13.0` becomes `0.13.0 - 0.13.1`, or
     `0.13.0 - 0.13.1` becomes `0.13.0 - 0.13.2`).
   - If any dependency differs from the last row, append a new row for
     the new version.
   - When a column value is the same as the one directly above it in the
     table, use `"` (a double-quote character) to indicate "same as above",
     following the existing table style.
   - **Bold convention**: For each column, only the single latest (most recently
     introduced) version value in the entire column should be wrapped in `**…**`.
     All older values must NOT be bold. When you append a new row and a column
     value changes, remove the bold markers from that column's previously-bolded
     value and bold the new value instead. Columns whose value did not change
     keep their existing bold placement unchanged.
   - Keep the column widths consistent with the existing table formatting.

6. Apply the same logic to the **lokksmith-compose** table using only the
   `compose-multiplatform` version.

7. Write the updated content back to `site/docs/dependency-matrix.md` using
   the edit tool.

8. Create a pull request (following the commit conventions defined above):
   - commit message: `chore(docs): update dependency matrix for ${{ github.event.release.tag_name }}`
   - title: `chore(docs): update dependency matrix for ${{ github.event.release.tag_name }}`
   - body: `Update dependency matrix in \`site/docs/dependency-matrix.md\` for release ${{ github.event.release.tag_name }}.`
   - branch: `chore/update-dependency-matrix-${{ github.event.release.tag_name }}`
