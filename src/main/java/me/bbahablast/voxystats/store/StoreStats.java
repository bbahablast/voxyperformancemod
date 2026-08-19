package me.bbahablast.voxystats.store;

/**
 * A snapshot of one world's LOD store.
 *
 * @param sectionsPerLevel stored section count indexed by LOD level, 0 .. MAX_LOD_LAYER
 * @param sstBytes         bytes occupying SST files on disk, including data that is
 *                         deleted but not yet compacted away. This is 0 for a young
 *                         store: RocksDB buffers writes in memtables and only creates
 *                         SSTs once one fills, so thousands of sections can exist with
 *                         no SST bytes at all.
 * @param unflushedBytes   bytes sitting in memtables, not yet written to any SST
 * @param liveBytes        estimated bytes of SST data still reachable. The gap between
 *                         this and {@code sstBytes} is what compaction can reclaim.
 * @param sizesAvailable   false when the backend is not RocksDB, in which case the byte
 *                         figures are 0 and must not be shown as if they were measured
 */
public record StoreStats(
        long[] sectionsPerLevel,
        long sstBytes,
        long unflushedBytes,
        long liveBytes,
        boolean sizesAvailable
) {
    public long totalSections() {
        long total = 0;
        for (long count : this.sectionsPerLevel) {
            total += count;
        }
        return total;
    }

    /** Everything the store is holding, flushed or not. */
    public long totalBytes() {
        return this.sstBytes + this.unflushedBytes;
    }

    /** Bytes that compaction would hand back to the filesystem. */
    public long reclaimableBytes() {
        return Math.max(0, this.sstBytes - this.liveBytes);
    }
}
