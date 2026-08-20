package me.bbahablast.voxystats.store;

import me.bbahablast.voxystats.VoxyStats;
import me.bbahablast.voxystats.mixin.SectionSerializationStorageAccessor;
import me.cortex.voxy.client.core.VoxyRenderSystem;
import me.cortex.voxy.common.config.section.SectionSerializationStorage;
import me.cortex.voxy.common.config.storage.StorageBackend;
import me.cortex.voxy.common.config.storage.rocksdb.RocksDBStorageBackend;
import me.cortex.voxy.common.world.WorldEngine;
import net.minecraft.client.Minecraft;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Method;

/**
 * Resolves the pieces of Voxy we operate on. Everything here goes through Voxy's own
 * public surface except {@link SectionSerializationStorageAccessor}.
 */
public final class StoreAccess {
    private StoreAccess() {}

    private static volatile Method renderSystemAccessor;

    /** The engine for the world currently being rendered, or null if Voxy is not active. */
    public static @Nullable WorldEngine currentWorld() {
        var vrs = renderSystem();
        return vrs == null ? null : vrs.getEngine();
    }

    /**
     * Voxy mixes an interface into {@code LevelRenderer} to hang its render system off,
     * but renamed it between Minecraft versions -- {@code IGetVoxyRenderSystem} on 1.21.x,
     * {@code IVoxyRenderSystemHolder} on 26.x -- and the accessor method was renamed with
     * it. Binding to either name directly would make this jar refuse to load on the other
     * version, so we look the method up by its return type, which has not changed.
     */
    private static @Nullable VoxyRenderSystem renderSystem() {
        Object levelRenderer = Minecraft.getInstance().levelRenderer;
        if (levelRenderer == null) {
            return null;
        }
        Method accessor = renderSystemAccessor;
        if (accessor == null || !accessor.getDeclaringClass().isInstance(levelRenderer)) {
            accessor = findAccessor(levelRenderer);
            if (accessor == null) {
                return null;
            }
            renderSystemAccessor = accessor;
        }
        try {
            return (VoxyRenderSystem) accessor.invoke(levelRenderer);
        } catch (ReflectiveOperationException e) {
            VoxyStats.LOGGER.warn("Could not read Voxy's render system", e);
            return null;
        }
    }

    private static @Nullable Method findAccessor(Object levelRenderer) {
        for (Method method : levelRenderer.getClass().getMethods()) {
            if (method.getParameterCount() == 0 && method.getReturnType() == VoxyRenderSystem.class) {
                return method;
            }
        }
        VoxyStats.LOGGER.warn("No Voxy render system accessor on {}; is Voxy installed?",
                levelRenderer.getClass().getName());
        return null;
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
