# Feature Specification: Offline Field Event Capture & Sync

**Feature Branch**: `001-field-event-sync`

**Created**: 2026-09-25

**Status**: Draft

**Input**: User description: "take these steps as general requirements, I want to start working one by one: 1. Create a FIFO queue to handle Event class 2. Create a data base to store Field Events 3. Support CRUD events in the repository. 4. Use the repository pattern to sync data when a network connection is detected. 5. Design a class named FieldEvent to support following data: 6. Design a enum to store event status: pending, synced, or failed 7. When a network connection is detected all the records with pending status must be sync, batch syncing is supported. Sample event: { id: a3f1c2d4-8b0e-4e2a-9c7f-1d6e5b3a2f01 (UUID), workerId: w_001 (value class WorkerId), blockId: block_42 (value class BlockId), quantity: 3, timestamp: 2025-06-10T08:32:00Z }"

## User Scenarios & Testing *(mandatory)*

The stories below are ordered so that each one can be built and verified on its own, matching the requester's intent to work through the requirements one by one. Story 1 alone is a usable offline logbook; each later story adds one capability on top of it.

### User Story 1 - Capture a field event while offline (Priority: P1)

A field worker records that a quantity of something was produced or handled at a given block (for example, "worker w_001 handled 3 units at block_42 at 08:32"). The worker may be far from any network. The event is saved on the device immediately, with a unique identifier and a status of **pending**, and it is still there after the app is closed and reopened.

**Why this priority**: Nothing else matters if the record is lost. Durable local capture is the foundation every other story builds on.

**Independent Test**: With networking disabled, record three events, kill and relaunch the app, and confirm all three are listed with status pending, correct worker, block, quantity, and timestamp.

**Acceptance Scenarios**:

1. **Given** the device has no connectivity, **When** a worker records an event with worker id, block id, quantity, and timestamp, **Then** the event is stored locally with a freshly generated unique id and status pending.
2. **Given** an event was stored, **When** the app is force-closed and reopened, **Then** the event is still present with identical data and status.
3. **Given** a worker submits an event with a non-positive quantity, missing worker id, or missing block id, **When** they attempt to save, **Then** the event is rejected with a clear reason and nothing is stored.

---

### User Story 2 - Pending events sync automatically when connectivity returns (Priority: P2)

When the device regains a network connection, every event still marked pending is sent to the remote system without the worker doing anything. Events that the remote system accepts become **synced**; events it rejects become **failed**. Events are delivered in the order they were recorded (oldest first).

**Why this priority**: This is the core value of the feature: workers never have to remember to "upload"; the data flows on its own as soon as it can.

**Independent Test**: Record five events offline, enable connectivity, and confirm within a short time that all five show status synced and that the remote system received them in recording order, each exactly once.

**Acceptance Scenarios**:

1. **Given** there are pending events and the device is offline, **When** connectivity is detected, **Then** a sync run starts automatically without user action.
2. **Given** a sync run delivers an event and the remote system accepts it, **When** the run completes, **Then** that event's status is synced.
3. **Given** a sync run delivers an event and the remote system rejects it, **When** the run completes, **Then** that event's status is failed and the reason is retained for display.
4. **Given** events were recorded at 08:30, 08:32, and 08:35, **When** they are synced, **Then** they are delivered in that order.
5. **Given** connectivity is lost mid-run, **When** the run is interrupted, **Then** events already accepted stay synced and events not yet delivered stay pending; nothing is marked failed solely because the connection dropped.
6. **Given** connectivity is detected twice in quick succession, **When** the second trigger arrives while a run is in progress, **Then** no event is delivered twice.

---

### User Story 3 - Large backlogs sync in batches (Priority: P3)

After a long shift offline, a worker may have hundreds of pending events. The system sends them in groups rather than one at a time, and each event still ends up with its own individual status.

**Why this priority**: Batching keeps a big backlog from taking too long or draining the battery, but a one-at-a-time sync (Story 2) is already functionally correct, so this is an optimisation layered on top.

**Independent Test**: Record 250 events offline, enable connectivity, and confirm they are delivered in a small number of groups (not 250 separate deliveries), all in recording order, with per-event status reflecting the remote outcome.

**Acceptance Scenarios**:

1. **Given** more pending events than the batch size, **When** a sync run executes, **Then** the events are delivered in consecutive batches, each batch in recording order, until no pending events remain.
2. **Given** a batch where the remote system accepts some events and rejects others, **When** the batch completes, **Then** accepted events become synced and rejected events become failed, independently.
3. **Given** a batch that cannot be delivered at all (for example the connection drops), **When** the run stops, **Then** every event in that batch remains pending.

---

### User Story 4 - Review and manage recorded events (Priority: P4)

A worker or supervisor can see the list of recorded events, filter by status, open one to view its details, correct a mistake (for example a wrong quantity), or delete an event recorded by accident. Corrections are only possible while the event has not yet been accepted by the remote system; once synced, an event is read-only on the device.

