package me.bbahablast.voxystats.command;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import me.bbahablast.voxystats.VoxyStats;
import me.bbahablast.voxystats.store.StoreAccess;
import me.bbahablast.voxystats.store.StoreInspector;
import me.bbahablast.voxystats.store.StoreLocator;
import me.bbahablast.voxystats.store.StorePruner;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;

import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommands.argument;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommands.literal;

public final class VoxyStatsCommand {
    /** Listing indices refer to this, so delete cannot act on a stale ordering. */
    private static List<StoreLocator.StoreEntry> lastListing = List.of();

    private VoxyStatsCommand() {}

    public static void register() {
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, access) -> dispatcher.register(
                literal("voxystats")
                        .then(literal("stats").executes(ctx -> stats(ctx.getSource())))
                        .then(literal("list").executes(ctx -> list(ctx.getSource())))
                        .then(literal("compact").executes(ctx -> compact(ctx.getSource())))
                        .then(literal("delete")
                                .then(argument("index", IntegerArgumentType.integer(1))
                                        .executes(ctx -> deleteHint(ctx.getSource()))
                                        .then(literal("confirm").executes(ctx -> delete(
                                                ctx.getSource(),
                                                IntegerArgumentType.getInteger(ctx, "index"))))))
        ));
    }

    private static int stats(FabricClientCommandSource source) {
        var engine = StoreAccess.currentWorld();
        if (engine == null) {
            return error(source, "Voxy has no active world.");
        }
        var stats = StoreInspector.inspect(engine);
        source.sendFeedback(Component.literal("Sections: " + stats.totalSections()));
        if (stats.sizesAvailable()) {
            source.sendFeedback(Component.literal(
                    "Total " + bytes(stats.totalBytes())
                            + " (" + bytes(stats.sstBytes()) + " in SSTs, "
                            + bytes(stats.unflushedBytes()) + " unflushed)"));
            source.sendFeedback(Component.literal(
                    "Reclaimable by compaction: " + bytes(stats.reclaimableBytes())));
        } else {
            source.sendFeedback(Component.literal("Sizes unavailable (backend is not RocksDB)"));
        }
        return 1;
    }

    private static int list(FabricClientCommandSource source) {
        var entries = StoreLocator.findAll();
        lastListing = entries;
        if (entries.isEmpty()) {
            return error(source, "No Voxy stores found on disk.");
        }
        Path active = StoreLocator.activeBasePath();
        source.sendFeedback(Component.literal(entries.size() + " store(s):").withStyle(ChatFormatting.BOLD));
        for (int i = 0; i < entries.size(); i++) {
            var entry = entries.get(i);
            boolean inUse = active != null && entry.directory().startsWith(active);
            source.sendFeedback(Component.literal(
                    (i + 1) + ". " + entry.label() + "  " + bytes(entry.bytes())
                            + "  " + age(entry.lastModified())
                            + (inUse ? "  [in use]" : ""))
                    .withStyle(inUse ? ChatFormatting.GRAY : ChatFormatting.WHITE));
        }
        return 1;
    }

    private static int compact(FabricClientCommandSource source) {
        var engine = StoreAccess.currentWorld();
        if (engine == null) {
            return error(source, "Voxy has no active world.");
        }
        var backend = StoreAccess.backendOf(engine);
        var rocks = backend == null ? null : StoreAccess.rocksIn(backend);
        if (rocks == null) {
            return error(source, "This world's backend is not RocksDB; nothing to compact.");
        }

        long before = StoreInspector.totalBytes(rocks);
        source.sendFeedback(Component.literal("Compacting; the game may stutter."));
        try {
            StorePruner.compact(rocks);
        } catch (RuntimeException e) {
            VoxyStats.LOGGER.error("Compaction failed", e);
            return error(source, "Compaction failed: " + e.getMessage());
        }
        long after = StoreInspector.totalBytes(rocks);
        if (before < 0 || after < 0) {
            source.sendFeedback(Component.literal("Compacted; size could not be measured."));
        } else {
            source.sendFeedback(Component.literal("Compacted. " + bytes(Math.max(0, before - after)) + " freed.")
                    .withStyle(ChatFormatting.GREEN));
        }
        return 1;
    }

    private static int deleteHint(FabricClientCommandSource source) {
        source.sendFeedback(Component.literal("Add 'confirm' to actually delete. This cannot be undone.")
                .withStyle(ChatFormatting.YELLOW));
        return 1;
    }

    private static int delete(FabricClientCommandSource source, int index) {
        if (index > lastListing.size()) {
            return error(source, "No such store. Run /voxystats list first.");
        }
        var entry = lastListing.get(index - 1);

        // Deleting files underneath an open RocksDB corrupts it. The active base path is
        // the one Voxy currently has handles on, so refuse anything inside it.
        Path active = StoreLocator.activeBasePath();
        if (active != null && entry.directory().startsWith(active)) {
            return error(source, "That store is in use. Leave the world first.");
        }

        try {
            StoreLocator.delete(entry.directory());
        } catch (Exception e) {
            VoxyStats.LOGGER.error("Could not delete {}", entry.directory(), e);
            return error(source, "Delete failed: " + e.getMessage());
        }
        source.sendFeedback(Component.literal("Deleted " + entry.label() + ", freeing " + bytes(entry.bytes()))
                .withStyle(ChatFormatting.GREEN));
        lastListing = List.of();
        return 1;
    }

    private static int error(FabricClientCommandSource source, String message) {
        source.sendError(Component.literal(message));
        return 0;
    }

    private static String age(long epochMillis) {
        if (epochMillis <= 0) {
            return "unknown";
        }
        long days = Duration.between(Instant.ofEpochMilli(epochMillis), Instant.now()).toDays();
        if (days <= 0) {
            return "today";
        }
        return days + "d ago";
    }

    private static String bytes(long value) {
        if (value < 0) {
            return "?";
        }
        if (value < 1024) {
            return value + " B";
        }
        String[] units = {"KiB", "MiB", "GiB", "TiB"};
        double scaled = value;
        int unit = -1;
        while (scaled >= 1024 && unit < units.length - 1) {
            scaled /= 1024;
            unit++;
        }
        return String.format("%.1f %s", scaled, units[unit]);
    }
}
