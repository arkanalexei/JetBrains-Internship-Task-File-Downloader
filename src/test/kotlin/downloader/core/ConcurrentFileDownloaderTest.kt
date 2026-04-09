package downloader.core

import app.cash.turbine.test
import downloader.domain.ByteRange
import downloader.domain.DownloadState
import downloader.domain.FileInfo
import downloader.io.ConcurrentFileWriter
import downloader.network.NetworkClient
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.util.concurrent.CancellationException

@OptIn(ExperimentalCoroutinesApi::class)
class ConcurrentFileDownloaderTest {

    @Test
    fun `emits correct state sequence on successful download`() = runTest {
        // Mock the dependencies using MockK
        val mockNetwork = mockk<NetworkClient>()
        val mockMath = mockk<ChunkCalculator>()
        val mockDisk = mockk<ConcurrentFileWriter>(relaxed = true)

        coEvery { mockNetwork.fetchFileInfo(any()) } returns FileInfo(100L, true)
        every { mockMath.calculate(any(), any()) } returns listOf(ByteRange(0, 99))
        coEvery { mockNetwork.streamChunk(any(), any(), any()) } coAnswers {
            val callback = arg<suspend (ByteArray, Int) -> Unit>(2)
            // Simulate receiving 100 bytes over the network
            callback(ByteArray(100), 100)
        }

        // Use a TestDispatcher to run coroutines synchronously in tests
        val downloader = ConcurrentFileDownloader(
            mockNetwork, mockMath, mockDisk, UnconfinedTestDispatcher(testScheduler)
        )

        // Test the Flow emissions sequentially
        downloader.download("http://random-url.com", 1).test {
            assertTrue(awaitItem() is DownloadState.Starting)

            val downloadingState = awaitItem() as DownloadState.Downloading
            assertTrue(downloadingState.progressPercentage == 100)

            assertTrue(awaitItem() is DownloadState.Success)
            awaitComplete()
        }
    }

    @Test
    fun `emits Error state when network throws`() = runTest {
        val mockNetwork = mockk<NetworkClient>()
        val mockMath = mockk<ChunkCalculator>()
        val mockDisk = mockk<ConcurrentFileWriter>(relaxed = true)

        val networkException = RuntimeException("Connection timed out")
        coEvery { mockNetwork.fetchFileInfo(any()) } throws networkException
        every { mockMath.calculate(any(), any()) } returns listOf(ByteRange(0, 99))

        val downloader = ConcurrentFileDownloader(
            mockNetwork, mockMath, mockDisk, UnconfinedTestDispatcher(testScheduler)
        )

        downloader.download("http://random-url.com", 1).test {
            assertTrue(awaitItem() is DownloadState.Starting)

            val errorState = awaitItem() as DownloadState.Error
            assertEquals(networkException, errorState.exception)
            assertEquals("Connection timed out", errorState.exception.message)

            awaitComplete()
        }
    }

    @Test
    fun `CancellationException is rethrown and not swallowed as Error`() = runTest {
        val mockNetwork = mockk<NetworkClient>()
        val mockMath = mockk<ChunkCalculator>()
        val mockDisk = mockk<ConcurrentFileWriter>(relaxed = true)

        coEvery { mockNetwork.fetchFileInfo(any()) } throws CancellationException("Cancelled by caller")
        every { mockMath.calculate(any(), any()) } returns listOf(ByteRange(0, 99))

        val downloader = ConcurrentFileDownloader(
            mockNetwork, mockMath, mockDisk, UnconfinedTestDispatcher(testScheduler)
        )

        // The flow itself should be cancelled, not emit an Error state
        assertThrows<CancellationException> {
            downloader.download("http://random-url.com", 1).toList()
        }
    }
}