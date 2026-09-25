<!--
Sync Impact Report
- Version change: unratified draft (2026-09-25, same day) -> 1.0.0 (ratification)
- Modified principles: draft I..V rewritten in MUST language; draft V "Test-Backed Phases"
  renumbered to VI
- Added sections: Principle V "Ask, Don't Invent"; "Technical Constraints" expanded with
  concurrency and build rules; "Development Workflow" gains phase gate checklist
- Removed sections: none
- Templates: plan/spec/tasks templates are stock and read this file at runtime; no edits needed
- Follow-up TODOs: none
-->
# picktrace-challenge Constitution

## Core Principles

### I. Room Is the Single Source of Truth
The UI MUST observe Room `Flow`s only. No screen MAY read from the network, a repository cache,
or a ViewModel-held copy of persisted data. All sync state (outbox, cursor, attempt counts, node
id) MUST live in Room, and all scheduling state MUST live in WorkManager. No sync state MAY be held
in memory across process death.

Rationale: an offline-first app is only trustworthy if a process kill at any instant loses nothing
and changes nothing about what will be synced.

### II. Atomic Local Writes
The following pairs MUST each happen inside one `db.withTransaction {}`:
- a domain entity change and the insert or rewrite of its `PendingOp`;
- the application of a pulled page and the save of its cursor;
- the status update of an acked event and the deletion of its outbox row.

There MUST be no observable state in which one half of a pair has happened without the other.

Rationale: every sync bug that cannot be reproduced comes from a torn write.

### III. Client-Owned Identity and Ordering
Every op id and entity id MUST be a client-generated UUIDv7. Every op MUST carry a `schemaVersion`
and an HLC timestamp. Ops MUST carry changed fields only. Delivery order MUST be the local
recording sequence, oldest first. A sync run MUST push before it pulls. Outbox rows MUST be
deleted only after the server acknowledges them, never before, never on transport failure.

Rationale: the server is idempotent by `opId`; the client can therefore retry blindly, and
ordering by recording sequence keeps clock skew from reordering events.

### IV. Never Clobber Pending Work
Pulled data MUST NOT overwrite a field that still has a pending local op (rebase). An event whose
status is `synced` MUST be read-only on the device; any edit or delete attempt MUST be refused
with an explanatory message. Corrections after sync are out of scope for the client.

Rationale: the user's unsent work is the most valuable data on the device.

### V. Ask, Don't Invent
No library outside the list in `CLAUDE.md` MAY be added without asking. No backend endpoint,
field, or status code MAY be invented; the contract in `CLAUDE.md` is the whole contract and
unknowns MUST be raised as questions. Until the backend exists, the app MUST run against the
in-process `FakeSyncServer` that implements that contract.

Rationale: the backend is being built separately; a guessed field is a future integration bug.

### VI. Test-Backed Phases (NON-NEGOTIABLE)
The project MUST build and all tests MUST pass at the end of every phase. Each phase MUST end with
a summary of changes, the list of files touched, open questions, and a STOP for approval before
the next phase starts. A claim that something works MUST be backed by build or test output shown
in the summary, never by assertion alone.

Rationale: the requester reviews between phases; an unverified claim wastes that review.

## Technical Constraints

- Stack, module graph, DTOs, and sync semantics are as recorded in `CLAUDE.md`, which this
  constitution incorporates by reference. Conflicts are resolved in favour of this file.
- Annotation processing MUST use KSP only; kapt is forbidden.
- Production code MUST NOT use `GlobalScope` or `runBlocking`.
- Library versions MUST be the latest stable, looked up at the time of adding them and checked for
  mutual compatibility (KSP with Kotlin, Hilt and Room with KSP, Retrofit with the serialization
  converter). Alpha and beta versions are forbidden.
- minSdk is 26; targetSdk and compileSdk are the latest stable.
- Firebase Cloud Messaging and the pull/snapshot engine are deferred; a phase MUST NOT implement
  them without an explicit decision recorded in `CLAUDE.md`.

## Development Workflow

- Each feature follows the Spec Kit flow: `/speckit-specify` -> optional `/speckit-clarify` ->
  `/speckit-plan` -> `/speckit-tasks` -> `/speckit-implement`.
- Implementation proceeds in numbered phases with one approval gate per phase.
- Phase gate checklist, in this order: build passes; all tests pass with output shown; summary of
  changes; files touched; open questions; compliance statement against Principles I to VI; STOP.
- Every plan MUST include a "Constitution Check" naming any principle it bends and why.

## Governance

This constitution supersedes ad-hoc practice and any conflicting note elsewhere in the repository.
Amendments are made only in this file: bump the version per semantic versioning (MAJOR for
principle removal or redefinition, MINOR for a new principle or materially expanded guidance,
PATCH for wording), update the Sync Impact Report comment and the dates below, and mirror any
stack, contract, or workflow change into `CLAUDE.md` in the same change. Every plan and every
phase summary MUST state how it complies with Principles I to VI; a reviewer MAY block a phase
that does not.

**Version**: 1.0.0 | **Ratified**: 2026-09-25 | **Last Amended**: 2026-09-25
