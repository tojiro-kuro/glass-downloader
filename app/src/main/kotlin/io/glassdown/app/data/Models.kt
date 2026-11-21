package io.glassdown.app.data

import java.util.UUID

data class VideoInfo(
    val title: String,
    val thumbnailUrl: String?,
    val durationSeconds: Long,
    val uploader: String?,
    val sourceUrl: String,
    val formats: List<FormatOption>,
)

data class FormatOption(
    val id: String,
    val label: String,
    val qualityLabel: String,
    val container: String,
    val fileSizeBytes: Long?,
    val videoUrl: String?,
    val audioUrl: String?,
    val isVideoOnly: Boolean,
    val isAudioOnly: Boolean,
    val height: Int?,
    val fps: Int?,
    val bitrateKbps: Int?,
) {
    val needsMux: Boolean get() = !isAudioOnly && !videoUrl.isNullOrBlank() && !audioUrl.isNullOrBlank()
    val isProgressive: Boolean get() = !isAudioOnly && !videoUrl.isNullOrBlank() && audioUrl.isNullOrBlank()
}

enum class DownloadStatus {
    QUEUED, RESOLVING, DOWNLOADING, MERGING, COMPLETED, FAILED, CANCELED
}

data class DownloadTask(
    val id: String = UUID.randomUUID().toString(),
    val sourceUrl: String,
    val title: String,
    val format: FormatOption,
    val status: DownloadStatus = DownloadStatus.QUEUED,
    val progress: Float = 0f,
    val downloadedBytes: Long = 0L,
    val totalBytes: Long = -1L,
    val error: String? = null,
    val filePath: String? = null,
    val fileUri: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
)
