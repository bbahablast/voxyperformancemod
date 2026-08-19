package me.bbahablast.voxystats.store;

import me.bbahablast.voxystats.VoxyStats;
import me.cortex.voxy.client.VoxyClientInstance;
import me.cortex.voxy.commonImpl.VoxyCommon;
import net.minecraft.client.Minecraft;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

/**
 * Finds Voxy stores on disk, including worlds that are not loaded.
 *
 * <p>Voxy lays them out in two places, per {@code VoxyClientInstance.getBasePath}:
 * singleplayer stores live under {@code <save>/voxy}, and multiplayer under
 * {@code <gamedir>/.voxy/saves/<server address>}. Below either root, each world gets a
 * directory named by a 32 character hash of its seed and dimension key, containing
 * {@code storage/}.
 *
 * <p>The hash is not reversible, so a store cannot be labelled with its dimension. The
 * server address above it is readable though, which is the part that matters when
 * deciding what to delete.
 */
public final class StoreLocator {
    private StoreLocator() {}

    /**
     * @param label        what to show the user
     * @param directory    the world store directory
     * @param bytes        total size on disk
     * @param lastModified epoch millis of the most recently touched file
     */
    public record StoreEntry(String label, Path directory, long bytes, long lastModified) {}

    /** The base path Voxy is writing to right now, or null if it is not in a world. */
    public static @Nullable Path activeBasePath() {
        if (!VoxyCommon.isAvailable()) {
            return null;
        }
        if (VoxyCommon.getInstance() instanceof VoxyClientInstance client) {
            return client.getStorageBasePath();
        }
        return null;
    }

    /** Every store we can find, largest first. */
    public static List<StoreEntry> findAll() {
        var entries = new ArrayList<StoreEntry>();
        for (Path root : roots()) {
            collect(root, entries);
        }
        entries.sort(Comparator.comparingLong(StoreEntry::bytes).reversed());
        return entries;
    }

    private static List<Path> roots() {
        var roots = new ArrayList<Path>();
        Path multiplayer = Minecraft.getInstance().gameDirectory.toPath().resolve(".voxy").resolve("saves");
        if (Files.isDirectory(multiplayer)) {
            roots.add(multiplayer);
        }
        // Singleplayer stores sit inside each save, so scan the saves folder rather than
        // only the world that happens to be open.
        Path saves = Minecraft.getInstance().gameDirectory.toPath().resolve("saves");
        if (Files.isDirectory(saves)) {
            try (var stream = Files.list(saves)) {
                stream.map(save -> save.resolve("voxy"))
                        .filter(Files::isDirectory)
                        .forEach(roots::add);
            } catch (IOException e) {
                VoxyStats.LOGGER.warn("Could not scan saves directory", e);
            }
        }
        return roots;
    }

    /**
     * Walks a root looking for directories that contain a {@code storage/} child, which
     * is what marks a world store. Depth is bounded because the multiplayer root nests
     * one level deeper (server address) than the singleplayer one.
     */
    private static void collect(Path root, List<StoreEntry> into) {
        try (Stream<Path> stream = Files.walk(root, 3)) {
            stream.filter(Files::isDirectory)
                    .filter(dir -> Files.isDirectory(dir.resolve("storage")))
                    .forEach(dir -> into.add(describe(root, dir)));
        } catch (IOException e) {
            VoxyStats.LOGGER.warn("Could not scan {}", root, e);
        }
    }

    private static StoreEntry describe(Path root, Path dir) {
        long bytes = 0;
        long newest = 0;
        try (Stream<Path> stream = Files.walk(dir)) {
            for (Path file : (Iterable<Path>) stream.filter(Files::isRegularFile)::iterator) {
                try {
                    bytes += Files.size(file);
                    newest = Math.max(newest, Files.getLastModifiedTime(file).toMillis());
                } catch (IOException ignored) {
                    // A file vanishing mid-walk is normal for a store being written to.
                }
            }
        } catch (IOException e) {
            VoxyStats.LOGGER.warn("Could not size {}", dir, e);
        }
        return new StoreEntry(label(root, dir), dir, bytes, newest);
    }

    private static String label(Path root, Path dir) {
        Path relative = root.relativize(dir);
        // Show the readable part (server address, or save name) plus a short hash, rather
        // than a 32 character hash on its own.
        String tail = dir.getFileName().toString();
        String shortTail = tail.length() > 8 ? tail.substring(0, 8) : tail;
        Path parent = relative.getParent();
        String prefix = parent == null ? root.getParent().getFileName().toString() : parent.toString();
        return prefix + "/" + shortTail;
    }

    /** Recursively deletes a store directory. */
    public static void delete(Path directory) throws IOException {
        try (Stream<Path> stream = Files.walk(directory)) {
            for (Path path : (Iterable<Path>) stream.sorted(Comparator.reverseOrder())::iterator) {
                Files.delete(path);
            }
        }
    }
}
