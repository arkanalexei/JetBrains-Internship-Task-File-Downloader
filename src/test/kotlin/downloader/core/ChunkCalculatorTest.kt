package downloader.core

import downloader.domain.ByteRange
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class ChunkCalculatorTest {
    private val calculator = ChunkCalculator()

    @Test
    fun `single chunk when file smaller than chunk count`() {
        val result = calculator.calculate(totalBytes = 3, chunkCount = 10)
        assertEquals(1, result.size)
        assertEquals(ByteRange(0, 2), result[0])
    }

    @Test
    fun `single byte file produces one chunk`() {
        val result = calculator.calculate(totalBytes = 1, chunkCount = 4)
        assertEquals(1, result.size)
        assertEquals(ByteRange(0, 0), result[0])
    }

    @Test
    fun `chunks cover entire file with no gaps and no overlaps`() {
        val totalBytes = 1000L
        val result = calculator.calculate(totalBytes, chunkCount = 4)

        // Verify no gaps or overlaps by checking contiguity
        for (i in 0 until result.size - 1) {
            assertEquals(
                result[i].end + 1, result[i + 1].start,
                "Gap or overlap between chunk $i and ${i + 1}"
            )
        }
        // Verify full coverage
        assertEquals(0L, result.first().start)
        assertEquals(totalBytes - 1, result.last().end)
    }

    @Test
    fun `remainder bytes are absorbed into last chunk`() {
        // 10 bytes / 3 chunks = base 3, remainder 1
        // Expected: [0-2], [3-5], [6-9]
        val result = calculator.calculate(totalBytes = 10, chunkCount = 3)
        assertEquals(3, result.size)
        assertEquals(ByteRange(0, 2), result[0])
        assertEquals(ByteRange(3, 5), result[1])
        assertEquals(ByteRange(6, 9), result[2])
    }

    @Test
    fun `chunk sizes sum to total bytes`() {
        val totalBytes = 52_428_800L // 50MB
        val result = calculator.calculate(totalBytes, chunkCount = 7)
        assertEquals(totalBytes, result.sumOf { it.size })
    }

    @Test
    fun `throws when totalBytes is zero`() {
        assertThrows<IllegalArgumentException> {
            calculator.calculate(totalBytes = 0, chunkCount = 4)
        }
    }

    @Test
    fun `throws when chunkCount is zero`() {
        assertThrows<IllegalArgumentException> {
            calculator.calculate(totalBytes = 1000, chunkCount = 0)
        }
    }

}