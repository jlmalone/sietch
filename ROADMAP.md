# Sietch Roadmap

> **Sietch** — IPFS content indexing library and CLI. Powers CHOAM's file cataloging.

---

## Phase 1: Open-Source Prep

Same treatment CHOAM received before going public.

### 1.1 — Git History Anonymization
- Rewrite git history to remove PII (machine names, paths, IP addresses)
- Remove any references to specific Tailscale IPs, SSH users, or hostnames
- Scrub commit messages for personal info

### 1.2 — License
- Add MIT License file
- Add license headers if needed

### 1.3 — README Refresh
- Update README with clear usage examples
- Document the composite build relationship with CHOAM
- Add API docs for `sietch-core` module

### 1.4 — Test Audit
- Ensure all tests pass without a live Kubo IPFS node
- Mock or skip IPFS-dependent tests when node unavailable
- Target: all tests green in CI without external dependencies

### 1.5 — Cleanup
- Remove hardcoded IPs, hostnames, usernames
- Review `choam.conf.example` for sensitive data
- Verify no credentials in source tree

---

## Phase 2: Standalone Improvements

### 2.1 — Incremental Indexing
- Support indexing only changed files (by mtime comparison)
- Reduce full rescan from 50h to minutes for routine updates

### 2.2 — Multi-Hash Support
- Support SHA-256, Blake3, CIDv1 simultaneously
- Allow choosing hash algorithm per catalog

### 2.3 — Better CLI UX
- Progress bars for long indexing operations
- Human-readable output by default

---

## Ecosystem Relationship

Sietch is consumed by CHOAM via Gradle composite build:
```kotlin
// choam/settings.gradle.kts
includeBuild("../Sietch")

// choam/build.gradle.kts
implementation("vision.salient.sietch:sietch-core")
```

Changes to Sietch APIs affect CHOAM. Coordinate releases.
