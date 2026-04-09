package downloader.domain

data class FileInfo(
    val sizeBytes: Long,
    val supportsRanges: Boolean
)