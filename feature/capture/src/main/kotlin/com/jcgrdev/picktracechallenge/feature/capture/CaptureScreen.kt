package com.jcgrdev.picktracechallenge.feature.capture

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jcgrdev.picktracechallenge.core.designsystem.icon.PicktraceIcons
import com.jcgrdev.picktracechallenge.core.model.ValidationError
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

@Composable
fun CaptureScreenRoute(onBack: () -> Unit, viewModel: CaptureViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    CaptureScreen(
        state = state,
        onWorkerIdChange = viewModel::onWorkerIdChange,
        onBlockIdChange = viewModel::onBlockIdChange,
        onQuantityChange = viewModel::onQuantityChange,
        onSave = viewModel::onSave,
        onMessageShown = viewModel::onMessageShown,
        onBack = onBack,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CaptureScreen(
    state: CaptureUiState,
    onWorkerIdChange: (String) -> Unit = {},
    onBlockIdChange: (String) -> Unit = {},
    onQuantityChange: (String) -> Unit = {},
    onSave: () -> Unit = {},
    onMessageShown: () -> Unit = {},
    onBack: () -> Unit = {},
) {
    val snackbar = remember { SnackbarHostState() }
    val message = state.message?.let {
        stringResource(if (it == UserMessage.Saved) R.string.capture_saved else R.string.capture_storage_full)
    }
    LaunchedEffect(state.message) {
        if (message != null) {
            snackbar.showSnackbar(message)
            onMessageShown()
        }
    }
    val formatter = remember { DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM).withZone(ZoneId.systemDefault()) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.capture_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(PicktraceIcons.ArrowBack, contentDescription = stringResource(R.string.capture_back))
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Column(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
        ) {
            FormField(
                value = state.workerId,
                onValueChange = onWorkerIdChange,
                label = stringResource(R.string.capture_worker_id),
                error = state.errors.firstMessageFor(ValidationError.WorkerIdMissing),
            )
            FormField(
                value = state.blockId,
                onValueChange = onBlockIdChange,
                label = stringResource(R.string.capture_block_id),
                error = state.errors.firstMessageFor(ValidationError.BlockIdMissing),
            )
            FormField(
                value = state.quantity,
                onValueChange = onQuantityChange,
                label = stringResource(R.string.capture_quantity),
                error = state.errors.firstMessageFor(ValidationError.QuantityNotANumber, ValidationError.QuantityNotPositive),
                keyboardType = KeyboardType.Number,
                imeAction = ImeAction.Done,
            )
            Text(
                stringResource(R.string.capture_recorded_at, formatter.format(state.timestamp)),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Button(onClick = onSave, enabled = !state.saving, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.capture_save))
            }
        }
    }
}

@Composable
private fun FormField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    error: String?,
    keyboardType: KeyboardType = KeyboardType.Text,
    imeAction: ImeAction = ImeAction.Next,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        isError = error != null,
        supportingText = error?.let { { Text(it) } },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType, imeAction = imeAction),
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun Set<ValidationError>.firstMessageFor(vararg candidates: ValidationError): String? =
    candidates.firstOrNull { it in this }?.let {
        stringResource(
            when (it) {
                ValidationError.WorkerIdMissing -> R.string.capture_error_worker_missing
                ValidationError.BlockIdMissing -> R.string.capture_error_block_missing
                ValidationError.QuantityNotANumber -> R.string.capture_error_quantity_not_number
                ValidationError.QuantityNotPositive -> R.string.capture_error_quantity_not_positive
            },
        )
    }
