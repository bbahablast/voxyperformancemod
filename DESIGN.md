# Voxy Storage & Stats — Design

A client-side Fabric addon for [Voxy](https://modrinth.com/mod/voxy) that manages the LOD
store on disk and surfaces what Voxy is actually doing.

Status: design only, no code yet.

---

## 1. Why this, and not a "performance mod"

Voxy *is* the performance layer. Below it sit Sodium and Nvidium. An addon claiming to make
Voxy faster would have to optimise someone else's renderer from the outside via mixins —
against a codebase at `0.2.19-beta` with 175 open issues. That breaks on every alpha bump.

The unserved need is disk and observability. Nothing on Modrinth covers it:

| Existing addon | Covers | Downloads |
| --- | --- | --- |
| Voxy WorldGen | background chunk gen + auto-ingest | 657K |
| Voxy Extra | nether fog fix, server black/whitelist, LoD mirror | 214K |
| Voxy Server Side / Voxy Server | server-side LOD streaming | 123K / 54K |
| Voxy Auto LOD | generate LODs without flying | 28K |
| Voxy Fog Addon | render distance fog | 10K |
| Voxy Hypixel Addon | per-island world isolation | 8K |

Pregeneration, streaming, fog and per-server isolation are taken. Storage management is not.

## 2. What the source actually says

Read against `MCRcortex/voxy@337b919` (dev, `mod_version = 0.2.19-beta`, MC `26.2`).

### 2.1 Relocation already exists — correcting my own premise

Issue #646 ("can I change where voxy writes to?") is **not** a missing feature. It is a
discoverability problem. `ConfigBuildCtx` already defines path tokens:

```java
public static final String BASE_SAVE_PATH   = "{base_save_path}";
public static final String WORLD_IDENTIFIER = "{world_identifier}";
public static final String PLAYER_UUID      = "{player_uuid}";
public static final String DEFAULT_STORAGE_PATH = BASE_SAVE_PATH+"/"+WORLD_IDENTIFIER+"/storage/";
```

and `BasicPathInsertionConfig` (`"BasicPathConfig"`) inserts an arbitrary path into the
backend chain. `ConfigBuildCtx.concatPath` explicitly handles absolute paths and Windows
drive letters — so pointing the store at another disk works today, by hand-editing
`config.json`.

**Consequence for us:** we do not build relocation. We build a *UI and a safe migration* for
a capability that already exists but that ~nobody can find. That is a smaller, more honest
claim, and it cannot be "fixed upstream" out from under us — upstream shipping a config GUI
is the only thing that would obsolete it.

### 2.2 The prune/measure API is public and sufficient

`StorageBackend` (`common/config/storage/StorageBackend.java`) exposes everything needed:

```java
public abstract MemoryBuffer getSectionData(long key, MemoryBuffer scratch);
public abstract void         setSectionData(long key, MemoryBuffer data);
public abstract void         deleteSectionData(long key);
public abstract void         flush();
public List<StorageBackend>  getChildBackends();   // + collectAllBackends()
void iteratePositions(int level, LongConsumer callback);  // via IStoredSectionPositionIterator
```

`iteratePositions` enumerates stored section keys per LOD level; `WorldEngine` has static
decoders `getLevel/getX/getY/getZ(long id)` and `MAX_LOD_LAYER = 4`. So "walk every stored
section, decode its position, measure it, optionally delete it" is expressible entirely
against public methods. No renderer internals touched.

### 2.3 Reachability

`WorldEngine.storage` is `public final SectionStorage`, and `SectionStorage` implements
`iteratePositions`. Enumeration therefore needs no mixin at all.

`deleteSectionData` and byte sizes live on `StorageBackend`, held in
`SectionSerializationStorage.backend` (non-public). That needs exactly **one**
`@Accessor` mixin on a single field. That is our entire mixin surface — worth stating in the
README, because a one-field accessor is about as update-resilient as an addon gets.

### 2.4 We cannot register our own config types

`Serialization.init()` scans only `me.cortex.voxy`, from Voxy's own mod container root:

```java
var path = FabricLoader.getInstance().getModContainer("voxy").get().getRootPaths().get(0);
```

So the tempting design — inject a metering `DelegatingStorageAdaptor` into the config chain
to count bytes — **will not work** for an addon. Squatting in `me.cortex.voxy` to get picked
up by the classloader scan is fragile and rude. We measure at runtime instead, via
`collectAllBackends()` plus direct filesystem sizing of the resolved store path.

### 2.5 Integration points for UI

From `fabric.mod.json`, Voxy registers `sodium:config_api_user` →
`VoxyConfigMenu`. Voxy Extra states all its options live in the Sodium menu; we do the same
rather than inventing a screen. For the HUD, `VoxyDebugScreenEntry` calls
`instance.addDebug(lines)` and `RenderStatistics.addDebug(lines)` — we register our own
debug screen entry alongside rather than mixin into theirs.

## 3. Scope

**v0.1**
- Storage browser: every world/server Voxy has a store for, with on-disk size, section
  counts per LOD level, and last-played date.
- Prune: delete a whole world's store; delete sections beyond radius N of a chosen centre;
  delete LOD level 0 only (the bulk of the bytes) while keeping coarse levels.
- Relocation UI: pick a directory, rewrite `config.json` to a `BasicPathConfig` wrapper, and
  move existing data with verification before deleting the source.
- Diagnostics HUD: store size, section counts, ingest/save queue depth, `MemoryBuffer`
  count and total MB (all already surfaced by `VoxyInstance.addDebug`).

**Explicitly out of scope**
- Anything touching the renderer, shaders, or Iris.
- LOD relighting / seam repair (issues #638, #626) — upstream's to fix.
- Server-side anything.

## 4. Risks

| Risk | Handling |
| --- | --- |
| Deleting live data corrupts a world | Prune only while no `WorldEngine` is active for that identifier; `flush()` after; never touch the store of the world currently loaded. |
| `0.2.x` is pre-release; API churn | Depend on public `StorageBackend` methods + one accessor. Pin a tested Voxy range in `depends`. |
| Voxy is All-Rights-Reserved, "Do not redistribute" | We compile against it from the Modrinth maven and ship **only our own jar**. No Voxy classes bundled, no code copied. Worth a line in the README. |
| RocksDB space is not reclaimed on delete | Deletes are logical; report both logical section bytes and actual directory size, and expose an explicit compaction step. Needs verification against `RocksDBStorageBackend`. |
| Upstream ships a config GUI | Accepted. Prune and stats still stand alone. |

## 5. Build target

Match Voxy's own `gradle.properties`: MC `26.2`, Fabric loader `0.19.3`, Loom
`1.16-SNAPSHOT`, Sodium `mc26.2-0.9.2-alpha.3-fabric`. Voxy pulled from
`https://api.modrinth.com/maven` (`maven.modrinth:voxy:<version>`), `compileOnly` +
`modRuntimeOnly` — never bundled.

Entrypoints: `client` for init, `sodium:config_api_user` for options, plus a debug screen
entry for the HUD.

## 6. Open questions

1. Does RocksDB reclaim disk on `deleteSectionData` without explicit compaction? Determines
   whether "freed 4 GB" is honest or a lie.
2. Is there a safe way to enumerate stores for worlds that are *not* currently loaded?
   `.voxy/saves/<world_identifier>/storage/` is walkable on disk, but mapping a directory
   back to a human-readable server/world name needs a check of `WorldIdentifier.getWorldId()`.
3. Singleplayer stores live under the save dir (`<world>/voxy`), multiplayer under
   `.voxy/saves` — the browser must cover both roots.
