package me.bbahablast.voxystats.store;

import me.bbahablast.voxystats.mixin.SectionSerializationStorageAccessor;
import me.cortex.voxy.client.core.IVoxyRenderSystemHolder;
import me.cortex.voxy.common.config.section.SectionSerializationStorage;
import me.cortex.voxy.common.config.storage.StorageBackend;
import me.cortex.voxy.common.config.storage.rocksdb.RocksDBStorageBackend;
import me.cortex.voxy.common.world.WorldEngine;
import org.jetbrains.annotations.Nullable;

/**
 * Resolves the pieces of Voxy we operate on. Everything here goes through Voxy's own
 * public surface except {@link SectionSerializationStorageAccessor}.
 */
public final class StoreAccess {
    private StoreAccess() {}

    /** The engine for the world currently being rendered, or null if Voxy is not active. */
    public static @Nullable WorldEngine currentWorld() {
        var vrs = IVoxyRenderSystemHolder.getNullable();
        return vrs == null ? null : vrs.getEngine();
    }

    /**
     * The backend chain root for an engine, or null if the engine uses a section storage
     * we do not understand. Voxy's {@code SectionStorage} is abstract and only
     * {@link SectionSerializationStorage} owns a {@link StorageBackend}; a different
     * implementation is not an error, we just cannot inspect it.
     */
    public static @Nullable StorageBackend backendOf(WorldEngine engine) {
        if (engine.storage instanceof SectionSerializationStorage sss) {
            return ((SectionSerializationStorageAccessor) sss).voxystats$getBackend();
        }
        return null;
    }

    /**
     * The RocksDB backend somewhere in the chain, or null if this store is not backed by
     * RocksDB. The default config nests it under a compression adaptor, hence the walk.
     */
    public static @Nullable RocksDBStorageBackend rocksIn(StorageBackend root) {
        for (var backend : root.collectAllBackends()) {
            if (backend instanceof RocksDBStorageBackend rocks) {
                return rocks;
            }
        }
        return null;
    }
}
