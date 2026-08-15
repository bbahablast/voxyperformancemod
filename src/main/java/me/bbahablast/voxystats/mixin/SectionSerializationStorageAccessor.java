package me.bbahablast.voxystats.mixin;

import me.cortex.voxy.common.config.section.SectionSerializationStorage;
import me.cortex.voxy.common.config.storage.StorageBackend;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Voxy exposes {@code WorldEngine.storage} publicly, but that is a
 * {@link me.cortex.voxy.common.config.section.SectionStorage}, which only offers
 * section load/save and position iteration. Byte sizes and deletion live one layer
 * down on the {@link StorageBackend}, held in a non-public field.
 *
 * <p>This single field accessor is the whole of our mixin surface into Voxy.
 */
@Mixin(SectionSerializationStorage.class)
public interface SectionSerializationStorageAccessor {
    @Accessor("backend")
    StorageBackend voxystats$getBackend();
}
