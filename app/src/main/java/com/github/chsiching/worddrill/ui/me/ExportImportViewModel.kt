package com.github.chsiching.worddrill.ui.me

import android.app.Application
import android.net.Uri
import androidx.annotation.StringRes
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.github.chsiching.worddrill.R
import com.github.chsiching.worddrill.data.PresetImportOrchestrator
import com.github.chsiching.worddrill.data.backup.BackupService
import com.github.chsiching.worddrill.data.backup.DatabaseJsonSerializer
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@HiltViewModel
class ExportImportViewModel @Inject constructor(
    application: Application,
    private val backup: BackupService,
    private val presetImportOrchestrator: PresetImportOrchestrator,
) : AndroidViewModel(application) {

    private val _status: MutableStateFlow<ExportImportStatus> = MutableStateFlow(ExportImportStatus.Idle)
    val status: StateFlow<ExportImportStatus> = _status.asStateFlow()

    fun exportTo(uri: Uri, nickname: String?) {
        _status.value = ExportImportStatus.Working
        viewModelScope.launch {
            try {
                val snapshot = withContext(Dispatchers.IO) { backup.export(nickname) }
                val json = DatabaseJsonSerializer.serialize(snapshot)
                withContext(Dispatchers.IO) {
                    val out = getApplication<Application>().contentResolver.openOutputStream(uri)
                        ?: error("export uri not openable")
                    out.use { it.write(json.toByteArray(Charsets.UTF_8)) }
                }
                _status.value = ExportImportStatus.Done(R.string.me_export_done)
            } catch (e: Exception) {
                _status.value = ExportImportStatus.Failed(R.string.me_export_failed, e.message)
            }
        }
    }

    fun importFrom(uri: Uri) {
        _status.value = ExportImportStatus.Working
        viewModelScope.launch {
            try {
                val json = withContext(Dispatchers.IO) {
                    val input = getApplication<Application>().contentResolver.openInputStream(uri)
                        ?: error("import uri not openable")
                    input.use { it.readBytes().toString(Charsets.UTF_8) }
                }
                val snapshot = DatabaseJsonSerializer.deserialize(json)
                withContext(Dispatchers.IO) {
                    backup.import(snapshot)
                    // A backup from an older version carries that version's exam books,
                    // word lists and definitions. The refresh replaces them with the
                    // bundled content and leaves the user's own data as restored.
                    presetImportOrchestrator.refreshPresets()
                }
                _status.value = ExportImportStatus.Done(R.string.me_import_done)
            } catch (e: Exception) {
                _status.value = ExportImportStatus.Failed(R.string.me_import_failed, e.message)
            }
        }
    }

    fun clearStatus() {
        _status.value = ExportImportStatus.Idle
    }
}

sealed interface ExportImportStatus {
    data object Idle : ExportImportStatus
    data object Working : ExportImportStatus
    data class Done(@StringRes val messageRes: Int) : ExportImportStatus
    data class Failed(@StringRes val messageRes: Int, val detail: String?) : ExportImportStatus
}
