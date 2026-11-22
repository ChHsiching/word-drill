package com.github.chsiching.worddrill.update

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.github.chsiching.worddrill.R
import com.github.chsiching.worddrill.ui.theme.DialogButton
import com.github.chsiching.worddrill.ui.theme.DialogButtonStyle
import com.github.chsiching.worddrill.ui.theme.WordDrillDialog

/**
 * Production entry: hosts the update ViewModel, runs the silent launch check, and
 * bridges the permission event to the unknown-app-sources settings screen.
 * Composed only once the splash has finished, so the dialog can never cover it.
 */
@Composable
fun UpdateGate(modifier: Modifier = Modifier) {
    val viewModel: UpdateViewModel = hiltViewModel()
    LaunchedEffect(viewModel) {
        viewModel.checkForUpdate()
    }
    UpdateContent(
        viewModel = viewModel,
        onRequestInstallPermission = rememberInstallPermissionRequest(viewModel),
        modifier = modifier,
    )
}

/**
 * Me-tab host for the manual check-update row (#59): renders the shared update dialog
 * while an update is available and bridges the install-permission event. The result
 * dialogs (up-to-date / errors) stay in MeScreen, which owns the manual-check flow.
 */
@Composable
fun UpdateDialogHost(viewModel: UpdateViewModel) {
    val requestPermission = rememberInstallPermissionRequest(viewModel)
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                UpdateEvent.RequestInstallPermission -> requestPermission()
            }
        }
    }
    (state as? UpdateUiState.Available)?.let { available ->
        UpdateDialog(
            state = available,
            onUpdateNow = viewModel::updateNow,
            onDismiss = viewModel::dismissDialog,
        )
    }
}

/** Launcher for the unknown-app-sources settings screen; routes the activity result
 *  back into [UpdateViewModel.onInstallPermissionResult]. */
@Composable
private fun rememberInstallPermissionRequest(viewModel: UpdateViewModel): () -> Unit {
    val context = androidx.compose.ui.platform.LocalContext.current
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        viewModel.onInstallPermissionResult()
    }
    return {
        permissionLauncher.launch(
            DownloadManagerApkInstaller.installPermissionIntent(context)
        )
    }
}

/**
 * Renders the update dialog or the inline failure notice for [viewModel]'s state.
 * [onRequestInstallPermission] launches the settings intent in production; tests
 * record the call instead.
 */
@Composable
fun UpdateContent(
    viewModel: UpdateViewModel,
    onRequestInstallPermission: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                UpdateEvent.RequestInstallPermission -> onRequestInstallPermission()
            }
        }
    }
    when (val current = state) {
        UpdateUiState.Idle -> Unit
        // Only the Me tab's manual check sets this; MeScreen renders the dialog.
        UpdateUiState.UpToDate -> Unit
        is UpdateUiState.Available -> UpdateDialog(
            state = current,
            onUpdateNow = viewModel::updateNow,
            onDismiss = viewModel::dismissDialog,
        )
        is UpdateUiState.Notice -> UpdateNoticeBanner(
            message = stringResource(current.messageRes),
            onDismiss = viewModel::dismissNotice,
            modifier = modifier,
        )
    }
}

@Composable
private fun UpdateDialog(
    state: UpdateUiState.Available,
    onUpdateNow: () -> Unit,
    onDismiss: () -> Unit,
) {
    WordDrillDialog(
        // Scrim tap lands here too — same as the later button.
        onDismissRequest = onDismiss,
        title = stringResource(R.string.update_dialog_title),
        message = stringResource(R.string.update_dialog_version, state.release.version),
        content = state.release.notes.takeIf { it.isNotBlank() }?.let { notes ->
            {
                Column(
                    modifier = Modifier
                        .heightIn(max = 220.dp)
                        .verticalScroll(rememberScrollState()),
                ) {
                    Text(
                        text = notes,
                        fontSize = 13.sp,
                        lineHeight = 20.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        buttons = {
            DialogButton(
                text = stringResource(R.string.update_action_later),
                onClick = onDismiss,
                style = DialogButtonStyle.Cancel,
            )
            DialogButton(
                text = stringResource(R.string.update_action_now),
                onClick = onUpdateNow,
                style = DialogButtonStyle.Primary,
            )
        },
    )
}

@Composable
private fun UpdateNoticeBanner(
    message: String,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(12.dp),
        tonalElevation = 2.dp,
        shadowElevation = 6.dp,
        modifier = modifier
            .statusBarsPadding()
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(start = 14.dp),
        ) {
            Text(
                text = message,
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(4.dp))
            IconButton(onClick = onDismiss) {
                Icon(
                    imageVector = Icons.Filled.Close,
                    contentDescription = stringResource(R.string.update_notice_dismiss),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
