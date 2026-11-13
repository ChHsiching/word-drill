package com.github.chsiching.worddrill.ui.me

import android.app.Application
import android.net.Uri
import androidx.annotation.StringRes
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.github.chsiching.worddrill.R
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

/**
 * ViewModel for export/import (Ticket #10).
 *
 * SAF file picking is launched by the Composable with
 * [androidx.activity.compose.rememberLauncherForActivityResult]
 * ([androidx.activity.result.contract.ActivityResultContracts.CreateDocument] /
 * [OpenDocument]); when the user finishes picking, the returned [Uri] is passed to this
 * VM's [exportTo] / [importFrom].
 *
 * This VM only does: Uri to ContentResolver stream to [BackupService] (serialization is
 * handled by the pure function [DatabaseJsonSerializer], already covered by JVM unit
 * tests). All I/O runs on [Dispatchers.IO].
 *
 * nickname is a label for the exported file (optionally entered by the UI in the export
 * dialog) and is not stored in global settings.
 *
 * Extends [AndroidViewModel] to get the [Application]'s ContentResolver (more direct than
 * @Inject Context, and ContentResolver is an app-level singleton with no lifecycle-leak
 * risk).
 */
@HiltViewModel
class ExportImportViewModel @Inject constructor(
    application: Application,
    private val backup: BackupService,
) : AndroidViewModel(application) {

    private val _status: MutableStateFlow<ExportImportStatus> = MutableStateFlow(ExportImportStatus.Idle)
    val status: StateFlow<ExportImportStatus> = _status.asStateFlow()

    /** Exports the whole database as JSON to [uri]. nickname is optional. */
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

    /** Reads JSON from [uri] and restores the whole database, overwriting it. */
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
                withContext(Dispatchers.IO) { backup.import(snapshot) }
                _status.value = ExportImportStatus.Done(R.string.me_import_done)
            } catch (e: Exception) {
                _status.value = ExportImportStatus.Failed(R.string.me_import_failed, e.message)
            }
        }
    }

    /** Clears the status back to Idle after the UI has shown it. */
    fun clearStatus() {
        _status.value = ExportImportStatus.Idle
    }
}

/**
 * UI status of export/import.
 *
 * [Done] / [Failed] carry a message resource id (the VM is not a Composable and cannot
 * call stringResource directly; [StatusLine] resolves it in the Composable layer). This
 * both follows the repo convention that user-visible text goes through R.string (no
 * hardcoded Chinese in the VM) and keeps the one-way VM-to-UI data flow.
 */
sealed interface ExportImportStatus {
    data object Idle : ExportImportStatus
    data object Working : ExportImportStatus
    data class Done(@StringRes val messageRes: Int) : ExportImportStatus
    data class Failed(@StringRes val messageRes: Int, val detail: String?) : ExportImportStatus
}
