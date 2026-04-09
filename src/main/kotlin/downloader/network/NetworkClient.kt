package downloader.network

import downloader.domain.ByteRange
import downloader.domain.FileInfo
import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.utils.io.*
import java.io.Closeable


class NetworkClient(
    private val client: HttpClient = HttpClient(),
) : Closeable {
    /**
     * Sends a HEAD request to discover the file size and verify the server supports partial downloads
     */
    suspend fun fetchFileInfo(url: String): FileInfo {
        val response = client.head(url)

        val contentLength = response.headers[HttpHeaders.ContentLength]?.toLongOrNull()
            ?: throw IllegalStateException("Server did not return a valid Content-Length")

        val supportsRanges = response.headers[HttpHeaders.AcceptRanges] == "bytes"

        return FileInfo(contentLength, supportsRanges)
    }

    /**
     * Streams a specific byte range from the server.
     * Takes a higher-order suspending function [onBytesReceived] to hand off
     * the raw byte array and the number of bytes read as they arrive.
     */
    suspend fun streamChunk(
        url: String,
        range: ByteRange,
        onBytesReceived: suspend (buffer: ByteArray, bytesRead: Int) -> Unit
    ) {
        client.prepareGet(url) {
            header(HttpHeaders.Range, "bytes=${range.start}-${range.end}")
        }.execute { response ->
            if (!response.status.isSuccess()) {
                throw IllegalStateException("Server rejected chunk request. Status: ${response.status}")
            }

            val channel: ByteReadChannel = response.bodyAsChannel()
            // 8KB buffer
            val buffer = ByteArray(8 * 1024)

            // Read the stream sequentially as bytes arrive over the network
            while (!channel.isClosedForRead) {
                val bytesRead = channel.readAvailable(buffer, 0, buffer.size)
                if (bytesRead > 0) {
                    // Hand the buffer back to the caller
                    onBytesReceived.invoke(buffer, bytesRead)
                }
            }
        }
    }

    override fun close() {
        client.close()
    }
}