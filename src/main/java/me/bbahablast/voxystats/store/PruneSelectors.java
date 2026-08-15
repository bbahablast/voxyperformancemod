package me.bbahablast.voxystats.store;

import me.cortex.voxy.common.world.WorldEngine;

import java.util.function.LongPredicate;

/**
 * Selectors for {@link StorePruner#prune}. Section keys pack level and position; Voxy's
 * static decoders are the only supported way to read them.
 */
public final class PruneSelectors {
    private PruneSelectors() {}

    /** Everything. */
    public static LongPredicate all() {
        return key -> true;
    }

    /** Only sections at one LOD level. Level 0 is the finest and by far the largest. */
    public static LongPredicate atLevel(int level) {
        return key -> WorldEngine.getLevel(key) == level;
    }

    /**
     * Sections whose horizontal distance from a centre exceeds {@code radius}.
     *
     * <p>Section coordinates are per level -- a level 1 section spans twice the blocks of
     * a level 0 one -- so the radius is scaled per level to stay a consistent distance in
     * the world rather than in sections.
     *
     * @param centreX centre in level 0 section coordinates
     * @param centreZ centre in level 0 section coordinates
     * @param radius  radius in level 0 sections
     */
    public static LongPredicate beyondRadius(int centreX, int centreZ, int radius) {
        return key -> {
            int level = WorldEngine.getLevel(key);
            int scaledRadius = radius >> level;
            int dx = WorldEngine.getX(key) - (centreX >> level);
            int dz = WorldEngine.getZ(key) - (centreZ >> level);
            return ((long) dx * dx) + ((long) dz * dz) > ((long) scaledRadius * scaledRadius);
        };
    }
}
