# Sietch

[![CI](https://github.com/jlmalone/sietch/actions/workflows/ci.yml/badge.svg)](https://github.com/jlmalone/sietch/actions/workflows/ci.yml)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](LICENSE)

IPFS-backed universal content index. Catalogs files with content-addressed identifiers (CIDs) — same file = same CID, forever, regardless of where it lives.

## What It Does

Sietch indexes directory trees and produces catalogs with:
- **IPFS CIDs** — content-addressed identifiers computed via Kubo
- **SHA-256 hashes** — cryptographic integrity verification
- **File sizes** — space accounting
- **Location tracking** — which files live on which machines

```
path                    cid                                           sha256              size
main/kotlin/App.kt     bafkreifzbrtilt...                           b90c6685ce08...     7053
lib/Utils.kt           bafkreib2g5k5ex...                           3a3755d25cbd...     4340
```

## Why

Files spread across multiple machines (NAS, cloud, laptops) are referenced by paths — brittle, machine-specific, and break when content moves. IPFS CIDs solve this at the protocol level: content-addressed, location-independent, permanently verifiable.

Sietch is the index layer. CHOAM is the transport layer (rsync). Kubo is the IPFS node.

## Install

Requires JDK 21+.

```bash
git clone https://github.com/jlmalone/Sietch.git
cd Sietch
./gradlew build
```

## Usage

### IPFS-Aware Indexing (New)

```bash
# Index a directory with IPFS CIDs
./gradlew :sietch-cli:run --args="index /path/to/scan --machine my-machine --ipfs http://localhost:5001"

# Resolve a CID to a file
./gradlew :sietch-cli:run --args="resolve bafkrei..."

# List content on a machine
./gradlew :sietch-cli:run --args="list --machine my-machine"

# Verify locations still exist
./gradlew :sietch-cli:run --args="verify --machine my-machine"

# Check IPFS + registry status
./gradlew :sietch-cli:run --args="status --ipfs http://localhost:5001"
```

### Classic Indexing

```bash
# SHA-256 catalog (backward compatible)
./gradlew run --args="/path/to/directory --output catalog.txt --hash sha256"

# Via Gradle task
./gradlew index -PscanPath=/path/to/directory -Poutput=catalog.txt

# Inspect a SQLite database
./gradlew inspectDb -Pdb=/path/to/database.sqlite
```

## Architecture

```
sietch-core (library)          sietch-cli (commands)
├── walkTree()                 ├── index (IPFS CIDs + registry)
├── computeHash()              ├── resolve (CID → file)
├── indexDirectory()           ├── list (machine inventory)
├── IpfsClient                 ├── verify (check locations)
├── ContentLocationRegistry    ├── catalog (legacy format)
└── ContentResolver            └── status (IPFS + registry)
```

- **sietch-core**: Pure library, no CLI deps. Used by CHOAM and other projects.
- **sietch-cli**: Clikt commands wrapping sietch-core.
- **Kubo**: IPFS reference node (default: localhost:5001, configure with `--ipfs` flag).

## Tech Stack

- Kotlin 2.0 / JVM 21
- Ktor 3.0.3 (HTTP client for Kubo API)
- SQLite JDBC (location registry + Naib)
- Clikt (CLI framework)
- Kubo 0.39.0 (IPFS node)
- Gradle 8.11

## Name

Named after the Fremen cave dwellings in Frank Herbert's *Dune*. A sietch stores water — the most precious resource on Arrakis. This tool indexes your data — the most precious resource on your drives.

## License

MIT License. See [LICENSE](LICENSE) for details.
