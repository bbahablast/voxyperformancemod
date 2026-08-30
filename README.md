# Voxy Storage & Stats

A client-side Fabric addon for [Voxy](https://modrinth.com/mod/voxy) by MCRcortex. It tells
you how much disk Voxy's LOD store is using, and lets you get some of it back.

Alpha. No GUI yet, just commands and an F3 readout. Voxy is pre-release and its internals
move, so expect this to break when Voxy updates.

## Commands

| Command | Does |
| --- | --- |
| `/voxystats stats` | section counts and byte totals for the world you're in |
| `/voxystats list` | every store on disk, biggest first, with size and age |
| `/voxystats compact` | compacts the current world's store and reclaims deleted space |
| `/voxystats delete <n> confirm` | deletes a whole store from `list` |

`list` is the useful one. It finds stores for worlds you don't have open, which is where
wasted space actually collects. Multiplayer stores group under the server address, so a 4 GB
row labelled with a server you quit months ago is easy to spot. The directory below that is
a hash of the seed and dimension, so there's no turning it back into a readable name.

`delete` won't touch the world you're in, because removing files under an open RocksDB
corrupts it. Client commands only run inside a world, so you have to be somewhere else
rather than nowhere: join another world or server first. In singleplayer that usually means
loading a different save.

`StorePruner` can also prune by radius or LOD level, but nothing calls it yet. It refuses
the loaded world, and opening a store for an unloaded world isn't implemented, so there's no
way to reach it. That's the next piece.

## Why deleting doesn't free space

Voxy keeps LODs in RocksDB. `deleteSectionData` issues a RocksDB delete, which writes a
tombstone. The bytes stay in their SST files until those files get compacted, and nothing in
Voxy ever calls `compactRange`. Its `flush()` only flushes the WAL.

So a prune that just deletes reports thousands of removed sections and gives back no disk.
`/voxystats compact` is what reclaims it, and it reports the real before and after from
RocksDB instead of guessing.

Sizes come from two RocksDB properties, shown separately on purpose. `total-sst-files-size`
counts only what's been written to SST files, and `cur-size-all-mem-tables` counts what's
still buffered. A young store holds thousands of sections at zero SST bytes, so reporting
the first alone reads as "nothing is stored".

## Checking it loaded

Look for `Voxy Storage & Stats loaded` in the log, then open F3.

Registering a debug entry isn't enough to display it. `DebugScreenEntryList.getStatus`
returns `NEVER` for any id it has no stored status for, and a modded id never appears in a
vanilla debug profile. So the mod switches its own group on once, on first run, and records
that in `config/voxystats-debug-entry-initialised`. Delete that file to switch it back on.
Turning it off in the F3 options afterwards sticks.

## What it touches in Voxy

Two `@Accessor` mixins over three fields:

| Mixin | Field | Why |
| --- | --- | --- |
| `SectionSerializationStorage` | `backend` | `WorldEngine.storage` is public but only does load/save and position iteration. Deletion and byte sizes live on the `StorageBackend` underneath. |
| `RocksDBStorageBackend` | `db`, `worldSections` | constant-time size properties, and `compactRange` to reclaim space |

Everything else goes through Voxy's public API: enumerating sections, deleting, flushing,
reaching the live world. Voxy is at 0.2.x with 175 open issues, and a small mixin surface is
what keeps an addon working across its releases.

`StoreAccess` also finds Voxy's render system by reflection rather than naming the
interface. Voxy renamed it between Minecraft generations, `IGetVoxyRenderSystem` on 1.21.x
and `IVoxyRenderSystemHolder` on 26.x, and renamed the accessor method with it. The lookup
goes by return type, which hasn't changed.

## Supported versions

| Minecraft | Voxy | Status |
| --- | --- | --- |
| 26.2 | 0.2.18-beta | built and tested |
| 26.1.2 | 0.2.18-beta | builds, never run |
| 1.21.x | 0.2.4 to 0.2.16 | not built, see below |
| 1.20.4 | 0.1.5-alpha | not possible |

1.21.x is a toolchain problem rather than a code one. The storage API is identical there.
What blocks it: Fabric API for 1.21.x ships access wideners in the intermediary namespace,
and Loom 1.16, which 26.x needs, expects the official namespace and has dropped the
`modImplementation` configurations that used to remap them. Supporting 1.21.x means running
a second Loom generation, not adding a row to the target map.

1.20.4 is out for good. Voxy 0.1.5-alpha there predates the storage abstraction: no
`StorageBackend`, no `SectionSerializationStorage`, no `VoxyCommon`. Nothing to build on.

## Building

JDK 25 and Gradle 9.6+, both matched to Voxy's own build.

```
./gradlew build -PmcVersion=26.2   # one target
./buildAll.sh                      # all of them
```

Targets live in the `TARGETS` map in `build.gradle`. Voxy is pinned there by Modrinth
version *id* rather than version number, because the same number gets published for several
Minecraft versions.

## Licensing

This addon is MIT, and it's original work rather than a fork or a port.

Voxy is All Rights Reserved, "Do not redistribute". It's a `compileOnly` / `runtimeOnly`
dependency here, and no Voxy code is copied or bundled into the jar. If you redistribute
this, ship this jar alone and let people install Voxy from its own Modrinth page.

See [DESIGN.md](DESIGN.md) for how the scope got picked.
