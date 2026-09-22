package com.tkrz.qrtix.services

import com.tkrz.qrtix.data.Event
import com.tkrz.qrtix.data.Ticket
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

object GenerationTaskHolder {
    var dummyTickets: List<Ticket> = emptyList()
    var eventForExport: Event? = null
    var zipName: String = ""

    private val _progress = MutableStateFlow(GenerationProgress())
    val progress: StateFlow<GenerationProgress> = _progress

    fun updateProgress(current: Int, total: Int, isFinished: Boolean = false, successFile: java.io.File? = null, uploadTasks: List<com.tkrz.qrtix.data.cloud.UploadTask> = emptyList()) {
        _progress.value = GenerationProgress(current, total, isFinished, successFile, uploadTasks)
    }

    fun reset() {
        dummyTickets = emptyList()
        eventForExport = null
        zipName = ""
        _progress.value = GenerationProgress()
    }
}

data class GenerationProgress(
    val current: Int = 0,
    val total: Int = 0,
    val isFinished: Boolean = false,
    val successFile: java.io.File? = null,
    val uploadTasks: List<com.tkrz.qrtix.data.cloud.UploadTask> = emptyList()
)
