# Contributing

Sietch welcomes focused changes to its content-addressed indexing library and CLI.

## Development setup

Install JDK 21, clone the repository, and run:

```bash
./gradlew test
```

Changes to `sietch-core` must preserve or intentionally version the API consumed by
CHOAM. Include tests for parsing, hashing, traversal, ignore rules, registry behavior,
and failure cases affected by the change.

## Privacy

Tests and documentation must use synthetic files, documentation addresses, and neutral
machine names. Do not commit real catalogs, registry databases, account names, hostnames,
tailnet addresses, mount labels, or private project names.

Before opening a pull request:

```bash
git diff --check
./gradlew test
```

Explain the behavior change, compatibility impact, and verification in the pull request.
