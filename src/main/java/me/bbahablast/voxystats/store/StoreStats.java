package me.bbahablast.voxystats.store;

/**
 * A snapshot of one world's LOD store.
 *
 * @param sectionsPerLevel stored section count indexed by LOD level, 0 .. MAX_LOD_LAYER
 * @param sstBytes         bytes the backend currently occupies on disk, including data
 *                         that is deleted but not yet compacted away
 * @param liveBytes        estimated bytes of data still reachable. The gap between this
 *                         and {@code sstBytes} is reclaimable by compaction.
 * @param sizesAvailable   false when the backend is not RocksDB, in which case both byte
 *                         figures are 0 and must not be shown as if they were measured
 */
public record StoreStats(long[] sectionsPerLevel, long sstBytes, long liveBytes, boolean sizesAvailable) {
    public long totalSections() {
        long total = 0;
        for (long count : this.sectionsPerLevel) {
            total += count;
        }
        return total;
    }

    /** Bytes that compaction would hand back to the filesystem. */
    public long reclaimableBytes() {
        return Math.max(0, this.sstBytes - this.liveBytes);
    }
}
