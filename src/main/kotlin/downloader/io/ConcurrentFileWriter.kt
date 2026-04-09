package downloader.io

import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.channels.FileChannel

class ConcurrentFileWriter(private val destination: File) : AutoCloseable {
    private val randomAccessFile = RandomAccessFile(destination, "rw")
    private val channel: FileChannel = randomAccessFile.channel

    /**
     * Pre-allocates the file size on disk.
     * This prevents disk fragmentation and ensures we actually have enough space before downloading.
     */
    fun allocate(totalBytes: Long) {
        randomAccessFile.setLength(totalBytes)
    }

    /**
     * Writes a block of bytes to a specific physical offset in the file.
     * FileChannel.write(buffer, position) is thread-safe for concurrent writes to non-overlapping regions.
     */
    fun writeChunk(offset: Long, data: ByteArray, length: Int) {
        val buffer = ByteBuffer.wrap(data, 0, length)
        var currentOffset = offset

        // channel.write doesn't guarantee writing all bytes in one go,
        // so we loop until the buffer is drained
        while (buffer.hasRemaining()) {
            val bytesWritten = channel.write(buffer, currentOffset)
            currentOffset += bytesWritten
        }
    }

    override fun close() {
        channel.close()
        randomAccessFile.close()
    }
}