**Why this priority**: Corrections are needed in the real world, but the capture-and-sync loop delivers value without them.

**Independent Test**: Record several events, then list them, filter to pending only, edit one event's quantity, delete another, and confirm the list reflects the changes and the edited event will be sent with the corrected value. Then confirm that a synced event cannot be edited or deleted.

**Acceptance Scenarios**:

1. **Given** stored events, **When** the user opens the event list, **Then** they see every event with its worker, block, quantity, timestamp, and status, newest first.
2. **Given** stored events with mixed statuses, **When** the user filters by a status, **Then** only events with that status are shown.
3. **Given** a pending event, **When** the user edits its quantity and saves, **Then** the stored event reflects the new value and it remains pending so the corrected value is what gets sent.
4. **Given** a pending event, **When** the user deletes it, **Then** it is removed locally and is never sent.
5. **Given** a synced event, **When** the user attempts to edit or delete it, **Then** the action is refused with a message explaining that synced events are read-only, and the stored event is unchanged.

---

### User Story 5 - Failed events are visible and can be retried (Priority: P5)

When the remote system rejects an event, or the event could not be delivered after several tries, the worker can see that it failed and why, and can retry it manually. Transient transport problems resolve themselves because undelivered events stay pending and are retried on the next run.

**Why this priority**: Failure handling makes the feature trustworthy, but it only matters once syncing (Story 2) exists.

**Independent Test**: Make the remote system reject one event, confirm it shows as failed with a reason, trigger a retry, make the remote system accept it, and confirm it becomes synced.

**Acceptance Scenarios**:

1. **Given** a failed event, **When** the user views it, **Then** the failure reason and the number of attempts are shown.
2. **Given** a failed event, **When** the user requests a retry, **Then** the event returns to pending with a fresh attempt count and is included in the next sync run as a new submission.
3. **Given** a pending event whose last delivery attempt could not reach the remote system, **When** connectivity is detected again, **Then** it is retried and its attempt count increases.
4. **Given** an event has accumulated 5 unreachable attempts or was rejected by the remote system, **When** a sync run executes, **Then** it is not retried automatically and stays failed until the user retries it manually.

---

### Edge Cases

- **Duplicate delivery**: the app is killed after the remote system accepted a batch but before the local status was updated. On the next run those events are sent again; the remote system must treat a delivery with an already-seen delivery identity (opId) as already accepted, and the local status must then become synced, not failed.
- **Connectivity flapping**: the network toggles on and off every few seconds. Only one sync run is active at a time; a new trigger while a run is active is coalesced into "run again when the current run ends".
- **Clock skew**: an event's timestamp is in the future or far in the past relative to the device clock. The timestamp is stored as given; ordering for delivery uses the local recording sequence, not the event timestamp.
- **Very large backlog**: thousands of pending events after a multi-day outage. The sync completes without the app becoming unresponsive and without loading every event into memory at once.
- **Edit during sync**: the user tries to edit an event that is currently inside an in-flight batch. The edit is refused while the event is in flight, because if the delivery succeeds the event becomes read-only; the user is told to wait for the run to finish. If the delivery fails, the event is editable again.
- **Storage full**: the device cannot store a new event. The worker is told immediately and the event is not silently dropped.
- **Invalid remote response**: the remote system returns something unintelligible for a batch. All events in that batch stay pending; the run stops and will retry on the next trigger.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: The system MUST store each field event locally on the device at the moment it is recorded, without requiring network connectivity.
- **FR-002**: Each field event MUST carry: a globally unique identifier generated on the device, the identifier of the worker who recorded it, the identifier of the block it applies to, a positive whole-number quantity, the timestamp of the event, and a sync status.
- **FR-003**: The worker identifier and block identifier MUST be distinct, non-interchangeable types so that a worker id can never be stored where a block id is expected, and vice versa.
- **FR-004**: The sync status of an event MUST be exactly one of: **pending**, **synced**, or **failed**. A newly recorded event MUST start as pending.
- **FR-005**: Locally stored events MUST survive the app being closed, killed, or the device being restarted.
- **FR-006**: The system MUST reject an event whose quantity is not a positive whole number or whose worker id or block id is empty, and MUST report the reason.
- **FR-007**: The system MUST allow a user to list all events, filter them by status, view a single event, update an event's quantity, and delete an event.
- **FR-008**: Updating a pending event MUST keep it pending; the updated values are what get delivered.
- **FR-009**: Deleting a pending event MUST prevent it from ever being delivered.
- **FR-009a**: Events with status synced MUST be read-only: any attempt to update or delete a synced event MUST be refused with an explanatory message and MUST leave the stored event unchanged. Events that are in flight within an active sync run MUST be treated the same way until the run reports their outcome.
- **FR-009b**: Failed events MUST be editable and deletable in the same way as pending events.
- **FR-010**: The system MUST detect when the device gains network connectivity and MUST automatically start a sync run if any pending events exist.
- **FR-011**: A sync run MUST deliver pending events in the order they were recorded (first recorded, first delivered).
- **FR-012**: A sync run MUST deliver events in batches of a configurable maximum size, defaulting to 50 events per batch, consuming the backlog batch after batch until no pending events remain.
- **FR-013**: After a batch is delivered, each event in it MUST be individually marked synced or failed according to the remote system's per-event outcome.
- **FR-014**: If a batch cannot be delivered at all (connection lost, no intelligible response), every event in that batch MUST remain pending.
- **FR-015**: At most one sync run MUST be active at any time; a connectivity trigger that arrives during an active run MUST cause another run once the active one finishes, not a parallel run.
- **FR-016**: An event MUST never be marked synced unless the remote system acknowledged it; re-delivery of an event the remote system has already accepted MUST result in synced, not failed.
- **FR-017**: A failed event MUST retain the failure reason and the number of delivery attempts. An event becomes failed either because the remote system rejected it (permanent, no automatic retry) or because 5 consecutive delivery attempts could not reach the remote system or were told to retry later.
- **FR-018**: A user MUST be able to manually retry a failed event, which returns it to pending with a fresh attempt count and a new delivery identity so the remote system treats it as a new submission.
- **FR-019**: Delivery attempts that fail for transport reasons (no network, remote system busy or unavailable) MUST leave the event pending and count as one attempt; the event is retried on the next run. Events rejected by the remote system MUST NOT be retried automatically.
- **FR-020**: Sync activity (run started, batch delivered, per-event outcome, run finished, run interrupted) MUST be observable so that the user interface can show progress and problems.

