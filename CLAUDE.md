# Working in this repo

## Communication

Be plain and concise. No tables unless the data genuinely needs columns, no section
headers on short answers, no restating what was just done. Lead with the finding, not the
process that produced it.

## Project

A Fabric addon for Voxy (`MCRcortex/voxy`), an LOD rendering mod. See DESIGN.md for scope
and the reasoning behind it.

Voxy is All Rights Reserved and must never be bundled or copied into this repo — it is a
`compileOnly` / `runtimeOnly` dependency only.

Keep the mixin surface minimal. Voxy is pre-release and changes often; every mixin is a
future breakage. Prefer its public API, and justify any new mixin in the README table.

## Code style

Write it modular, so someone else can open one file and change it without reading the
rest. Concretely, for this repo:

- Packages are the module boundary and stay single-purpose: `store` reads and prunes the
  Voxy world store, `hud` renders, `command` parses and dispatches, `mixin` holds nothing
  but accessors. Logic never leaks across those lines — a command handler calls into
  `store`, it does not open a database itself.
- One responsibility per class, one job per method. When a class needs an "and also" to
  describe it, split it.
- Keep the public surface small: expose the few methods callers need, make the rest
  private. Callers depend on a class's API, never on its internals.
- No duplicated logic. Extract it once (`PruneSelectors`, `StoreLocator`) and call it.
- Constants and paths live in one named place, not as literals at each use site.
- Mixin accessors stay dumb — they expose a field and nothing more, so the behaviour that
  uses them lives in normal, readable classes.

## Build

JDK 25, Gradle 9.6+, matched to Voxy's own `gradle.properties`. Voxy is pinned by Modrinth
version *ID* rather than version number, since the same number is published for multiple
Minecraft versions.
