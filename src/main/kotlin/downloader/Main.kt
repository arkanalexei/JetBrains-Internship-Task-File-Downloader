package downloader

import downloader.core.ChunkCalculator
import downloader.core.ConcurrentFileDownloader
import downloader.domain.DownloadState
import downloader.io.ConcurrentFileWriter
import downloader.network.NetworkClient
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.runBlocking
import java.io.File

private val logger = KotlinLogging.logger {}

fun main(args: Array<String>) = runBlocking {
    val url = args.getOrElse(0) { "http://localhost:8080/my-local-file.txt" }
    val destination = File(args.getOrElse(1) { "data/downloaded-file.txt" })
    val threads = args.getOrElse(2) { "4" }.toInt()

    NetworkClient().use { networkClient ->
        ConcurrentFileWriter(destination).use { fileWriter ->

            val downloader = ConcurrentFileDownloader(
                networkClient = networkClient,
                chunkCalculator = ChunkCalculator(),
                fileWriter = fileWriter
            )

            downloader.download(url, threads).collect { state ->
                when (state) {
                    is DownloadState.Starting -> logger.info { "Initializing download..." }
                    is DownloadState.Downloading -> logger.debug {
                        "Progress: ${state.progressPercentage}% (${state.bytesDownloaded}/${state.totalBytes})"
                    }

                    is DownloadState.Success -> logger.info { "Done: ${state.fileDetails}" }
                    is DownloadState.Error -> logger.error(state.exception) { "Failed: ${state.exception.message}" }
                }
            }
        }
    }
}