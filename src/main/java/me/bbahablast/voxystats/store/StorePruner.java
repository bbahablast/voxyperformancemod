package me.bbahablast.voxystats.store;

import it.unimi.dsi.fastutil.longs.LongArrayList;
import me.bbahablast.voxystats.VoxyStats;
import me.bbahablast.voxystats.mixin.RocksDBStorageBackendAccessor;
import me.cortex.voxy.common.config.storage.StorageBackend;
import me.cortex.voxy.common.config.storage.rocksdb.RocksDBStorageBackend;
import me.cortex.voxy.common.world.WorldEngine;
import org.rocksdb.RocksDBException;

import java.util.function.LongPredicate;

/**
 * Deletes stored sections and reclaims the disk they occupied.
 *
 * <p>Deletion alone does not free space. Voxy's {@code deleteSectionData} issues a
 * RocksDB delete, which writes a tombstone; the bytes stay in their SST files until
 * those files are compacted, and nothing in Voxy ever compacts. So a prune that skips
 * {@link #compact} will report thousands of deleted sections and give back no disk,
 * which is worse than doing nothing.
 */
public final class StorePruner {
    private StorePruner() {}

    /**
     * @param engine   the world to prune
     * @param selector receives each stored section key; return true to delete it. Keys
     *                 decode via {@code WorldEngine.getLevel/getX/getY/getZ}.
     * @param compact  whether to compact afterwards. Only pass false if you intend to
     *                 batch several prunes and compact once at the end.
     */
    public static PruneResult prune(WorldEngine engine, LongPredicate selector, boolean compact) {
        if (engine == StoreAccess.currentWorld()) {
            // The active section tracker caches WorldSection objects in memory. Deleting
            // rows out from under it leaves the renderer showing terrain that no longer
            // exists on disk, and the next save can resurrect it.
            throw new IllegalStateException("Refusing to prune the world that is currently loaded");
        }

        StorageBackend backend = StoreAccess.backendOf(engine);
        if (backend == null) {
            throw new IllegalStateException("This world's section storage exposes no backend to prune");
        }

        var doomed = new LongArrayList();
        for (int level = 0; level < StoreInspector.LEVEL_COUNT; level++) {
            engine.storage.iteratePositions(level, key -> {
                if (selector.test(key)) {
                    doomed.add(key);
                }
            });
        }

        RocksDBStorageBackend rocks = StoreAccess.rocksIn(backend);
        long before = rocks == null ? -1 : diskBytes(rocks);

        // Collect first, delete second. Deleting during iteration mutates the structure
        // the iterator is walking.
        for (long key : doomed) {
            backend.deleteSectionData(key);
        }
        backend.flush();

        if (compact && rocks != null) {
            compact(rocks);
        }

        long after = rocks == null ? -1 : diskBytes(rocks);
        VoxyStats.LOGGER.info("Pruned {} sections, disk {} -> {} bytes", doomed.size(), before, after);
        return new PruneResult(doomed.size(), before, after);
    }

    /** Forces RocksDB to rewrite its SSTs, actually returning deleted space to the filesystem. */
    public static void compact(RocksDBStorageBackend rocks) {
        var accessor = (RocksDBStorageBackendAccessor) rocks;
        try {
            accessor.voxystats$getDb().compactRange(accessor.voxystats$getWorldSections());
        } catch (RocksDBException e) {
            throw new RuntimeException("Compaction failed; space was not reclaimed", e);
        }
    }

    /**
     * Counts memtables as well as SSTs. Measuring SSTs alone would report a store that
     * has not flushed yet as 0 bytes, making any prune look like it freed nothing.
     */
    private static long diskBytes(RocksDBStorageBackend rocks) {
        return StoreInspector.totalBytes(rocks);
    }

    /**
     * @param bytesBefore -1 when the backend cannot report its size
     * @param bytesAfter  -1 when the backend cannot report its size
     */
    public record PruneResult(int sectionsDeleted, long bytesBefore, long bytesAfter) {
        public long bytesFreed() {
            if (this.bytesBefore < 0 || this.bytesAfter < 0) {
                return -1;
            }
            return Math.max(0, this.bytesBefore - this.bytesAfter);
        }
    }
}
