# AGENTS.md

Apply any machine-level instructions first, then this repository file.

## Purpose

Sietch is a content-addressed file index. `sietch-core` is a reusable Kotlin library;
`sietch-cli` provides operator commands. CHOAM consumes `sietch-core` through a Gradle
composite build.

## Public-content contract

Treat the complete tracked tree and Git history as public.

- Use neutral machines such as `workstation`, `laptop`, and `server`.
- Use documentation addresses, generic paths, and synthetic content examples.
- Never track real hostnames, tailnet addresses, mount labels, account names, private
  repository names, catalog output, or database contents.
- Keep operator context in ignored `AI.local.md` and runtime databases outside the
  repository.
- Do not include secrets, SSH keys, Keychain values, or credentials in local overlays.

## Development

- Preserve the public APIs used by CHOAM or coordinate an intentional versioned change.
- Run `./gradlew test` after Kotlin or build changes.
- Keep generated Gradle, Kotlin, database, and catalog artifacts untracked.
- Read `docs/TRUSTED_MACHINE_CONTEXT.md` before propagating private context.
