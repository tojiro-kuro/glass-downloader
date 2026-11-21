package io.glassdown.app.download

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import android.os.Build
import android.os.Environment
import android.os.IBinder
import android.provider.MediaStore
import androidx.core.app.NotificationCompat
import io.glassdown.app.R
import io.glassdown.app.data.DownloadStatus
import io.glassdown.app.data.DownloadTask
import io.glassdown.app.extract.NewPipeDownloader
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.math.roundToInt

class DownloadService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val queueMutex = Mutex()
    private val jobs = mutableMapOf<String, Job>()
    private val client = OkHttpClient.Builder()
        .followRedirects(true)
        .followSslRedirects(true)
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        createChannel()
        when (intent?.action) {
            ACTION_CANCEL -> {
                val id = intent.getStringExtra(EXTRA_TASK_ID) ?: return START_NOT_STICKY
                jobs[id]?.cancel()
                DownloadRepository.update(id) { it.copy(status = DownloadStatus.CANCELED, error = "Cancelada") }
                stopIfIdle()
            }
            else -> {
                val id = intent?.getStringExtra(EXTRA_TASK_ID) ?: return START_NOT_STICKY
                startForegroundCompat(id)
                enqueue(id)
            }
        }
        return START_NOT_STICKY
    }

    private fun enqueue(taskId: String) {
        if (jobs[taskId]?.isActive == true) return
        val job = scope.launch {
            queueMutex.withLock { runTask(taskId) }
            jobs.remove(taskId)
            stopIfIdle()
        }
        jobs[taskId] = job
    }

    private suspend fun runTask(taskId: String) {
        val task = DownloadRepository.get(taskId) ?: return
        if (task.status == DownloadStatus.CANCELED) return
        DownloadRepository.update(taskId) { it.copy(status = DownloadStatus.DOWNLOADING, error = null, progress = 0f) }
        updateNotification(taskId)

        try {
            val outFile = if (task.format.needsMux) {
                downloadAndMux(task)
            } else {
                downloadSingle(task)
            }
            val (uri, path) = publishToDownloads(task, outFile)
            outFile.delete()
            DownloadRepository.update(taskId) {
                it.copy(status = DownloadStatus.COMPLETED, progress = 1f, fileUri = uri, filePath = path, error = null)
            }
            notifyDone(taskId, success = true)
        } catch (ce: kotlinx.coroutines.CancellationException) {
            DownloadRepository.update(taskId) { it.copy(status = DownloadStatus.CANCELED, error = "Cancelada") }
            notifyDone(taskId, success = false)
        } catch (t: Throwable) {
            DownloadRepository.update(taskId) {
                it.copy(status = DownloadStatus.FAILED, error = t.message?.take(140) ?: "Error de descarga")
            }
            notifyDone(taskId, success = false)
        }
    }

    private fun downloadSingle(task: DownloadTask): File {
        val url = task.format.videoUrl ?: task.format.audioUrl ?: error("URL vacía")
        val ext = task.format.container.ifBlank { "mp4" }
        val tmp = File(cacheDir, "dl_${task.id}.$ext")
        downloadToFile(task.id, url, tmp, 0f, 1f)
        return tmp
    }

    private fun downloadAndMux(task: DownloadTask): File {
        val videoUrl = task.format.videoUrl ?: error("URL de video vacía")
        val audioUrl = task.format.audioUrl ?: error("URL de audio vacía")
        val ext = task.format.container.ifBlank { "mp4" }
        val vTmp = File(cacheDir, "dl_${task.id}_v.$ext")
        val aExt = audioUrl.substringAfterLast('.', "m4a").substringBefore('?').take(4).ifBlank { "m4a" }
        val aTmp = File(cacheDir, "dl_${task.id}_a.$aExt")
        val out = File(cacheDir, "dl_${task.id}_out.$ext")
        try {
            downloadToFile(task.id, videoUrl, vTmp, 0f, 0.80f)
            DownloadRepository.update(task.id) { it.copy(status = DownloadStatus.DOWNLOADING, progress = 0.80f) }
            updateNotification(task.id)
            downloadToFile(task.id, audioUrl, aTmp, 0.80f, 0.95f)
            DownloadRepository.update(task.id) { it.copy(status = DownloadStatus.MERGING, progress = 0.96f) }
            updateNotification(task.id)
            muxStreams(vTmp, aTmp, out, ext)
            DownloadRepository.update(task.id) { it.copy(progress = 0.99f) }
        } finally {
            vTmp.delete()
            aTmp.delete()
        }
        return out
    }

    private fun downloadToFile(taskId: String, url: String, target: File, base: Float, ceiling: Float) {
        val req = Request.Builder().url(url).header("User-Agent", USER_AGENT).build()
        client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) throw IOException("HTTP ${resp.code}")
            val body = resp.body ?: throw IOException("Respuesta vacía")
            val total = body.contentLength()
            DownloadRepository.update(taskId) { it.copy(totalBytes = if (total > 0) total else it.totalBytes) }
            body.byteStream().use { input ->
                FileOutputStream(target).use { output ->
                    val buf = ByteArray(64 * 1024)
                    var read: Int
                    var copied = 0L
                    var lastUpdate = 0L
                    while (input.read(buf).also { read = it } != -1) {
                        if (Thread.currentThread().isInterrupted) throw IOException("Interrumpida")
                        val current = DownloadRepository.get(taskId)
                        if (current?.status == DownloadStatus.CANCELED) throw IOException("Cancelada")
                        output.write(buf, 0, read)
                        copied += read
                        val now = System.currentTimeMillis()
                        if (now - lastUpdate > 450) {
                            lastUpdate = now
                            val frac = if (total > 0) copied.toFloat() / total.toFloat() else 0f
                            val progress = (base + (ceiling - base) * frac.coerceIn(0f, 1f))
                            DownloadRepository.update(taskId) {
                                it.copy(downloadedBytes = copied, progress = progress)
                            }
                            updateNotification(taskId)
                        }
                    }
                    output.flush()
                }
            }
        }
    }

    private fun muxStreams(video: File, audio: File, output: File, container: String) {
        val outputFormat = if (container.equals("webm", true)) {
            MediaMuxer.OutputFormat.MUXER_OUTPUT_WEBM
        } else {
            MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4
        }
        val muxer = MediaMuxer(output.absolutePath, outputFormat)
        var started = false
        try {
            val vEx = MediaExtractor().apply { setDataSource(video.absolutePath) }
            val aEx = MediaExtractor().apply { setDataSource(audio.absolutePath) }
            try {
                val vIdx = selectTrack(vEx, "video/")
                val aIdx = selectTrack(aEx, "audio/")
                if (vIdx < 0 || aIdx < 0) error("No se pudieron leer pistas para mezclar")
                vEx.selectTrack(vIdx)
                aEx.selectTrack(aIdx)
                val vFormat = vEx.getTrackFormat(vIdx)
                val aFormat = aEx.getTrackFormat(aIdx)
                val videoTrack = muxer.addTrack(vFormat)
                val audioTrack = muxer.addTrack(aFormat)
                if (outputFormat == MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4) {
                    val rot = vFormat.getIntegerOrNull(MediaFormat.KEY_ROTATION) ?: 0
                    if (rot != 0) muxer.setOrientationHint(rot)
                }
                muxer.start()
                started = true
                writeTrack(vEx, muxer, videoTrack)
                writeTrack(aEx, muxer, audioTrack)
            } finally {
                vEx.release()
                aEx.release()
            }
        } finally {
            if (started) runCatching { muxer.stop() }
            muxer.release()
        }
    }

    private fun selectTrack(extractor: MediaExtractor, prefix: String): Int {
        for (i in 0 until extractor.trackCount) {
            val mime = extractor.getTrackFormat(i).getString(MediaFormat.KEY_MIME) ?: ""
            if (mime.startsWith(prefix)) return i
        }
        return -1
    }

    private fun writeTrack(extractor: MediaExtractor, muxer: MediaMuxer, trackIndex: Int) {
        val format = extractor.getTrackFormat(trackIndex)
        val maxSize = format.getIntegerOrNull(MediaFormat.KEY_MAX_INPUT_SIZE) ?: (512 * 1024)
        val buffer = java.nio.ByteBuffer.allocate(maxSize.coerceAtLeast(64 * 1024))
        val info = android.media.MediaCodec.BufferInfo()
        extractor.seekTo(0, MediaExtractor.SEEK_TO_CLOSEST_SYNC)
        while (true) {
            info.offset = 0
            info.size = extractor.readSampleData(buffer, 0)
            if (info.size < 0) break
            info.presentationTimeUs = extractor.sampleTime
            val flags = extractor.sampleFlags
            info.flags = when {
                flags and MediaExtractor.SAMPLE_FLAG_SYNC != 0 -> android.media.MediaCodec.BUFFER_FLAG_KEY_FRAME
                else -> 0
            }
            muxer.writeSampleData(trackIndex, buffer, info)
            extractor.advance()
        }
    }

    private fun MediaFormat.getIntegerOrNull(key: String): Int? = runCatching { getInteger(key) }.getOrNull()

    private fun publishToDownloads(task: DownloadTask, source: File): Pair<String?, String?> {
        val ext = task.format.container.ifBlank { if (task.format.isAudioOnly) "m4a" else "mp4" }
        val safeName = sanitizeFileName(task.title) + "." + ext
        val mime = mimeFor(ext, task.format.isAudioOnly)

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, safeName)
                put(MediaStore.Downloads.MIME_TYPE, mime)
                put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/GlassDown")
                put(MediaStore.Downloads.IS_PENDING, 1)
            }
            val resolver = contentResolver
            val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                ?: error("No se pudo crear el archivo en Descargas")
            resolver.openOutputStream(uri)?.use { out ->
                FileInputStream(source).use { inp -> inp.copyTo(out) }
            } ?: error("No se pudo escribir el archivo")
            values.clear()
            values.put(MediaStore.Downloads.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
            uri.toString() to safeName
        } else {
            val dir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "GlassDown")
            if (!dir.exists()) dir.mkdirs()
            val dest = uniqueFile(dir, safeName)
            FileInputStream(source).use { inp -> FileOutputStream(dest).use { out -> inp.copyTo(out) } }
            null to dest.absolutePath
        }
    }

    private fun uniqueFile(dir: File, name: String): File {
        var f = File(dir, name)
        if (!f.exists()) return f
        val base = name.substringBeforeLast('.')
        val ext = name.substringAfterLast('.', "")
        var i = 1
        while (f.exists()) {
            f = File(dir, "$base ($i).${ext}")
            i++
        }
        return f
    }

    private fun sanitizeFileName(name: String): String {
        val clean = name.replace(Regex("[\\\\/:*?\"<>|\\n\\r\\t]"), "_").trim().trim('.')
        return clean.take(80).ifBlank { "video" }
    }

    private fun mimeFor(ext: String, audioOnly: Boolean): String = when (ext.lowercase()) {
        "mp4" -> if (audioOnly) "audio/mp4" else "video/mp4"
        "webm" -> if (audioOnly) "audio/webm" else "video/webm"
        "m4a" -> "audio/mp4"
        "mp3" -> "audio/mpeg"
        else -> if (audioOnly) "audio/*" else "video/*"
    }

    private fun startForegroundCompat(taskId: String) {
        val notif = buildNotification(taskId)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIF_ID, notif, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            startForeground(NOTIF_ID, notif)
        }
    }

    private fun updateNotification(taskId: String) {
        val nm = getSystemService(NotificationManager::class.java) ?: return
        nm.notify(NOTIF_ID, buildNotification(taskId))
    }

    private fun notifyDone(taskId: String, success: Boolean) {
        val task = DownloadRepository.get(taskId) ?: return
        val nm = getSystemService(NotificationManager::class.java) ?: return
        val title = task.title.take(60)
        val text = if (success) getString(R.string.completed) else (task.error ?: getString(R.string.failed))
        val notif = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentTitle(title)
            .setContentText(text)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()
        nm.notify(taskId.hashCode(), notif)
    }

    private fun buildNotification(activeTaskId: String): Notification {
        val task = DownloadRepository.get(activeTaskId)
        val title = task?.title?.take(60) ?: getString(R.string.app_name)
        val progress = ((task?.progress ?: 0f) * 100).roundToInt().coerceIn(0, 100)
        val text = when (task?.status) {
            DownloadStatus.MERGING -> "Mezclando audio y video…"
            DownloadStatus.COMPLETED -> getString(R.string.completed)
            DownloadStatus.FAILED -> task.error ?: getString(R.string.failed)
            DownloadStatus.CANCELED -> "Cancelada"
            else -> "$progress%"
        }
        val ongoing = task?.status == DownloadStatus.DOWNLOADING || task?.status == DownloadStatus.MERGING
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle(title)
            .setContentText(text)
            .setOnlyAlertOnce(true)
            .setOngoing(ongoing)
            .setProgress(100, progress, task == null || task.status == DownloadStatus.QUEUED)
            .build()
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(NotificationManager::class.java)
            val ch = NotificationChannel(CHANNEL_ID, getString(R.string.notification_channel), NotificationManager.IMPORTANCE_LOW)
            ch.description = getString(R.string.notification_channel_desc)
            nm.createNotificationChannel(ch)
        }
    }

    private fun stopIfIdle() {
        val hasActive = DownloadRepository.tasks.value.any {
            it.status == DownloadStatus.QUEUED || it.status == DownloadStatus.DOWNLOADING || it.status == DownloadStatus.MERGING
        }
        if (!hasActive && jobs.isEmpty()) {
            stopForeground(STOP_FOREGROUND_DETACH)
            stopSelf()
        }
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        const val ACTION_START = "io.glassdown.action.START"
        const val ACTION_CANCEL = "io.glassdown.action.CANCEL"
        const val EXTRA_TASK_ID = "task_id"
        private const val CHANNEL_ID = "downloads"
        private const val NOTIF_ID = 1001
        private const val USER_AGENT = NewPipeDownloader.USER_AGENT

        fun start(context: Context, taskId: String) {
            val intent = Intent(context, DownloadService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_TASK_ID, taskId)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }
    }
}
