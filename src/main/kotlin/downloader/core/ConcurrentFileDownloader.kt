package downloader.core

import downloader.domain.DownloadState
import downloader.io.ConcurrentFileWriter
import downloader.network.NetworkClient
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import java.util.concurrent.CancellationException
import java.util.concurrent.atomic.AtomicLong

private val logger = KotlinLogging.logger {}

class ConcurrentFileDownloader(
    private val networkClient: NetworkClient,
    private val chunkCalculator: ChunkCalculator,
    private val fileWriter: ConcurrentFileWriter,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) {
    /**
     * Initiates the downloaded process and returns a hot Flow of state updates.
     */
    fun download(url: String, requestedThreads: Int): Flow<DownloadState> = channelFlow {
        send(DownloadState.Starting)
        logger.info { "Starting download for $url with $requestedThreads threads" }

        try {
            // Pre-flight Network Check
            val fileInfo = networkClient.fetchFileInfo(url)

            // Graceful degradation: If the server ignores our request for chunks,
            // fallback to a safe, single-threaded download to prevent file corruption.
            val actualThreads = if (fileInfo.supportsRanges) requestedThreads else 1

            // Pre-allocate Disk Space
            withContext(ioDispatcher) {
                fileWriter.allocate(fileInfo.sizeBytes)
            }
            logger.debug { "Allocated ${fileInfo.sizeBytes} bytes on disk" }

            // Mathematical Division
            val chunks = chunkCalculator.calculate(fileInfo.sizeBytes, actualThreads)
            val totalDownloadedBytes = AtomicLong(0L)

            // Concurrent Execution
            withContext(ioDispatcher) {
                // coroutineScope creates a structured boundary. If any child 'async' block throws an exception,
                // all other running chunks are cancelled safely.
                coroutineScope {
                    val deferredDownloads = chunks.map { chunk ->
                        async {
                            // Track the local write position for this specific thread
                            var currentWriteOffset = chunk.start

                            networkClient.streamChunk(url, chunk) { buffer, bytesRead ->
                                // Write incoming network buffer to disk
                                fileWriter.writeChunk(currentWriteOffset, buffer, bytesRead)
                                currentWriteOffset += bytesRead

                                // Update global progress safely across threads
                                val totalNow = totalDownloadedBytes.addAndGet(bytesRead.toLong())
                                val progress = ((totalNow.toDouble() / fileInfo.sizeBytes) * 100).toInt()

                                // Emit progress safely using channelFlow's send
                                send(
                                    DownloadState.Downloading(
                                        totalNow,
                                        fileInfo.sizeBytes,
                                        progress
                                    )
                                )
                            }
                        }
                    }

                    // Suspend and wait for all concurrent chunks to finish
                    deferredDownloads.awaitAll()
                }
            }

            send(DownloadState.Success("Successfully downloaded ${fileInfo.sizeBytes} bytes."))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Network timeouts, disk full errors, etc.
            send(DownloadState.Error(e))
        }


    }
}