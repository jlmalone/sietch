# Trusted-machine context

The public repository contains everything required to build and test Sietch. Private
operator context is optional and must remain outside Git.

## Context classes

- `AI.local.md` may describe private topology, operational history, and local integration
  decisions for trusted agents.
- Runtime catalogs and SQLite registries are machine data, not project configuration.
- SSH keys, tokens, passwords, and Keychain values are secrets and must never be placed in
  an overlay or copied with repository context.

## Propagation

Copy `AI.local.md` only between machines controlled by the same operator, over an
authenticated encrypted channel such as Tailscale SSH. Review the destination before
overwriting because each host may have additional local context.

Example:

```bash
chmod 600 AI.local.md
nice -n 19 rsync -a --chmod=F600 --rsync-path='nice -n 19 rsync' \
  AI.local.md "$TRUSTED_HOST:$SIETCH_CHECKOUT/AI.local.md"
```

Do not propagate registry databases as agent context. Use CHOAM's verified transfer
operations when a catalog or registry genuinely needs to move.
