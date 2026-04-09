package downloader.network

import downloader.domain.ByteRange
import io.ktor.client.*
import io.ktor.client.engine.mock.*
import io.ktor.http.*
import io.ktor.utils.io.*
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class NetworkClientTest {

    @Test
    fun `fetchFileInfo correctly parses HEAD headers`() = runBlocking {
        // Setup Mock Engine
        val mockEngine = MockEngine { request ->
            respond(
                content = ByteReadChannel(""),
                status = HttpStatusCode.OK,
                headers = headersOf(
                    HttpHeaders.ContentLength to listOf("5000"),
                    HttpHeaders.AcceptRanges to listOf("bytes")
                )
            )
        }

        // Inject the mock to client
        val client = NetworkClient(HttpClient(mockEngine))

        // Execute and verify
        val fileInfo = client.fetchFileInfo("http://random-url.com")

        assertEquals(5000L, fileInfo.sizeBytes)
        assertTrue(fileInfo.supportsRanges)
    }

    @Test
    fun `streamChunk successfully streams mocked bytes`() = runBlocking {
        val fakeNetworkData = "Hello JetBrains!".toByteArray()

        val mockEngine = MockEngine { request ->
            // Verify client sent the correct Range header
            assertEquals("bytes=0-15", request.headers[HttpHeaders.Range])

            respond(
                content = ByteReadChannel(fakeNetworkData),
                status = HttpStatusCode.PartialContent
            )
        }

        val client = NetworkClient(HttpClient(mockEngine))
        var totalBytesReceived = 0

        client.streamChunk("http://random-url.com", ByteRange(0, 15)) { buffer, bytesRead ->
            totalBytesReceived += bytesRead
        }

        assertEquals(fakeNetworkData.size, totalBytesReceived)
    }
}