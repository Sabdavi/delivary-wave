# Warehouse Dispatch Planning Service — Iterative Practice README

This README is intended to help evaluate the implementation across the first three iterations of the supply-chain interview exercise.

It documents:
- the business scope
- the functional requirements
- the non-functional concerns introduced per iteration
- the expected API and domain behavior
- what must be implemented in each step

It does **not** prescribe a specific implementation approach.

---

# 1. Business context

Albert Heijn’s transport planning team needs a backend service to manage outbound warehouse shipments.

A planner creates **dispatch waves** for a warehouse and dispatch window, then assigns eligible **shipments** into those waves. In later iterations, the system must also coordinate with an external routing/planning service and remain correct under concurrent planner activity and downstream failures.

The exercise is intentionally iterative:
- first establish a clean functional baseline
- then make assignment logic correct under concurrency
- then introduce asynchronous background processing and operational robustness

---

# 2. Domain concepts

## Shipment
A shipment represents goods that should leave a specific warehouse and be delivered to a specific store.

Expected business fields:
- `id`
- `warehouseId`
- `storeId`
- `plannedDepartureAt`
- `crateCount`
- `status`
- audit fields as needed

Expected shipment statuses:
- `CREATED`
- `READY`
- `ASSIGNED`
- `DISPATCHED`
- `CANCELLED`

## DispatchWave
A dispatch wave represents a planning bucket for one warehouse and one dispatch time window.

Expected business fields:
- `id`
- `warehouseId`
- `dispatchWindowStart`
- `dispatchWindowEnd`
- `maxCrates`
- `status`
- audit fields as needed

Expected wave statuses:
- `PLANNING`
- `LOCKED`
- `DISPATCHED`
- `CANCELLED`

## Assignment
An assignment represents that a shipment has been placed into a dispatch wave.

Expected business fields:
- `id`
- `shipmentId`
- `waveId`
- `assignedAt`
- audit fields as needed

---

# 3. Iteration 1 — Functional core

## Goal
Build a clean synchronous service that supports the basic dispatch planning flow.

## Required endpoints
- `POST /shipments`
- `POST /dispatch-waves`
- `POST /dispatch-waves/{waveId}/assignments`
- `GET /dispatch-waves/{waveId}`
- `GET /dispatch-waves/{waveId}/summary`

## Required business rules
- only shipments in `READY` status can be assigned
- shipment and dispatch wave must belong to the same warehouse
- a shipment can belong to at most one active wave
- a wave has a maximum crate capacity
- assignment must fail if the capacity would be exceeded
- assignment is allowed only while the wave is in `PLANNING`

## What must be implemented

### API design
- define request/response DTOs for shipment creation, wave creation, shipment assignment, wave details, and wave summary
- keep controller responsibilities clear and focused
- ensure API paths reflect the resource structure

### Validation
- validate request payloads at the API boundary
- validate business rules during assignment
- ensure invalid wave windows, invalid crate counts, empty assignment requests, and unsupported state transitions are rejected

### Domain modeling
- model the core domain objects required for shipment planning
- represent the relationship between shipments and dispatch waves
- keep business-relevant fields explicit in the model

### Persistence
- persist shipments, waves, and assignments in a relational database
- maintain data integrity between related records
- ensure the persisted model can support later iterations

### Transactional behavior
- ensure assignment is executed atomically
- ensure all changes needed for a successful assignment succeed or fail together

### Error handling
- expose clear API errors for validation failures, missing resources, invalid states, warehouse mismatches, duplicate assignment attempts, and capacity violations
- use a consistent error response format

## Acceptance criteria
- shipments can be created successfully
- dispatch waves can be created successfully
- eligible shipments can be assigned to a valid wave
- invalid shipments are rejected for the right business reasons
- capacity violations are rejected
- wave summary returns correct totals
- implementation is understandable, maintainable, and reviewable

## Evaluation focus
- resource design
- DTO design
- validation completeness
- service boundaries
- transaction boundary
- entity modeling
- exception handling

---

# 4. Iteration 2 — Concurrency and correctness

## Goal
Keep the same functional behavior from Iteration 1, but make the assignment flow correct under concurrent planner activity.

## Additional problem context
Multiple planners or system processes may attempt assignment at the same time.

The implementation must remain correct when concurrent requests target:
- the same shipment
- the same wave
- overlapping shipment sets
- a wave whose status changes while assignment is in progress
- shipments whose status changes while assignment is in progress

## Required correctness guarantees
- one shipment must not end up assigned to multiple active waves
- wave capacity must not be exceeded due to concurrent assignment requests
- assignment must not succeed when shipment or wave state becomes invalid during processing
- business invariants must remain correct even under simultaneous requests

## What must be implemented

