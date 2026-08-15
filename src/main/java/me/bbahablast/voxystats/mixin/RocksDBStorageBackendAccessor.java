package me.bbahablast.voxystats.mixin;

import me.cortex.voxy.common.config.storage.rocksdb.RocksDBStorageBackend;
import org.rocksdb.ColumnFamilyHandle;
import org.rocksdb.RocksDB;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Voxy's default backend is RocksDB. Two things we need are not reachable through
 * {@link me.cortex.voxy.common.config.storage.StorageBackend}:
 *
 * <ul>
 *   <li>Cheap size accounting. Measuring by reading every section is O(store); RocksDB
 *       can answer from its own properties in constant time.</li>
 *   <li>Compaction. {@code deleteSectionData} issues a RocksDB delete, which writes a
 *       tombstone -- the space is not returned to the filesystem until the affected
 *       SSTs are compacted. Voxy's {@code flush()} only calls {@code flushWal(true)}
 *       and nothing in the backend ever calls {@code compactRange}. Without this
 *       accessor, a "prune" would delete data and free no disk at all.</li>
 * </ul>
 */
@Mixin(RocksDBStorageBackend.class)
public interface RocksDBStorageBackendAccessor {
    @Accessor("db")
    RocksDB voxystats$getDb();

    @Accessor("worldSections")
    ColumnFamilyHandle voxystats$getWorldSections();
}
