package com.jcgrdev.picktracechallenge.feature.events.list

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jcgrdev.picktracechallenge.core.designsystem.component.StatusChip
import com.jcgrdev.picktracechallenge.core.designsystem.icon.PicktraceIcons
import com.jcgrdev.picktracechallenge.core.model.SyncStatus
import com.jcgrdev.picktracechallenge.feature.events.R
import com.jcgrdev.picktracechallenge.feature.events.labelRes
import com.jcgrdev.picktracechallenge.feature.events.tone
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

@Composable
fun EventListScreenRoute(onAdd: () -> Unit, onOpen: (String) -> Unit, viewModel: EventListViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    EventListScreen(state = state, onAdd = onAdd, onOpen = onOpen, onFilterChange = viewModel::onFilterChange)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EventListScreen(
    state: EventListUiState,
    onAdd: () -> Unit = {},
    onOpen: (String) -> Unit = {},
    onFilterChange: (SyncStatus?) -> Unit = {},
) {
    Scaffold(
        topBar = { TopAppBar(title = { Text(stringResource(R.string.events_title)) }) },
        floatingActionButton = {
            FloatingActionButton(onClick = onAdd) {
                Icon(PicktraceIcons.Add, contentDescription = stringResource(R.string.events_add))
            }
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            FilterRow(selected = state.filter, onFilterChange = onFilterChange)
            if (!state.loading && state.events.isEmpty()) {
                Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                    val empty = if (state.filter == null) R.string.events_empty else R.string.events_empty_filtered
                    Text(stringResource(empty), style = MaterialTheme.typography.bodyLarge)
                }
            } else {
                LazyColumn(Modifier.fillMaxSize()) {
                    items(state.events, key = { it.id }) { row ->
                        EventRowItem(row, onClick = { onOpen(row.id) })
                        HorizontalDivider()
                    }
                }
            }
        }
    }
}

@Composable
private fun FilterRow(selected: SyncStatus?, onFilterChange: (SyncStatus?) -> Unit) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp),
    ) {
        (listOf<SyncStatus?>(null) + SyncStatus.entries).forEach { status ->
            FilterChip(
                selected = selected == status,
                onClick = { onFilterChange(status) },
                label = { Text(stringResource(status?.labelRes ?: R.string.events_filter_all)) },
            )
        }
    }
}

@Composable
private fun EventRowItem(row: EventRow, onClick: () -> Unit) {
    val formatter = remember { DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM).withZone(ZoneId.systemDefault()) }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(stringResource(R.string.events_row_ids, row.workerId, row.blockId), style = MaterialTheme.typography.titleMedium)
            Text(
                "${stringResource(R.string.events_row_quantity, row.quantity)} · ${formatter.format(row.timestamp)}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        StatusChip(label = stringResource(row.status.labelRes), tone = row.status.tone)
    }
}
