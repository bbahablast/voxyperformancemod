# Voxy Storage & Stats

A client-side Fabric addon for [Voxy](https://modrinth.com/mod/voxy) that manages the LOD
store on disk and reports what it contains.

Voxy generates a lot of data and gives you no way to see how much, or to get any of it
back. This adds size accounting, pruning that actually reclaims disk, and an F3 readout.

**Status: alpha.** There is no GUI yet — everything is driven by commands and F3. Voxy
itself is pre-release and its internals move between versions, so expect this to need a
rebuild whenever Voxy updates.

## Commands

| Command | Does |
| --- | --- |
| `/voxystats stats` | section counts and byte totals for the loaded world |
| `/voxystats list` | every store on disk, largest first, with size and age |
| `/voxystats compact` | compacts the loaded world's store, reclaiming deleted space |
| `/voxystats delete <n> confirm` | deletes a whole store from `list`, refusing one in use |

Deletion refuses the store belonging to the world you are currently in, because removing
files under an open RocksDB corrupts it. Client commands only run inside a world, so to
delete a store you have to be somewhere else — join another world or server first. In
singleplayer, where the only store is usually the one you are standing in, that means
loading a different save.

`list` finds stores for worlds that are not loaded, which is the point — the space you
want back usually belongs to a server you have stopped playing. Multiplayer stores are
grouped by server address; the per-world directory below it is a hash of seed and
dimension and cannot be turned back into a readable name.

Fine-grained pruning (by radius, or by LOD level) exists in `StorePruner` but has no
command yet. It refuses to touch the loaded world, and opening a store for an unloaded
world is not implemented, so nothing can currently reach it. That is the next piece.

## Checking it loaded

Look for `Voxy Storage & Stats loaded` in the log, then open F3 — there should be a
store group showing disk/live/reclaimable bytes and per-level section counts.

Registering a debug entry does not make it visible: `DebugScreenEntryList.getStatus`
returns `NEVER` for any id it has no stored status for, and a modded id is never in a
vanilla debug profile. So the mod switches its own group on once, on first run, and
records that in `config/voxystats-debug-entry-initialised`. Delete that file to have it
switched back on; toggling it off in the F3 options afterwards sticks.

See [DESIGN.md](DESIGN.md) for how this was scoped and what was ruled out.

## Why deleting isn't enough

Voxy's default backend is RocksDB. `deleteSectionData` issues a RocksDB delete, which
writes a tombstone — the bytes remain in their SST files until those files are compacted.
Voxy's `flush()` only calls `flushWal(true)`, and nothing in the backend ever calls
`compactRange`. So a prune that just deletes reports thousands of removed sections and
frees no disk whatsoever.

`StorePruner` compacts after deleting, and reports the before/after size from RocksDB
rather than guessing.

## What it touches in Voxy

Two `@Accessor` mixins on three fields, and nothing else:

| Mixin | Field | Why |
| --- | --- | --- |
| `SectionSerializationStorage` | `backend` | `WorldEngine.storage` is public but only offers load/save and position iteration; deletion and byte sizes live on the `StorageBackend` below it |
| `RocksDBStorageBackend` | `db`, `worldSections` | constant-time size properties, and `compactRange` to actually reclaim space |

Everything else — enumerating sections, deleting, flushing, reaching the live world — goes
through Voxy's public API. That is deliberate: Voxy is at `0.2.x` with a large open issue
count, and a small mixin surface is what keeps an addon alive across its releases.

## Supported versions

| Minecraft | Voxy | Status |
| --- | --- | --- |
| 26.2 | 0.2.18-beta | built and tested |
| 26.1.2 | 0.2.18-beta | builds, untested in game |
| 1.21.x | 0.2.4 – 0.2.16 | not built — see below |
| 1.20.4 | 0.1.5-alpha | not possible |

**1.21.x** is a toolchain problem, not a code one. Voxy's storage API is identical there,
and `StoreAccess` resolves Voxy's render system reflectively precisely so the rename
between `IGetVoxyRenderSystem` (1.21.x) and `IVoxyRenderSystemHolder` (26.x) does not
matter. What blocks it is the build: Fabric API for 1.21.x ships access wideners in the
intermediary namespace, and Loom 1.16 — required for 26.x — expects the official
namespace and has dropped the `modImplementation` configurations that used to remap
them. Supporting 1.21.x means running a second Loom generation, not adding a row to the
target map.

**1.20.4** is out for good. Voxy 0.1.5-alpha there predates the storage abstraction
entirely: no `StorageBackend`, no `SectionSerializationStorage`, no `VoxyCommon`. There is
nothing to port to.

## Building

Requires JDK 25 and Gradle 9.6+ (both matched to Voxy's own build).

```
./gradlew build -PmcVersion=26.2     # one target
./buildAll.sh                        # every target
```

Targets live in the `TARGETS` map in `build.gradle`. Voxy is pinned there by Modrinth
version *ID* rather than version number, since the same number is published for several
Minecraft versions.

## Licensing

This mod is MIT. Voxy is **All Rights Reserved, "Do not redistribute"** — it is used as a
`compileOnly` / `runtimeOnly` dependency and no part of it is copied or bundled into our
jar. If you package this, ship this jar only and let users install Voxy themselves from
its official Modrinth page.