### Concurrency-aware assignment handling
- review the Iteration 1 assignment flow and identify race conditions
- ensure the assignment use case remains correct when invoked concurrently
- ensure the correctness of validation and persistence under concurrent access

### Persistence integrity
- ensure database-level integrity supports the business rules
- ensure duplicate assignment attempts cannot silently corrupt the state
- ensure conflicting concurrent writes are handled deterministically

### Transactional correctness
- revisit the transaction boundary for assignment
- ensure the transaction semantics are sufficient for concurrent execution
- ensure the implementation does not rely on assumptions valid only in single-threaded execution

### Error handling under contention
- define how the API behaves when a request loses a race with another request
- ensure clients receive a clear and consistent error response for concurrency conflicts

### Testing
- add tests that simulate concurrent assignment attempts
- verify that correctness guarantees hold under race conditions
- verify that wave capacity and shipment uniqueness constraints remain protected

## Acceptance criteria
- two concurrent requests cannot assign the same shipment twice
- overlapping concurrent requests cannot cause capacity oversubscription
- concurrent conflicts are visible and testable
- implementation remains understandable and production-oriented

## Evaluation focus
- race-condition awareness
- transaction correctness
- persistence integrity
- API behavior for conflicts
- concurrency testing quality

---

# 5. Iteration 3 — Background route preparation and operational robustness

## Goal
After shipments have been assigned to a wave, the system must support asynchronous background route preparation for **one dispatch wave at a time**.

In this exercise, the route-preparation model is:
- one dispatch wave is the unit of triggering
- one dispatch wave belongs to one warehouse
- one dispatch wave may contain shipments for multiple stores
- route preparation runs for that single wave
- the result of route preparation may later represent one or more executable delivery routes, but this iteration focuses on wave-level preparation tracking

## Business context
Once a dispatch wave has been planned and is no longer open for further changes, the system must be able to request route preparation for that wave.

Route preparation is the operational step that takes the warehouse-scoped wave and starts the process of turning its assigned shipments into a feasible delivery plan.

For this iteration, route preparation is treated as:
- background work
- dependent on an external downstream service
- potentially slow
- potentially failure-prone
- observable through application state

## Scope of this iteration
This iteration introduces asynchronous workflow handling and application lifecycle concerns beyond the synchronous request/response path.

The system now needs to deal with:
- asynchronous execution
- bounded work intake
- retryable downstream failure
- pending backlog
- running work
- completed work
- failed work
- shutdown behavior
- restart expectations

## High-level model additions

### Route preparation request model
The implementation must introduce a wave-level concept that represents that route preparation has been requested for a given dispatch wave.

Expected business data to track at the application level:
- target `waveId`
- route-preparation status
- timestamps relevant to request/start/completion/failure as needed
- failure details or failure reason as needed
- retry-related metadata as needed

This may be represented through:
- fields added to the existing dispatch wave model
- a separate route-preparation tracking model
- or another explicit persistence model

The implementation choice is open, but the application must clearly represent route-preparation state.

### Route preparation statuses
The application must define statuses for the wave-level route-preparation lifecycle.

Expected route-preparation statuses:
- `PENDING`
- `IN_PROGRESS`
- `COMPLETED`
- `FAILED`

The implementation must make these states visible and meaningful for both application logic and API responses.

### Relationship to existing models
- a `DispatchWave` still belongs to one warehouse
- a `DispatchWave` still contains shipments assigned from that same warehouse
- route preparation is triggered **per dispatch wave**
- the route-preparation lifecycle must remain consistent with wave lifecycle and shipment lifecycle

## Status expectations in this iteration

### Shipment status expectations
Shipment statuses remain part of the model and continue to reflect shipment planning/execution state.

Expected shipment statuses:
- `CREATED`
- `READY`
- `ASSIGNED`
- `DISPATCHED`
- `CANCELLED`

### Dispatch wave status expectations
Dispatch wave statuses remain part of the model and continue to reflect the wave lifecycle.

Expected wave statuses:
- `PLANNING`
- `LOCKED`
- `DISPATCHED`
- `CANCELLED`

### Route-preparation status expectations
Route-preparation status is a separate workflow concern and must be tracked explicitly.

Expected route-preparation statuses:
- `PENDING`
- `IN_PROGRESS`
- `COMPLETED`
- `FAILED`

## Required endpoint additions
The system must introduce API support for route preparation at wave level.

Expected endpoint scope:
- an endpoint to trigger route preparation for a specific dispatch wave
- an endpoint to retrieve route-preparation state for a specific dispatch wave, or otherwise expose that state through an existing wave endpoint

Expected endpoint direction:
- route preparation should be requested in the context of a specific wave
- the API should make it clear that route preparation is not requested across multiple waves in this version

