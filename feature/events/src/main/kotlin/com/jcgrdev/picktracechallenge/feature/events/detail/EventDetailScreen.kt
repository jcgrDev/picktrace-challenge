package com.jcgrdev.picktracechallenge.feature.events.detail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jcgrdev.picktracechallenge.core.designsystem.component.StatusChip
import com.jcgrdev.picktracechallenge.core.designsystem.icon.PicktraceIcons
import com.jcgrdev.picktracechallenge.core.model.FieldEventDetail
import com.jcgrdev.picktracechallenge.core.model.SyncStatus
import com.jcgrdev.picktracechallenge.core.model.ValidationError
import com.jcgrdev.picktracechallenge.feature.events.R
import com.jcgrdev.picktracechallenge.feature.events.labelRes
import com.jcgrdev.picktracechallenge.feature.events.tone
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

@Composable
fun EventDetailScreenRoute(onBack: () -> Unit, viewModel: EventDetailViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(state.deleted) { if (state.deleted) onBack() }
    EventDetailScreen(
        state = state,
        onBack = onBack,
        onEdit = viewModel::onEdit,
        onQuantityChange = viewModel::onQuantityChange,
        onSaveEdit = viewModel::onSaveEdit,
        onCancelEdit = viewModel::onCancelEdit,
        onDelete = viewModel::onDelete,
        onMessageShown = viewModel::onMessageShown,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EventDetailScreen(
    state: EventDetailUiState,
    onBack: () -> Unit = {},
    onEdit: () -> Unit = {},
    onQuantityChange: (String) -> Unit = {},
    onSaveEdit: () -> Unit = {},
    onCancelEdit: () -> Unit = {},
    onDelete: () -> Unit = {},
    onMessageShown: () -> Unit = {},
) {
    val snackbar = remember { SnackbarHostState() }
    val message = state.message?.let {
        stringResource(
            when (it) {
                UserMessage.ReadOnlySynced -> R.string.detail_read_only
                UserMessage.InFlight -> R.string.detail_in_flight
                UserMessage.StorageFull -> R.string.detail_storage_full
            },
        )
    }
    LaunchedEffect(state.message) {
        if (message != null) {
            snackbar.showSnackbar(message)
            onMessageShown()
        }
    }
    var confirmDelete by rememberSaveable { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.detail_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(PicktraceIcons.ArrowBack, contentDescription = stringResource(R.string.detail_back))
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        val detail = state.detail ?: return@Scaffold
        Column(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
        ) {
            Fields(detail)
            Notice(detail)
            if (state.editingQuantity != null) {
                EditQuantity(state.editingQuantity, state.errors, onQuantityChange, onSaveEdit, onCancelEdit)
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(onClick = onEdit, enabled = detail.canEdit) { Text(stringResource(R.string.detail_edit)) }
                    OutlinedButton(onClick = { confirmDelete = true }, enabled = detail.canEdit) {
                        Text(stringResource(R.string.detail_delete))
                    }
                }
            }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(R.string.detail_delete_title)) },
            text = { Text(stringResource(R.string.detail_delete_body)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    onDelete()
                }) { Text(stringResource(R.string.detail_delete_confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.detail_cancel)) }
            },
        )
    }
}

@Composable
private fun Fields(detail: FieldEventDetail) {
    val formatter = remember { DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM).withZone(ZoneId.systemDefault()) }
    val event = detail.event
    LabeledValue(stringResource(R.string.detail_worker), event.workerId.value)
    LabeledValue(stringResource(R.string.detail_block), event.blockId.value)
    LabeledValue(stringResource(R.string.detail_quantity), event.quantity.toString())
    LabeledValue(stringResource(R.string.detail_recorded_at), formatter.format(event.timestamp))
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(stringResource(R.string.detail_status), style = MaterialTheme.typography.labelLarge)
        StatusChip(label = stringResource(event.status.labelRes), tone = event.status.tone)
    }
    if (detail.attempts > 0) {
        Text(stringResource(R.string.detail_attempts, detail.attempts), style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun Notice(detail: FieldEventDetail) {
    val notice = when {
        detail.event.status == SyncStatus.SYNCED -> R.string.detail_read_only
        detail.inFlight -> R.string.detail_in_flight
        else -> null
    } ?: return
    Text(stringResource(notice), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun LabeledValue(label: String, value: String) {
    Column {
        Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun EditQuantity(
    value: String,
    errors: Set<ValidationError>,
    onValueChange: (String) -> Unit,
    onSave: () -> Unit,
    onCancel: () -> Unit,
) {
    val error = when {
        ValidationError.QuantityNotANumber in errors -> stringResource(R.string.detail_error_quantity_not_number)
        ValidationError.QuantityNotPositive in errors -> stringResource(R.string.detail_error_quantity_not_positive)
        else -> null
    }
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(stringResource(R.string.detail_quantity)) },
        isError = error != null,
        supportingText = error?.let { { Text(it) } },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = Modifier.fillMaxWidth(),
    )
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Button(onClick = onSave) { Text(stringResource(R.string.detail_save)) }
        OutlinedButton(onClick = onCancel) { Text(stringResource(R.string.detail_cancel)) }
    }
}
