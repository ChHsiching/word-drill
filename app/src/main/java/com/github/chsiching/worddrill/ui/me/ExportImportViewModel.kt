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
 * 导出/导入（Ticket #10）的 ViewModel。
 *
 * SAF 文件选择由 Composable 用 [androidx.activity.compose.rememberLauncherForActivityResult]
 * 发起（[androidx.activity.result.contract.ActivityResultContracts.CreateDocument] /
 * [OpenDocument]），用户选完返回一个 [Uri] 传给本 VM 的 [exportTo] / [importFrom]。
 *
 * 本 VM 只做：Uri → ContentResolver 流 → [BackupService]（序列化由 [DatabaseJsonSerializer]
 * 纯函数处理，已在 JVM 单测覆盖）。所有 I/O 在 [Dispatchers.IO] 上。
 *
 * nickname 是导出文件的标签（由 UI 在导出弹窗里可选输入），不落全局设置。
 *
 * 继承 [AndroidViewModel] 以拿 [Application] 的 ContentResolver（比 @Inject Context 更直接，
 * 且 ContentResolver 是应用级单例，无生命周期泄漏风险）。
 */
@HiltViewModel
class ExportImportViewModel @Inject constructor(
    application: Application,
    private val backup: BackupService,
) : AndroidViewModel(application) {

    private val _status: MutableStateFlow<ExportImportStatus> = MutableStateFlow(ExportImportStatus.Idle)
    val status: StateFlow<ExportImportStatus> = _status.asStateFlow()

    /** 导出整库为 JSON 到 [uri]。nickname 可空。 */
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

    /** 从 [uri] 读 JSON 并整库覆盖恢复。 */
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

    /** UI 展示过状态后清回 Idle。 */
    fun clearStatus() {
        _status.value = ExportImportStatus.Idle
    }
}

/**
 * 导出/导入的 UI 状态。
 *
 * [Done] / [Failed] 携带文案资源 id（VM 非 Composable 不能直接 stringResource；
 * 由 [StatusLine] 在 Composable 层解析）。这样既符合"用户可见文案走 R.string"的
 * repo 约定（不把中文写死在 VM 里），又保留 VM→UI 的单向数据流。
 */
sealed interface ExportImportStatus {
    data object Idle : ExportImportStatus
    data object Working : ExportImportStatus
    data class Done(@StringRes val messageRes: Int) : ExportImportStatus
    data class Failed(@StringRes val messageRes: Int, val detail: String?) : ExportImportStatus
}
