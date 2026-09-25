# Contract: Screens, routes and UI state

Navigation Compose with type-safe `@Serializable` routes. Each ViewModel exposes one immutable
`StateFlow<UiState>` built from repository `Flow`s (Principle I). One-shot results (saved, refused)
are part of the state as a `message: UserMessage?` that the screen acknowledges with an event. There
are no `Channel`s.

## Routes

| Route | Module | Start? |
|-------|--------|--------|
| `EventListRoute` | `:feature:events` | yes |
| `CaptureRoute` | `:feature:capture` | from list FAB |
| `EventDetailRoute(id: String)` | `:feature:events` | from list row (1 tap, which meets SC-006) |

## Capture (`:feature:capture`)

```kotlin
data class CaptureUiState(
    val workerId: String = "", val blockId: String = "", val quantity: String = "",
    val timestamp: Instant,                          // now(clock), shown read-only
    val errors: Set<ValidationError> = emptySet(),   // shown per field
    val saving: Boolean = false,
    val message: UserMessage? = null,                // Saved | StorageFull
)
```

Actions: `onWorkerIdChange`, `onBlockIdChange`, `onQuantityChange`, `onSave`, `onMessageShown`.
A successful save clears the quantity, keeps the worker and block ids for fast repeated entry,
refreshes the timestamp, and shows "Saved offline". Nothing is stored while `errors` is non-empty
(US1-3).

## Event list (`:feature:events`)

```kotlin
data class EventListUiState(
    val filter: SyncStatus? = null,                  // chips: All / Pending / Synced / Failed
    val events: List<EventRow> = emptyList(),        // newest first
    val sync: SyncRunSummary? = null,                // SyncBanner
    val loading: Boolean = true,
)
data class EventRow(val id: String, val workerId: String, val blockId: String,
                    val quantity: Int, val timestamp: Instant, val status: SyncStatus)
```

Each row shows worker, block, quantity, local-formatted timestamp, and a `StatusChip` (US4-1).
Filter chips satisfy US4-2. The banner shows "Syncing… n sent", "Last sync failed: <error>", or
"Sync interrupted" (FR-020).

## Event detail (`:feature:events`)

```kotlin
data class EventDetailUiState(
    val detail: FieldEventDetail? = null,
    val editingQuantity: String? = null,             // non-null while in edit mode
    val errors: Set<ValidationError> = emptySet(),
    val message: UserMessage? = null,                // ReadOnlySynced | InFlight | Deleted | Retried | StorageFull
    val deleted: Boolean = false,                    // screen pops back when true
)
```

| Status | Shown | Actions enabled |
|--------|-------|-----------------|
| PENDING, not in flight | fields, attempts (if > 0), last transport error (if any) | Edit quantity, Delete |
| PENDING, in flight | fields, "Syncing now" | none (actions are visible but disabled; if one fires it's refused with `InFlight`) |
| FAILED | fields, **failure reason**, **attempts**, kind label | Edit quantity, Delete, **Retry** |
| SYNCED | fields, "Synced" | none; if one fires it's refused with the message "Synced events are read-only" (US4-5) |

The Delete action asks for confirmation with an in-app M3 `AlertDialog`.
