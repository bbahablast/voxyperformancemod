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
import java.util.concurrent.atomic.AtomicBoolean;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommands.argument;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommands.literal;

public final class VoxyStatsCommand {
    /** Listing indices refer to this, so delete cannot act on a stale ordering. */
    private static List<StoreLocator.StoreEntry> lastListing = List.of();

    /** Guards against a second compaction starting while one is still running. */
    private static final AtomicBoolean compacting = new AtomicBoolean();

    private VoxyStatsCommand() {}

    public static void register() {
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, access) -> dispatcher.register(
                literal("voxystats")
                        .then(literal("stats").executes(ctx -> stats(ctx.getSource())))
                        .then(literal("list").executes(ctx -> list(ctx.getSource())))
                        .then(literal("compact").executes(ctx -> compact(ctx.getSource())))
                        .then(literal("delete")
                                // Without this, bare "/voxystats delete" is a raw Brigadier
                                // parse error that says nothing about what is missing.
                                .executes(ctx -> deleteUsage(ctx.getSource()))
                                .then(argument("index", IntegerArgumentType.integer(1))
                                        .executes(ctx -> deleteHint(
                                                ctx.getSource(),
                                                IntegerArgumentType.getInteger(ctx, "index")))
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

    /**
     * Compaction runs on its own thread. {@code compactRange} is synchronous and client
     * commands execute on the render thread, so doing it inline freezes the game for as
     * long as the rewrite takes -- seconds on a small store, minutes on a multi-gigabyte
     * one, long enough that the OS offers to kill Minecraft.
     */
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
        if (!compacting.compareAndSet(false, true)) {
            return error(source, "A compaction is already running.");
        }

        // Pin the world for the duration. Voxy frees idle worlds on a timer, and
        // compacting a RocksDB handle that has been closed underneath us is a native
        // crash, not an exception. isWorldUsed() honours this count, so the world cannot
        // be freed while we hold it.
        engine.acquireRef();

        var client = source.getClient();
        long before = StoreInspector.totalBytes(rocks);
        source.sendFeedback(Component.literal("Compacting in the background; this may take a while."));

        Thread worker = new Thread(() -> {
            String message;
            boolean ok = true;
            try {
                StorePruner.compact(rocks);
                long after = StoreInspector.totalBytes(rocks);
                message = (before < 0 || after < 0)
                        ? "Compacted; size could not be measured."
                        : "Compacted. " + bytes(Math.max(0, before - after)) + " freed.";
            } catch (Throwable e) {
                VoxyStats.LOGGER.error("Compaction failed", e);
                message = "Compaction failed: " + e;
                ok = false;
            } finally {
                engine.releaseRef();
                compacting.set(false);
            }
            // Chat must be touched from the client thread.
            String finalMessage = message;
            boolean finalOk = ok;
            client.execute(() -> source.sendFeedback(
                    Component.literal(finalMessage)
                            .withStyle(finalOk ? ChatFormatting.GREEN : ChatFormatting.RED)));
        }, "voxystats-compaction");
        worker.setDaemon(true);
        worker.start();
        return 1;
    }

    private static int deleteUsage(FabricClientCommandSource source) {
        source.sendFeedback(Component.literal("Usage: /voxystats delete <number> confirm")
                .withStyle(ChatFormatting.YELLOW));
        source.sendFeedback(Component.literal("The number comes from /voxystats list."));
        return list(source);
    }

    private static int deleteHint(FabricClientCommandSource source, int index) {
        if (index > lastListing.size()) {
            return error(source, "No such store. Run /voxystats list first.");
        }
        var entry = lastListing.get(index - 1);
        source.sendFeedback(Component.literal(
                "Would delete " + entry.label() + " (" + bytes(entry.bytes()) + ").")
                .withStyle(ChatFormatting.YELLOW));
        source.sendFeedback(Component.literal(
                "Add 'confirm' to go ahead. This cannot be undone."));
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
            // Client commands only run inside a world, so "leave the world" alone is not
            // actionable -- you have to be somewhere else, not nowhere.
            return error(source, "That store belongs to the world you are in. "
                    + "Join a different world or server, then run this again.");
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
