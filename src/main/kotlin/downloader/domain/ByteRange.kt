package downloader.domain

data class ByteRange(val start: Long, val end: Long) {
    val size: Long get() = end - start + 1
}