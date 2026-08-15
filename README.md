# Voxy Storage & Stats

A client-side Fabric addon for [Voxy](https://modrinth.com/mod/voxy) that manages the LOD
store on disk and reports what it contains.

Voxy generates a lot of data and gives you no way to see how much, or to get any of it
back. This adds size accounting, pruning that actually reclaims disk, and an F3 readout.

**Status: early. The storage layer works; there is no GUI yet.**

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

## Building

Requires JDK 25 and Gradle 9.6+ (both matched to Voxy's own build).

```
./gradlew build
```

Voxy is resolved from the Modrinth maven by version ID, not version number — the same
version number is published for several Minecraft versions, so the number alone is
ambiguous. See `voxy_version` in `gradle.properties`.

## Licensing

This mod is MIT. Voxy is **All Rights Reserved, "Do not redistribute"** — it is used as a
`compileOnly` / `runtimeOnly` dependency and no part of it is copied or bundled into our
jar. If you package this, ship this jar only and let users install Voxy themselves from
its official Modrinth page.
