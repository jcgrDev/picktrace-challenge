# Quickstart: validating Offline Field Event Capture & Sync

This file explains how to prove each user story works. It covers the automated tests first, since the
phase gates depend on them, and then a manual pass on an emulator. Shapes and behaviors are defined in
[data-model.md](data-model.md) and [contracts/](contracts/). They aren't repeated here.

## Prerequisites

- JDK 17 or newer on `PATH`. The exact AGP/Gradle-compatible JDK is confirmed in Phase 1.
- Android SDK with the compileSdk platform chosen in Phase 1 (`ANDROID_HOME` set or `local.properties`).
- For the manual pass only: an emulator or device on API 26+, and `adb`.
- No backend. Debug builds use `FakeSyncServer`.

## Automated validation (every phase gate)

```bash
./gradlew build test          # compiles all variants, runs every JVM test (incl. Robolectric)
./gradlew test --tests '*FieldEventValidatorTest' --tests '*Uuid7Test' --tests '*HlcTest'   # Phase 1
```

Test classes are named by Phase in [plan.md](plan.md#implementation-phases). The scenario each one
must cover:

| Story / criterion | Test (module) | Asserts |
|-------------------|---------------|---------|
| US1-1, FR-002/004 | `OfflineFirstFieldEventRepositoryTest` (`:core:data`) | recorded event has a v7 id and PENDING; one `pending_op` row exists |
| US1-2, SC-001 | `RelaunchPersistenceTest` (`:core:data`, file-backed Room) | close DB, reopen on the same file, events are identical |
| US1-3, FR-006 | `FieldEventValidatorTest`, `CaptureViewModelTest` | each invalid input yields its error; nothing stored |
| Principle II | `RecordAtomicityTest` | a failure injected after the entity insert leaves neither the event nor the op |
| US2-1, FR-010 | `SyncSchedulerTest` (`:core:sync`, `TestDriver`) | record → unique work enqueued with CONNECTED; runs when constraint met |
| US2-2/3, FR-013 | `SyncEngineTest` | acked → SYNCED & op gone; rejected → FAILED with reason |
| US2-4, SC-004 | `SyncEngineOrderTest` | `FakeSyncServer.deliveryLog` is in `seq` order, even with skewed timestamps |
| US2-5, FR-014 | `SyncEngineTransportTest` | `DropConnection` mid-run: earlier batches SYNCED, the rest PENDING, none FAILED |
| US2-6, FR-015 | `SyncSchedulerTest` | a second request during a run appends; no `opId` appears twice in `deliveryLog` |
| US3-1/2/3, FR-012 | `SyncEngineBatchTest` | 250 events → 5 pushes of ≤50; partial reject per op; a failed batch stays PENDING |
| Edge: duplicate delivery, SC-005, FR-016 | `SyncEngineRelaunchTest` | `DropAfterProcessing`, new DB + engine instance on the same file, run again → SYNCED, the server stored each entity once |
| Edge: invalid response | `SyncEngineTransportTest` | `GarbageBody` → batch PENDING, attempts +1, run stops |
| US4-1..4, FR-007/008/009 | `EventListViewModelTest`, `EventDetailViewModelTest`, repository tests | filter, newest-first, edit keeps PENDING and rewrites op fields, delete removes both |
| US4-5, FR-009a | `ReadOnlyRulesTest` | edit/delete of SYNCED → `ReadOnlySynced`; of IN_FLIGHT → `InFlight`; DB unchanged |
| US5-1/2, FR-017/018 | `RetryTest` | FAILED shows reason + attempts; retry → new `opId`, attempts 0, same `seq`, next run acks it |
| US5-3/4, FR-019 | `SyncEngineTransportTest` | 5× `Status(503)` → FAILED/EXHAUSTED; not retried automatically; 429 respects `Retry-After` |
| FR-020 | `SyncRunLogTest` | a run writes RUNNING→COMPLETED; a killed run shows INTERRUPTED on the next start |
| SC-003, SC-007 | `BacklogTest` (Phase 5) | 5,000 events: batches ≤ 50, no query without `LIMIT` on the outbox, list VM stays off the main thread |

Expected result: `BUILD SUCCESSFUL` and zero failures. The phase summary pastes the tail of that
output.

## Manual validation (emulator, debug build)

```bash
./gradlew :app:installDebug
adb shell am start -n com.jcgrdev.picktracechallenge/.MainActivity
```

1. **US1, capture offline.** Turn on airplane mode (`adb shell cmd connectivity airplane-mode enable`,
   API 30+). Record three events. Try a quantity of `0` and a blank block, and check that both are
   refused with reasons. Run `adb shell am force-stop com.jcgrdev.picktracechallenge` and relaunch. All
   three should be listed as Pending.
2. **US2, auto sync.** Turn off airplane mode. Within 10 s the banner should show a sync, and all
   three events should become Synced.
3. **US5, rejection and retry.** Go offline and record one event with quantity `1000`, which the
   *fake-only* rule rejects (see [contracts/fake-sync-server.md](contracts/fake-sync-server.md)). Go
   online. It should become Failed, and its detail should show the reason and attempts. Edit the
   quantity to `3`. The event should stay Failed. Tap Retry. It should turn Pending and then Synced.
4. **US4, manage.** Filter by Pending, Synced, and Failed. Open a Synced event and check that edit and
   delete are refused with "Synced events are read-only". Record an event offline, edit its quantity,
   delete another, go online, and confirm only the edited one syncs.
5. **Background trigger.** Go offline, record an event, background the app, and go online. The event
   should sync without reopening the app. WorkManager's constraint path has no 10 s guarantee.

The debug `FakeSyncServer` state is lost on app restart. Duplicate-delivery behavior is proven by the
automated `SyncEngineRelaunchTest`, not by hand.
