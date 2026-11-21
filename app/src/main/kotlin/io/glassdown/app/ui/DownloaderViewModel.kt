package io.glassdown.app.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.glassdown.app.data.DownloadStatus
import io.glassdown.app.data.DownloadTask
import io.glassdown.app.data.FormatOption
import io.glassdown.app.data.VideoInfo
import io.glassdown.app.download.DownloadRepository
import io.glassdown.app.download.DownloadService
import io.glassdown.app.extract.ExtractorRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class UiState(
    val url: String = "",
    val resolving: Boolean = false,
    val videoInfo: VideoInfo? = null,
    val error: String? = null,
    val selectedFormatId: String? = null,
)

class DownloaderViewModel(app: Application) : AndroidViewModel(app) {
    private val _ui = MutableStateFlow(UiState())
    val ui: StateFlow<UiState> = _ui.asStateFlow()
    val tasks = DownloadRepository.tasks

    fun setUrl(url: String) {
        _ui.update { it.copy(url = url, error = null) }
    }

    fun resolve() {
        val url = _ui.value.url.trim()
        if (url.isBlank()) {
            _ui.update { it.copy(error = "Pega un enlace primero") }
            return
        }
        viewModelScope.launch {
            _ui.update { it.copy(resolving = true, error = null, videoInfo = null, selectedFormatId = null) }
            val result = ExtractorRepository.resolve(url)
            _ui.update { state ->
                result.fold(
                    onSuccess = { info ->
                        val defaultFormat = info.formats.firstOrNull { !it.isAudioOnly } ?: info.formats.firstOrNull()
                        state.copy(
                            resolving = false,
                            videoInfo = info,
                            selectedFormatId = defaultFormat?.id,
                            error = null,
                        )
                    },
                    onFailure = { e ->
                        state.copy(
                            resolving = false,
                            error = e.message?.take(160) ?: "No se pudo analizar el enlace",
                        )
                    }
                )
            }
        }
    }

    fun selectFormat(id: String) {
        _ui.update { it.copy(selectedFormatId = id) }
    }

    fun startDownload() {
        val state = _ui.value
        val info = state.videoInfo ?: return
        val format = info.formats.firstOrNull { it.id == state.selectedFormatId } ?: return
        val task = DownloadTask(
            sourceUrl = info.sourceUrl,
            title = info.title,
            format = format,
            status = DownloadStatus.QUEUED,
        )
        DownloadRepository.enqueue(task)
        DownloadService.start(getApplication(), task.id)
    }

    fun cancelDownload(id: String) {
        val ctx = getApplication<Application>()
        val intent = android.content.Intent(ctx, DownloadService::class.java).apply {
            action = DownloadService.ACTION_CANCEL
            putExtra(DownloadService.EXTRA_TASK_ID, id)
        }
        ctx.startService(intent)
    }

    fun clearTerminal() {
        DownloadRepository.clearTerminal()
    }
}
