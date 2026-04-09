package downloader.network

import io.ktor.client.*
import io.ktor.client.engine.mock.*
import io.ktor.http.*
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class NetworkClientMissingHeaderTest {

    @Test
    fun `fetchFileInfo throws when Content-Length header is missing`() {
        val mockEngine = MockEngine {
            respond(
                content = "",
                status = HttpStatusCode.OK,
                headers = headersOf() // No Content-Length
            )
        }

        val client = NetworkClient(HttpClient(mockEngine))

        assertThrows<IllegalStateException> {
            runBlocking { client.fetchFileInfo("http://random-url.com") }
        }
    }

    @Test
    fun `fetchFileInfo marks supportsRanges false when AcceptRanges header is absent`() = runBlocking {
        val mockEngine = MockEngine {
            respond(
                content = "",
                status = HttpStatusCode.OK,
                headers = headersOf("Content-Length", listOf("1000"))
                // No Accept-Ranges header
            )
        }

        val client = NetworkClient(HttpClient(mockEngine))
        val fileInfo = client.fetchFileInfo("http://random-url.com")

        assert(!fileInfo.supportsRanges)
    }

    @Test
    fun `streamChunk throws when server returns non-success status`() {
        val mockEngine = MockEngine {
            respond(
                content = "",
                status = HttpStatusCode.Forbidden
            )
        }

        val client = NetworkClient(HttpClient(mockEngine))

        assertThrows<IllegalStateException> {
            runBlocking {
                client.streamChunk("http://random-url.com", downloader.domain.ByteRange(0, 99)) { _, _ -> }
            }
        }
    }
}