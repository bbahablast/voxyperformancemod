# Voxy Storage & Stats

An addon for **[Voxy](https://modrinth.com/mod/voxy)** by **MCRcortex**. Voxy is required
and is installed separately — it is All Rights Reserved, and no part of it is included in
or redistributed by this mod. This addon is original work, not a fork or a port, and is
released under the MIT licence.

Voxy is very good at drawing terrain you're nowhere near. It's less good at telling you
what that costs. After a few long sessions the LOD store has quietly grown to gigabytes,
and there's no way to see how big it is or get any of it back.

This adds both.

## Commands

- `/voxystats stats` — sections stored in the current world, and how many bytes they take.
- `/voxystats list` — every store on your disk, biggest first, with size and how long since
  you touched it. Multiplayer stores are grouped by server address, so it's obvious which
  server ate 4 GB.
- `/voxystats compact` — reclaims space in the world you're in. Runs in the background.
- `/voxystats delete <n> confirm` — removes a whole store.

There's also an F3 readout with store size and per-LOD-level section counts. It switches
itself on the first time you launch.

## The thing worth knowing

Voxy keeps LODs in RocksDB. Deleting a row from RocksDB just writes a tombstone; the bytes
stay on disk until the database gets compacted, and Voxy never compacts. If you've noticed
your Voxy folder only ever grows, that's why. `/voxystats compact` is the fix, and it tells
you how much it actually freed rather than guessing.

## Before you install

Delete won't touch the world you're currently in, because removing files under an open
database corrupts it. Join a different world or server first.

This is alpha software. It's client-side only and needs both Voxy and Fabric API. Each
build targets one Minecraft version and is compiled against a specific Voxy release —
Voxy is pre-release and its internals move, so expect this to need updating when it does.

Not affiliated with Voxy. Grab Voxy from its own page.
