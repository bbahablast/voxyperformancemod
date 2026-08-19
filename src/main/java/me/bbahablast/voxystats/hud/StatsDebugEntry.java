package me.bbahablast.voxystats.hud;

import me.bbahablast.voxystats.VoxyStats;
import me.bbahablast.voxystats.store.StoreAccess;
import me.bbahablast.voxystats.store.StoreInspector;
import me.bbahablast.voxystats.store.StoreStats;
import me.cortex.voxy.common.world.WorldEngine;
import net.minecraft.client.gui.components.debug.DebugScreenDisplayer;
import net.minecraft.client.gui.components.debug.DebugScreenEntries;
import net.minecraft.client.gui.components.debug.DebugScreenEntry;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.LevelChunk;

import java.util.ArrayList;
import java.util.List;

/**
 * Adds a store section to the F3 screen, next to Voxy's own entries.
 */
public class StatsDebugEntry implements DebugScreenEntry {
    public static final Identifier ID = Identifier.fromNamespaceAndPath(VoxyStats.MOD_ID, "store");

    /**
     * Counting sections is a full key scan, so it cannot run per frame. Everything on
     * this overlay comes from one cached snapshot.
     */
    private static final long REFRESH_INTERVAL_MS = 5000;

    private StoreStats cached;
    private long cachedAt;
    private WorldEngine cachedFor;

    public static void register() {
        DebugScreenEntries.register(ID, new StatsDebugEntry());
    }

    @Override
    public void display(DebugScreenDisplayer displayer, Level level, LevelChunk clientChunk, LevelChunk chunk) {
        var engine = StoreAccess.currentWorld();
        if (engine == null) {
            return;
        }

        var stats = this.snapshot(engine);
        var lines = new ArrayList<String>();

        if (stats.sizesAvailable()) {
            // SSTs and memtables are reported separately on purpose. A young store has
            // thousands of sections and zero SST bytes, because RocksDB has not filled a
            // memtable yet -- showing only the SST figure reads as "nothing is stored".
            lines.add(String.format("Store: %s total (%s in SSTs, %s unflushed)",
                    formatBytes(stats.totalBytes()),
                    formatBytes(stats.sstBytes()),
                    formatBytes(stats.unflushedBytes())));
            lines.add(String.format("Live: %s, reclaimable by compaction: %s",
                    formatBytes(stats.liveBytes()),
                    formatBytes(stats.reclaimableBytes())));
        } else {
            lines.add("Store: size unavailable (backend is not RocksDB)");
        }

        lines.add("Sections: " + stats.totalSections() + " total " + perLevel(stats));
        displayer.addToGroup(ID, lines);
    }

    private StoreStats snapshot(WorldEngine engine) {
        long now = System.currentTimeMillis();
        if (this.cached == null || engine != this.cachedFor || now - this.cachedAt > REFRESH_INTERVAL_MS) {
            this.cached = StoreInspector.inspect(engine);
            this.cachedAt = now;
            this.cachedFor = engine;
        }
        return this.cached;
    }

    private static String perLevel(StoreStats stats) {
        List<String> parts = new ArrayList<>();
        long[] counts = stats.sectionsPerLevel();
        for (int level = 0; level < counts.length; level++) {
            parts.add("L" + level + ":" + counts[level]);
        }
        return "[" + String.join(" ", parts) + "]";
    }

    private static String formatBytes(long bytes) {
        if (bytes < 1024) {
            return bytes + " B";
        }
        String[] units = {"KiB", "MiB", "GiB", "TiB"};
        double value = bytes;
        int unit = -1;
        while (value >= 1024 && unit < units.length - 1) {
            value /= 1024;
            unit++;
        }
        return String.format("%.1f %s", value, units[unit]);
    }
}
