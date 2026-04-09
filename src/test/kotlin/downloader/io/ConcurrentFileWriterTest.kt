package downloader.io

import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.nio.file.Path

class ConcurrentFileWriterTest {

    @TempDir
    lateinit var tempDir: Path

    private fun tempFile() = File(tempDir.toFile(), "test-output.bin")

    @Test
    fun `allocate sets file length on disk`() {
        val file = tempFile()
        ConcurrentFileWriter(file).use { writer ->
            writer.allocate(1024)
        }
        assertEquals(1024, file.length())
    }

    @Test
    fun `writeChunk writes bytes at correct offset`() {
        val file = tempFile()
        val data = "Hello".toByteArray()

        ConcurrentFileWriter(file).use { writer ->
            writer.allocate(10)
            writer.writeChunk(offset = 5, data = data, length = data.size)
        }

        val written = file.readBytes()
        // First 5 bytes should be zeroes (pre-allocated)
        assertArrayEquals(ByteArray(5), written.slice(0..4).toByteArray())
        // Next 5 bytes should be "Hello"
        assertArrayEquals(data, written.slice(5..9).toByteArray())
    }

    @Test
    fun `concurrent writes to non-overlapping regions produce correct file`() {
        val file = tempFile()
        val chunk1 = "AAAAA".toByteArray()
        val chunk2 = "BBBBB".toByteArray()

        ConcurrentFileWriter(file).use { writer ->
            writer.allocate(10)
            // Simulate two threads writing simultaneously
            val t1 = Thread { writer.writeChunk(0, chunk1, chunk1.size) }
            val t2 = Thread { writer.writeChunk(5, chunk2, chunk2.size) }
            t1.start(); t2.start()
            t1.join(); t2.join()
        }

        val result = file.readString()
        assertEquals("AAAAABBBBB", result)
    }

    @Test
    fun `writeChunk at offset zero writes from start of file`() {
        val file = tempFile()
        val data = "JetBrains".toByteArray()

        ConcurrentFileWriter(file).use { writer ->
            writer.allocate(data.size.toLong())
            writer.writeChunk(0, data, data.size)
        }

        assertArrayEquals(data, file.readBytes())
    }
}

private fun File.readString() = readBytes().toString(Charsets.UTF_8)