## Triggering requirements
- route preparation must be requestable only for an eligible dispatch wave
- triggering route preparation must not require the HTTP caller to wait for downstream completion inline
- once triggered, the application must persist or otherwise reliably represent that the work has been requested
- duplicate or conflicting route-preparation requests must be handled consistently

## Eligibility requirements
The implementation must define and enforce eligibility rules for route preparation.

At minimum, eligibility must consider:
- the current dispatch wave status
- whether the wave has assigned shipments
- whether route preparation has already been requested, is already running, or has already completed
- whether the wave is cancelled or otherwise no longer processable

## Background-processing requirements
- route-preparation work must execute outside the main request thread
- the application must distinguish between accepted work and finished work
- the application must define how many route-preparation tasks can be active or pending at a time
- the implementation must prevent the system from behaving as though background capacity is unlimited

## Downstream integration requirements
- route preparation depends on a downstream external service
- downstream communication may be slow or fail
- the application must define what kinds of downstream failures are retryable
- the application must define what constitutes a terminal failure
- the application must record the observable outcome of downstream processing

## Retry and backlog requirements
- work that cannot be completed immediately must not be silently dropped
- retryable failures must remain visible in application state
- the implementation must track whether work is pending retry, running, completed, or failed
- the application must define when repeated failure becomes final failure

## Shutdown and restart requirements
The implementation must define expected behavior for both running and pending background route-preparation work.

At minimum, it must be clear:
- what happens to currently running work during application shutdown
- what happens to work that has been accepted but not yet started
- what application state remains after restart
- what manual or automatic recovery behavior is expected after restart

## Error-handling requirements
- the API must provide clear responses when route preparation cannot be requested
- invalid wave state, duplicate request attempts, missing waves, and other business violations must be distinguishable
- failures during background execution must be visible through processing state rather than hidden behind the original trigger request

## Testing requirements
The implementation must add tests covering the route-preparation workflow.

At minimum, tests should verify:
- route preparation can be requested for an eligible wave
- ineligible waves are rejected correctly
- background processing changes route-preparation state correctly
- retryable and terminal failures are reflected correctly
- accepted work is not lost silently
- shutdown/restart expectations are documented and testable where practical

## Acceptance criteria
- route preparation can be triggered for a single eligible dispatch wave
- the request returns without waiting for all downstream processing to complete inline
- the application exposes route-preparation state clearly
- background work has bounded and observable lifecycle behavior
- downstream failure scenarios are visible and handled consistently
- implementation remains understandable, maintainable, and reviewable

## Evaluation focus
- clarity of wave-level route-preparation model
- consistency of statuses across planning and background processing
- API clarity for triggering and observing route preparation
- reliability of background workflow handling
- visibility of failure and backlog state
- quality of tests and operational reasoning


## Step 3 — Entity requirements

The implementation for this step must make the persistence requirements explicit. The goal of this step is not only to trigger background route preparation, but also to persist enough state so the workflow is observable, recoverable, and testable.

### Existing entities still in scope
The following entities remain part of the model and continue to participate in validation and workflow transitions:
- `Shipment`
- `DispatchWave`
- `Assignment`

### Additional persistence requirement for Step 3
The implementation must introduce an additional persistence concept dedicated to **route-preparation workflow tracking**.

This requirement exists because route preparation now has its own lifecycle that is separate from the basic dispatch-wave planning lifecycle. The system must be able to represent that route preparation was requested, is pending, is being processed, has completed, or has failed.

### What this additional persistence concept must support
The model introduced for route preparation must be able to represent at least:
- the target dispatch wave
- workflow status
- creation/request timestamp
- last update timestamp
- retry-related state where relevant
- failure visibility where relevant
- result visibility where relevant

### Important requirement boundary
For this iteration, the README requires a persistence concept for **route-preparation workflow**. It does **not** require a full transport-execution route model unless the implementation explicitly needs that level of detail.

That means:
- a dedicated persistence model for route-preparation workflow is required
- a rich first-class `Route` domain entity is optional in this iteration

### Relationship expectations
The implementation must clearly define how the route-preparation persistence concept relates to:
- `DispatchWave`
- shipment assignments already contained in that wave
- route-preparation status visibility exposed through the API

### Status ownership requirement
The implementation must make clear which statuses belong to:
- shipment lifecycle
- dispatch-wave lifecycle
- route-preparation lifecycle

These lifecycles must not be collapsed into one ambiguous status model.

### Documentation requirement
The implementation README and code structure must make it clear:
- which persistence objects exist in Step 3
- why the new route-preparation persistence concept is needed
- what business lifecycle it represents
- whether a full `Route` entity is intentionally out of scope for this iteration
