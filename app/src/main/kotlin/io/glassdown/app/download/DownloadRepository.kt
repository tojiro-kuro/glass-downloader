package io.glassdown.app.download

import io.glassdown.app.data.DownloadStatus
import io.glassdown.app.data.DownloadTask
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update

object DownloadRepository {
    private val _tasks = MutableStateFlow<List<DownloadTask>>(emptyList())
    val tasks: StateFlow<List<DownloadTask>> = _tasks

    fun enqueue(task: DownloadTask) {
        _tasks.update { listOf(task) + it }
    }

    fun update(id: String, transform: (DownloadTask) -> DownloadTask) {
        _tasks.update { list -> list.map { if (it.id == id) transform(it) else it } }
    }

    fun get(id: String): DownloadTask? = _tasks.value.firstOrNull { it.id == id }

    fun remove(id: String) {
        _tasks.update { list -> list.filterNot { it.id == id } }
    }

    fun clearTerminal() {
        _tasks.update { list ->
            list.filterNot { it.status == DownloadStatus.COMPLETED || it.status == DownloadStatus.FAILED || it.status == DownloadStatus.CANCELED }
        }
    }
}
