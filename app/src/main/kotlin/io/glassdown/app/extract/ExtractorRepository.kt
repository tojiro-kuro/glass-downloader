package io.glassdown.app.extract

import io.glassdown.app.data.FormatOption
import io.glassdown.app.data.VideoInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.VideoStream
import kotlin.math.abs

object ExtractorRepository {

    fun ensureInit() {
        val needsInit = runCatching { NewPipe.getDownloader() == null }.getOrDefault(true)
        if (needsInit) {
            runCatching { NewPipe.init(NewPipeDownloader.getInstance()) }
        }
    }

    suspend fun resolve(url: String): Result<VideoInfo> = withContext(Dispatchers.IO) {
        runCatching {
            ensureInit()
            val cleanUrl = url.trim()
            val service = ServiceList.all().firstOrNull { svc ->
                runCatching { svc.streamLHFactory.acceptUrl(cleanUrl) }.getOrDefault(false)
            } ?: ServiceList.YouTube

            val info = StreamInfo.getInfo(service, cleanUrl)
            val formats = buildFormats(info)
            if (formats.isEmpty()) error("No se encontraron formatos descargables")

            VideoInfo(
                title = info.name?.takeIf { it.isNotBlank() } ?: "Video sin título",
                thumbnailUrl = info.thumbnails.maxByOrNull { it.height }?.url ?: info.thumbnailUrl,
                durationSeconds = info.duration.coerceAtLeast(0L),
                uploader = info.uploaderName?.takeIf { it.isNotBlank() },
                sourceUrl = cleanUrl,
                formats = formats,
            )
        }
    }

    private fun buildFormats(info: StreamInfo): List<FormatOption> {
        val out = mutableListOf<FormatOption>()
        val seen = mutableSetOf<String>()

        // Progressive (video+audio) streams
        info.videoStreams.orEmpty()
            .filter { !it.content.isNullOrBlank() }
            .sortedByDescending { it.height }
            .forEach { s ->
                val key = "p-${s.height}-${s.format?.name}"
                if (seen.add(key)) {
                    out += progressiveOption(s)
                }
            }

        // Muxed options: video-only + best audio
        val bestAudio = info.audioStreams.orEmpty()
            .filter { !it.content.isNullOrBlank() }
            .maxByOrNull { it.averageBitrate.takeIf { b -> b > 0 } ?: it.bitrate }
        if (bestAudio != null) {
            info.videoOnlyStreams.orEmpty()
                .filter { !it.content.isNullOrBlank() }
                .sortedByDescending { it.height }
                .forEach { s ->
                    val key = "m-${s.height}-${s.format?.name}"
                    // only add mux option if there isn't already a progressive with same height+container
                    val hasProgressive = out.any { it.height == s.height && it.isProgressive && it.container.equals(s.format?.name, true) }
                    if (!hasProgressive && seen.add(key)) {
                        out += muxOption(s, bestAudio)
                    }
                }
        }

        // Audio-only
        info.audioStreams.orEmpty()
            .filter { !it.content.isNullOrBlank() }
            .sortedByDescending { it.averageBitrate.takeIf { b -> b > 0 } ?: it.bitrate }
            .forEach { s ->
                val key = "a-${s.averageBitrate}-${s.format?.name}"
                if (seen.add(key)) {
                    val kbps = (s.averageBitrate.takeIf { b -> b > 0 } ?: s.bitrate).coerceAtLeast(0)
                    val ext = s.format?.suffix ?: "m4a"
                    out += FormatOption(
                        id = key,
                        label = "Audio ${if (kbps > 0) "${kbps}kbps" else ""} .$ext".trim(),
                        qualityLabel = if (kbps > 0) "${kbps} kbps" else "Audio",
                        container = ext,
                        fileSizeBytes = null,
                        videoUrl = null,
                        audioUrl = s.content,
                        isVideoOnly = false,
                        isAudioOnly = true,
                        height = null,
                        fps = null,
                        bitrateKbps = kbps.takeIf { it > 0 },
                    )
                }
            }

        return out.sortedWith(
            compareByDescending<FormatOption> { !it.isAudioOnly }
                .thenByDescending { it.height ?: 0 }
                .thenByDescending { it.bitrateKbps ?: 0 }
        )
    }

    private fun progressiveOption(s: VideoStream): FormatOption {
        val ext = s.format?.suffix ?: "mp4"
        val fps = s.fps.takeIf { it > 0 }
        val label = buildString {
            append("${s.height}p")
            if (fps != null && fps > 30) append(fps)
            append(" .").append(ext)
        }
        return FormatOption(
            id = "p-${s.itag}-${s.height}-${ext}",
            label = label,
            qualityLabel = "${s.height}p${if (fps != null && fps > 30) " ${fps}fps" else ""}",
            container = ext,
            fileSizeBytes = null,
            videoUrl = s.content,
            audioUrl = null,
            isVideoOnly = false,
            isAudioOnly = false,
            height = s.height,
            fps = fps,
            bitrateKbps = null,
        )
    }

    private fun muxOption(
        s: org.schabi.newpipe.extractor.stream.VideoStream,
        audio: org.schabi.newpipe.extractor.stream.AudioStream,
    ): FormatOption {
        val ext = s.format?.suffix ?: "mp4"
        val fps = s.fps.takeIf { it > 0 }
        val label = buildString {
            append("${s.height}p")
            if (fps != null && fps > 30) append(fps)
            append(" .").append(ext)
            append(" (mux)")
        }
        return FormatOption(
            id = "m-${s.itag}-${audio.itag}-${s.height}-${ext}",
            label = label,
            qualityLabel = "${s.height}p${if (fps != null && fps > 30) " ${fps}fps" else ""}",
            container = ext,
            fileSizeBytes = null,
            videoUrl = s.content,
            audioUrl = audio.content,
            isVideoOnly = false,
            isAudioOnly = false,
            height = s.height,
            fps = fps,
            bitrateKbps = null,
        )
    }
}
