package downloader.core

import downloader.domain.ByteRange

class ChunkCalculator {
    /**
     * Divides a total file size into distinct byte ranges for parallel downloading.
     */
    fun calculate(totalBytes: Long, chunkCount: Int): List<ByteRange> {
        require(totalBytes > 0) { "Total bytes must be greater than 0" }
        require(chunkCount > 0) { "Chunk count must be greater than 0" }

        // If the file is smaller than the requested chunk count, just use 1 chunk.
        if (totalBytes < chunkCount) {
            return listOf(ByteRange(0, totalBytes - 1))
        }

        val baseChunkSize = totalBytes / chunkCount
        val remainder = totalBytes % chunkCount

        return (0 until chunkCount).map { index ->
            val start = index * baseChunkSize
            val end = if (index == chunkCount - 1) {
                (start + baseChunkSize + remainder) - 1
            } else {
                (start + baseChunkSize) - 1
            }
            ByteRange(start, end)
        }
    }
}