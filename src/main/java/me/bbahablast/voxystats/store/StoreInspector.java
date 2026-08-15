package me.bbahablast.voxystats.store;

import me.bbahablast.voxystats.VoxyStats;
import me.bbahablast.voxystats.mixin.RocksDBStorageBackendAccessor;
import me.cortex.voxy.common.config.storage.StorageBackend;
import me.cortex.voxy.common.config.storage.rocksdb.RocksDBStorageBackend;
import me.cortex.voxy.common.world.WorldEngine;
import org.rocksdb.RocksDBException;

/**
 * Read-only measurement of a store. Safe to run against a live world.
 */
public final class StoreInspector {
    /** Levels are 0 .. MAX_LOD_LAYER inclusive. */
    public static final int LEVEL_COUNT = WorldEngine.MAX_LOD_LAYER + 1;

    private StoreInspector() {}

    /**
     * Counts stored sections per LOD level.
     *
     * <p>This is a full key scan per level, so it is not free -- callers should cache it
     * rather than calling it every frame. We iterate each level explicitly instead of
     * using the "all levels" sentinel, because that sentinel is a RocksDB implementation
     * detail rather than part of the {@code IStoredSectionPositionIterator} contract.
     */
    public static long[] countSections(WorldEngine engine) {
        long[] counts = new long[LEVEL_COUNT];
        for (int level = 0; level < LEVEL_COUNT; level++) {
            long[] counter = new long[1];
            engine.storage.iteratePositions(level, key -> counter[0]++);
            counts[level] = counter[0];
        }
        return counts;
    }

    public static StoreStats inspect(WorldEngine engine) {
        long[] counts = countSections(engine);

        StorageBackend backend = StoreAccess.backendOf(engine);
        RocksDBStorageBackend rocks = backend == null ? null : StoreAccess.rocksIn(backend);
        if (rocks == null) {
            return new StoreStats(counts, 0, 0, false);
        }

        long sst = longProperty(rocks, "rocksdb.total-sst-files-size");
        long live = longProperty(rocks, "rocksdb.estimate-live-data-size");
        if (sst < 0 || live < 0) {
            return new StoreStats(counts, 0, 0, false);
        }
        return new StoreStats(counts, sst, live, true);
    }

    /** Returns -1 if the property could not be read. */
    private static long longProperty(RocksDBStorageBackend rocks, String property) {
        var accessor = (RocksDBStorageBackendAccessor) rocks;
        try {
            String value = accessor.voxystats$getDb()
                    .getProperty(accessor.voxystats$getWorldSections(), property);
            return value == null ? -1 : Long.parseLong(value.trim());
        } catch (RocksDBException | NumberFormatException e) {
            VoxyStats.LOGGER.warn("Could not read RocksDB property {}", property, e);
            return -1;
        }
    }
}
