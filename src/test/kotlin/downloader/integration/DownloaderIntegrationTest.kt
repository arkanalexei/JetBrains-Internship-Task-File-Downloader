// src/test/kotlin/downloader/integration/DownloaderIntegrationTest.kt
package downloader.integration

import downloader.core.ChunkCalculator
import downloader.core.ConcurrentFileDownloader
import downloader.domain.DownloadState
import downloader.io.ConcurrentFileWriter
import downloader.network.NetworkClient
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.partialcontent.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import java.io.File

class DownloaderIntegrationTest {

    companion object {
        private lateinit var server: EmbeddedServer<*, *>
        private const val PORT = 8081

        @BeforeAll
        @JvmStatic
        fun setupServer() {
            // Spin up a real local Ktor server to host our test response
            server = embeddedServer(Netty, port = PORT) {
                install(PartialContent)
                routing {
                    head("/test-file") {
                        val file = File("src/test/resources/test-responses/dummy-payload.txt")
                        call.response.header(HttpHeaders.ContentLength, file.length().toString())
                        call.response.header(HttpHeaders.AcceptRanges, "bytes")
                        call.respond(HttpStatusCode.OK)
                    }
                    get("/test-file") {
                        val file = File("src/test/resources/test-responses/dummy-payload.txt")
                        call.respondFile(file)
                    }
                }
            }.start(wait = false)
        }

        @AfterAll
        @JvmStatic
        fun teardownServer() {
            server.stop(1000, 2000)
        }
    }

    @Test
    fun `end to end download succeeds with real network and disk IO`() = runBlocking {
        val targetFile = File("build/tmp/integration-download.txt")
        targetFile.delete() // Clean up from previous test runs

        NetworkClient().use { networkClient ->
            ConcurrentFileWriter(targetFile).use { fileWriter ->
                val downloader = ConcurrentFileDownloader(
                    networkClient, ChunkCalculator(), fileWriter
                )

                // Collect all states into a list to verify the final result
                val states = downloader.download("http://localhost:$PORT/test-file", 2).toList()

                // Assertions
                assertTrue(states.first() is DownloadState.Starting)
                assertTrue(states.last() is DownloadState.Success)
                assertTrue(targetFile.exists())
                assertTrue(targetFile.length() > 0)
            }
        }
    }
}