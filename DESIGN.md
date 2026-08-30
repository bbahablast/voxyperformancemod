# Design notes

Why the addon is shaped this way. Written against `MCRcortex/voxy@337b919`
(`0.2.19-beta`, MC 26.2), updated as things got built.

## Why storage and not performance

Voxy is the performance layer, with Sodium and Nvidium below it. An addon claiming to make
Voxy faster would be optimising someone else's renderer from the outside through mixins,
against a codebase at 0.2.x with 175 open issues. That breaks on every alpha bump.

Disk and observability were unserved. The addons that exist cover other ground:

| Addon | Covers | Downloads |
| --- | --- | --- |
| Voxy WorldGen | background chunk gen and auto-ingest | 657K |
| Voxy Extra | nether fog fix, server black/whitelist, LoD mirror | 214K |
| Voxy Server Side / Voxy Server | server-side LOD streaming | 123K / 54K |
| Voxy Auto LOD | generate LODs without flying | 28K |
| Voxy Fog Addon | render distance fog | 10K |
| Voxy Hypixel Addon | per-island world isolation | 8K |

Pregeneration, streaming, fog and per-server isolation were taken. Storage management wasn't.

## Relocation already existed

The first version of this plan was wrong. Issue #646, "can I change where voxy writes to?",
looks like a missing feature and isn't. `ConfigBuildCtx` already defines path tokens:

```java
public static final String BASE_SAVE_PATH   = "{base_save_path}";
public static final String WORLD_IDENTIFIER = "{world_identifier}";
public static final String PLAYER_UUID      = "{player_uuid}";
public static final String DEFAULT_STORAGE_PATH = BASE_SAVE_PATH+"/"+WORLD_IDENTIFIER+"/storage/";
```

`BasicPathInsertionConfig` inserts an arbitrary path into the backend chain, and
`concatPath` handles absolute paths and Windows drive letters. Pointing the store at another
disk works today by hand-editing `config.json`. It's a discoverability problem, not a
missing capability, so relocation dropped out of scope. If it comes back it's a UI over
something that already works, plus a safe migration.

## The prune and measure API is public

`StorageBackend` has what's needed:

```java
public abstract MemoryBuffer getSectionData(long key, MemoryBuffer scratch);
public abstract void         setSectionData(long key, MemoryBuffer data);
public abstract void         deleteSectionData(long key);
public abstract void         flush();
public List<StorageBackend>  getChildBackends();   // + collectAllBackends()
void iteratePositions(int level, LongConsumer callback);  // via IStoredSectionPositionIterator
```

`iteratePositions` walks stored section keys per LOD level, and `WorldEngine` has static
decoders plus `MAX_LOD_LAYER = 4`. Walking every section, decoding its position, measuring
it and optionally deleting it is all expressible against public methods.

`WorldEngine.storage` is public, so enumeration needs no mixin. Deletion and byte sizes live
one layer down on `StorageBackend`, held in `SectionSerializationStorage.backend`, which
isn't public. That single field is why the first accessor exists.

## What an addon can't do

`Serialization.init()` scans only `me.cortex.voxy`, from Voxy's own mod container root:

```java
var path = FabricLoader.getInstance().getModContainer("voxy").get().getRootPaths().get(0);
```

So the tidy design, injecting a metering `DelegatingStorageAdaptor` into the config chain to
count bytes, doesn't work from outside. Squatting in `me.cortex.voxy` to get picked up by
the scan is fragile and rude. Measurement happens at runtime instead, through
`collectAllBackends()` and RocksDB's own size properties.

## Scope

Built:

- `list`, with size and age, covering worlds that aren't loaded
- `stats` and an F3 group: per-level section counts, SST and memtable bytes
- `compact`, off the render thread, with the world pinned by `acquireRef`
- `delete` for a whole store, refusing anything under the active base path

Not built:

- radius and level pruning. The code is in `StorePruner`, but nothing can reach it until a
  store can be opened for a world that isn't loaded.
- a relocation UI

Out of scope: the renderer, shaders and Iris; LOD relighting and seam repair (#638, #626,
upstream's to fix); anything server-side.

## Risks

| Risk | Handling |
| --- | --- |
| deleting live data corrupts a world | `delete` refuses anything under the active base path, and `StorePruner` refuses the loaded engine |
| compaction freezing the client | runs on a daemon thread, with the world pinned by `acquireRef` so Voxy can't free the DB underneath it |
| 0.2.x API churn | public methods plus two accessors. The `depends` range still has no upper bound, which is a live problem. |
| Voxy is All Rights Reserved | compiled against, never bundled, checked on every build |

## Questions that got answered

**Does RocksDB reclaim disk on delete without compaction?** No. `deleteSectionData` is a
`db.delete`, which writes a tombstone, and `flush()` only calls `flushWal(true)`. Nothing in
Voxy calls `compactRange`, so compaction had to be built here.

**Can stores be found for worlds that aren't loaded?** Yes, from disk. `StoreLocator` walks
both roots looking for a directory that contains `storage/`. The world directory is a 32
character hash of seed and dimension so it can't be labelled, but multiplayer stores sit
under the server address, which can.

**Where do stores live?** Singleplayer under `<save>/voxy`, multiplayer under
`.voxy/saves/<server address>`. Both are covered.

## Still open

Opening a store for a world that isn't loaded. RocksDB's LOCK file makes this safe by
construction, since the live world's database can't be opened twice, which turns the
dangerous case into an error instead of corruption. That unlocks radius and level pruning,
the main thing still missing.
