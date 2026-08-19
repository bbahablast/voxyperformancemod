package me.bbahablast.voxystats;

import me.bbahablast.voxystats.command.VoxyStatsCommand;
import me.bbahablast.voxystats.hud.DebugEntryDefault;
import me.bbahablast.voxystats.hud.StatsDebugEntry;
import net.fabricmc.api.ClientModInitializer;

public class VoxyStatsClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        StatsDebugEntry.register();
        DebugEntryDefault.scheduleFirstRunEnable();
        VoxyStatsCommand.register();
        VoxyStats.LOGGER.info("Voxy Storage & Stats loaded");
    }
}
