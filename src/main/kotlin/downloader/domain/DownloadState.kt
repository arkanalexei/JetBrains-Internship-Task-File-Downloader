package downloader.domain

sealed interface DownloadState {
    data object Starting : DownloadState
    data class Downloading(
        val bytesDownloaded: Long,
        val totalBytes: Long,
        val progressPercentage: Int
    ) : DownloadState

    data class Success(val fileDetails: String) : DownloadState
    data class Error(val exception: Throwable) : DownloadState
}