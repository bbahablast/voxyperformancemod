package me.bbahablast.voxystats.hud;

import me.bbahablast.voxystats.VoxyStats;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.debug.DebugScreenEntryStatus;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Turns our F3 group on the first time the mod runs.
 *
 * <p>Registering a debug screen entry is not enough to make it visible.
 * {@code DebugScreenEntryList.getStatus} falls back to
 * {@link DebugScreenEntryStatus#NEVER} for any id it has no stored status for, and a
 * modded id is never in a vanilla debug profile -- so a freshly registered entry renders
 * nothing at all and looks like a broken mod.
 *
 * <p>We only do this once, tracked by a marker file. Forcing the status on every launch
 * would override the user if they later switch it off.
 */
public final class DebugEntryDefault {
    private static final Path MARKER = FabricLoader.getInstance()
            .getConfigDir()
            .resolve("voxystats-debug-entry-initialised");

    private static boolean done;

    private DebugEntryDefault() {}

    public static void scheduleFirstRunEnable() {
        if (Files.exists(MARKER)) {
            return;
        }
        // Minecraft is still being constructed during client init, so debugEntries is not
        // safe to touch yet. First tick is.
        ClientTickEvents.END_CLIENT_TICK.register(DebugEntryDefault::onTick);
    }

    private static void onTick(Minecraft client) {
        if (done) {
            return;
        }
        done = true;
        try {
            client.debugEntries.setStatus(StatsDebugEntry.ID, DebugScreenEntryStatus.IN_OVERLAY);
            client.debugEntries.rebuildCurrentList();
            client.debugEntries.save();
            Files.createDirectories(MARKER.getParent());
            Files.writeString(MARKER, "Delete this file to re-enable the Voxy store F3 group.\n");
            VoxyStats.LOGGER.info("Enabled the Voxy store debug group (first run)");
        } catch (Exception e) {
            VoxyStats.LOGGER.warn("Could not enable the debug group; turn it on in the F3 options", e);
        }
    }
}