### Key Entities

- **Field Event**: A single observation recorded in the field. Attributes: unique id, worker id, block id, quantity, event timestamp, sync status, recording sequence (for ordering), attempt count, last failure reason. It belongs to exactly one worker and one block.
- **Worker Id**: An opaque identifier for the person who recorded the event (example `w_001`). Referenced by field events; the worker's other details are out of scope.
- **Block Id**: An opaque identifier for the physical area or unit the event applies to (example `block_42`). Referenced by field events; the block's other details are out of scope.
- **Sync Status**: The lifecycle state of an event with respect to the remote system: pending (not yet acknowledged), synced (acknowledged), failed (rejected or exhausted).
- **Outbox**: The ordered set of events awaiting delivery, always processed oldest-first.
- **Sync Run**: One execution of the delivery process, triggered by connectivity detection or a manual retry; consists of one or more batches and produces per-event outcomes.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: 100% of events recorded while offline are present, unchanged, after the app is killed and relaunched.
- **SC-002**: While the app is in the foreground, a sync run begins within 10 seconds of the device regaining connectivity when pending events exist. In the background, a run begins once the operating system reports the network constraint met (best effort, no fixed bound).
- **SC-003**: A backlog of 1,000 pending events is fully delivered within 2 minutes on a typical mobile connection, with each event delivered exactly once as far as the remote system is concerned.
- **SC-004**: Events arrive at the remote system in recording order in 100% of sync runs.
- **SC-005**: Zero events are lost or duplicated when the app is killed at any point during a sync run.
- **SC-006**: A user can find the status of any recorded event, and the reason for any failure, in under 3 taps or clicks from the event list.
- **SC-007**: The app remains responsive (no input freeze longer than 1 second) while syncing a backlog of 5,000 events.

## Assumptions

- "Event" in requirement 1 and "FieldEvent" in requirement 5 refer to the same thing; there is a single event type in this feature.
- The remote system exists, accepts a batch of events in one delivery, treats each delivery's identity (opId, distinct from the event id) as an idempotency key, so a manual retry, which carries a new delivery identity, counts as a new submission, and reports an accept/reject outcome per event. Building that remote system is out of scope.
- Connectivity detection means the device reports that a usable network is available; the system does not probe the remote system's reachability before starting a run. An unreachable remote system simply leaves events pending.
- Batch size defaults to 50 and the transport attempt limit to 5; both are configurable but need no user interface in this feature.
- A rejection by the remote system is treated as permanent for that submission; the user decides whether to resubmit.
- Quantity is a positive whole number with no upper bound enforced beyond what the storage can hold.
- Timestamps are supplied in UTC by the caller (as in the sample) and stored as given; the system does not adjust them.
- Newest-first is the display order in lists; oldest-first is the delivery order. These are independent.
- Ordering for delivery is by local recording sequence, not by the event's timestamp, so clock skew cannot reorder delivery.
- Authentication with the remote system, worker and block master data, and any user interface beyond what is needed to exercise the stories are out of scope.
- Single-device use: the same worker does not record events on two devices that must be merged.
- Corrections to an event after the remote system has accepted it are handled outside this feature (for example in the back-office system); the device never sends updates or deletions for synced events.
