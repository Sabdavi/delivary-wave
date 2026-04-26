# Dispatch Wave Planning Service — Iteration 1 & 2 Review README

## Purpose of this document

This README is meant to evaluate the first **two iterations** of the Dispatch Wave Planning Service.

- **Iteration 1** focuses on a clean, production-style **synchronous functional core**.
- **Iteration 2** focuses on **correctness under concurrent access** and on identifying where the Iteration 1 design breaks when multiple planner actions happen at the same time.

This document can be used as:
- a self-review checklist before an interview
- a discussion guide during code review
- a way to explain design choices, known limitations, and the reasoning behind the concurrency changes

---

# Business context

The system supports warehouse transport planning.

A planner creates **dispatch waves** for a specific warehouse and dispatch time window, then assigns eligible **shipments** into those waves.

A dispatch wave acts as a planning bucket with limited crate capacity.

A shipment can only be assigned when it satisfies the business rules.

This first pair of iterations focuses on:
- creating shipments
- creating dispatch waves
- assigning shipments to a wave
- retrieving wave details
- retrieving wave summary
- making the assignment flow correct when multiple requests happen concurrently

---

# Iteration 1 — Functional synchronous core

## Scope

### Included
- REST API design for shipment and dispatch wave flows
- Request/response DTOs
- Validation of request fields
- Domain/business validation during assignment
- JPA entity modeling
- Transaction boundary for assignment
- Structured exception handling
- Simple summary retrieval

### Explicitly out of scope in Iteration 1
- asynchronous processing
- external route planning integration
- retries and backlog processing
- executor services and worker lifecycle
- distributed concurrency controls beyond one service instance
- optimistic/pessimistic locking strategies
- caching and performance optimization
- advanced search/list endpoints
- audit history beyond the minimum entity model

---

## Functional requirements

The service should support the following use cases.

### 1. Create shipment
A shipment represents goods that should be transported from a warehouse to a store.

### 2. Create dispatch wave
A dispatch wave represents a planning bucket for one warehouse and one dispatch time window.

### 3. Assign shipments to a dispatch wave
A planner assigns one or more shipments to a wave.

### 4. Read dispatch wave details
The client can retrieve a wave and inspect its configuration and assigned shipments.

### 5. Read dispatch wave summary
The client can retrieve business-oriented summary data for a wave.

---

## Business rules

The following rules should be enforced during shipment assignment:

1. Only shipments in `READY` status can be assigned.
2. Shipment warehouse must match dispatch wave warehouse.
3. A shipment can belong to at most one active wave.
4. A dispatch wave has a maximum crate capacity.
5. Assignment must fail if requested shipments would exceed the wave capacity.
6. Assignment is only allowed when wave status is `PLANNING`.

---

## API design

### Shipment endpoints

#### `POST /shipments`
Creates a shipment.

Example request:

```json
{
  "warehouseId": "WH-AMS-1",
  "storeId": "STORE-101",
  "plannedDepartureAt": "2026-04-22T14:30:00",
  "crateCount": 20,
  "status": "READY"
}
```

Example response:

```json
{
  "id": "a1f5d4d2-2ec0-4d6f-9d89-5a6a4f1b2c11",
  "warehouseId": "WH-AMS-1",
  "storeId": "STORE-101",
  "plannedDepartureAt": "2026-04-22T14:30:00",
  "crateCount": 20,
  "status": "READY",
  "createdAt": "2026-04-22T10:15:00"
}
```

### Dispatch wave endpoints

#### `POST /dispatch-waves`
Creates a new dispatch wave.

Example request:

```json
{
  "warehouseId": "WH-AMS-1",
  "dispatchWindowStart": "2026-04-22T14:00:00",
  "dispatchWindowEnd": "2026-04-22T15:00:00",
  "maxCrates": 100
}
```

Example response:

```json
{
  "id": "b5d4c1aa-1c33-4b11-8b4a-99d6fbc0d765",
  "warehouseId": "WH-AMS-1",
  "dispatchWindowStart": "2026-04-22T14:00:00",
  "dispatchWindowEnd": "2026-04-22T15:00:00",
  "maxCrates": 100,
  "status": "PLANNING",
  "createdAt": "2026-04-22T10:20:00"
}
```

#### `POST /dispatch-waves/{waveId}/assignments`
Assigns one or more shipments to a specific dispatch wave.

Example request:

```json
{
  "shipmentIds": [
    "a1f5d4d2-2ec0-4d6f-9d89-5a6a4f1b2c11",
    "b7a9d2e3-6fa0-4b6a-a2c2-8bc9d92ad901"
  ]
}
```

Example response:

```json
{
  "waveId": "b5d4c1aa-1c33-4b11-8b4a-99d6fbc0d765",
  "assignedShipmentIds": [
    "a1f5d4d2-2ec0-4d6f-9d89-5a6a4f1b2c11",
    "b7a9d2e3-6fa0-4b6a-a2c2-8bc9d92ad901"
  ],
  "totalAssignedShipments": 2,
  "totalAssignedCrates": 50,
  "remainingCapacity": 50
}
```

#### `GET /dispatch-waves/{waveId}`
Returns dispatch wave details.

#### `GET /dispatch-waves/{waveId}/summary`
Returns business summary for the wave.

---

## Controller design

A simple and clean controller split for this iteration is:

### `ShipmentController`
Responsible for shipment endpoints:
- `POST /shipments`

### `DispatchWaveController`
Responsible for wave-related endpoints:
- `POST /dispatch-waves`
- `POST /dispatch-waves/{waveId}/assignments`
- `GET /dispatch-waves/{waveId}`
- `GET /dispatch-waves/{waveId}/summary`

This keeps controllers small while preserving a resource-oriented API structure.

---

## Domain model

### Shipment
Represents goods to be sent from a warehouse to a store.

Suggested fields:
- `id`
- `warehouseId`
- `storeId`
- `plannedDepartureAt`
- `crateCount`
- `status`
- `createdAt`
- `updatedAt`

Suggested status values:
- `CREATED`
- `READY`
- `ASSIGNED`
- `DISPATCHED`
- `CANCELLED`

### DispatchWave
Represents a planning bucket for one warehouse and one dispatch time window.

Suggested fields:
- `id`
- `warehouseId`
- `dispatchWindowStart`
- `dispatchWindowEnd`
- `maxCrates`
- `status`
- `createdAt`
- `updatedAt`

Suggested status values:
- `PLANNING`
- `LOCKED`
- `DISPATCHED`

### Assignment
Represents the link between a shipment and a dispatch wave.

Suggested fields:
- `id`
- `shipmentId`
- `waveId`
- `assignedAt`

---

## Why `warehouseId` exists on both Shipment and DispatchWave

This is intentional and business-driven.

### On Shipment
A shipment originates from a specific warehouse independently of any assignment. It may exist before any wave is created.

### On DispatchWave
A dispatch wave belongs to a specific warehouse and exists before any shipment is assigned.

### Why this is useful
It makes the assignment rule explicit and easy to validate:

- `shipment.warehouseId == dispatchWave.warehouseId`

It also supports clear querying and avoids ambiguous designs where warehouse must be inferred indirectly.

---

## Entity modeling choice: separate `Assignment` entity

For this iteration, a separate `Assignment` entity is preferred over storing `waveId` directly on `Shipment`.

### Reasons
- clearer domain modeling
- explicit relationship between shipment and wave
- easier future extension for audit/history
- easier to enrich later with assignment-specific metadata
- better separation between shipment lifecycle and planning relationship

### Alternative considered
A simpler design is to add `waveId` directly to `Shipment`. That works for a minimal version but couples shipment state too tightly to the current wave relationship and leaves less room for future evolution.

---

## DTO design

Entities should not be exposed directly through the API.

Suggested DTOs for this iteration:

### Request DTOs
- `CreateShipmentRequest`
- `CreateDispatchWaveRequest`
- `AssignShipmentsRequest`

### Response DTOs
- `ShipmentResponse`
- `DispatchWaveResponse`
- `DispatchWaveDetailsResponse`
- `DispatchWaveSummaryResponse`
- `WaveAssignmentResponse`

This separation helps keep persistence concerns out of the API surface.

---

## Validation design

Validation should happen on two levels.

### 1. Request validation
Use bean validation for structural and field-level validation.

Examples:
- `warehouseId` not blank
- `storeId` not blank
- `plannedDepartureAt` not null
- `crateCount` positive
- `dispatchWindowStart` not null
- `dispatchWindowEnd` not null
- `maxCrates` positive
- `shipmentIds` not empty

A custom validation can be used to ensure:
- `dispatchWindowStart < dispatchWindowEnd`

### 2. Business validation
These checks belong in the service layer during assignment.

Examples:
- wave exists
- all shipments exist
- wave status is `PLANNING`
- shipment status is `READY`
- shipment warehouse matches wave warehouse
- shipment is not already assigned
- total assigned crates does not exceed wave capacity

---

## Service boundaries

A simple service split for this iteration could be:

### `ShipmentService`
- create shipment
- optionally retrieve shipment data needed by assignment flow

### `DispatchWaveService`
- create dispatch wave
- get wave details
- get wave summary
- assign shipments to wave

An alternative is to move assignment into a dedicated `WaveAssignmentService` if the assignment logic grows. For the first iteration, keeping it inside `DispatchWaveService` is acceptable if the class remains focused and readable.

---

## Transaction boundary

The assignment use case should be executed within a single transaction.

Suggested transactional method:

- `assignShipmentsToWave(waveId, shipmentIds)`

### Why this should be atomic
The following operations belong together:
- load and validate the wave
- load and validate the shipments
- calculate requested capacity
- create assignment records
- update shipment statuses

If any validation or persistence step fails, the entire assignment should roll back.

The controller should not own transactional behavior. The transaction boundary should be in the service layer.

---

## Exception handling

The implementation should expose structured, predictable API errors.

Suggested exception types:
- `ShipmentNotFoundException`
- `DispatchWaveNotFoundException`
- `InvalidShipmentStateException`
- `InvalidWaveStateException`
- `WarehouseMismatchException`
- `ShipmentAlreadyAssignedException`
- `WaveCapacityExceededException`

A global exception handler should map these into stable HTTP responses.

Suggested error response shape:

```json
{
  "timestamp": "2026-04-22T10:25:00Z",
  "status": 400,
  "errorCode": "WAVE_CAPACITY_EXCEEDED",
  "message": "Assignment exceeds dispatch wave capacity",
  "path": "/dispatch-waves/123/assignments"
}
```

---

## Suggested persistence constraints

Useful database constraints for this iteration include:
- primary keys on all entities
- non-null constraints on required fields
- positive numeric constraints where applicable
- a unique constraint on `assignment.shipment_id` if one shipment can belong to only one active assignment in this iteration

Note that more advanced modeling of active vs historical assignments can be introduced later if the business evolves.

---

## Summary calculation

The summary endpoint should return:
- total number of assigned shipments
- total assigned crates
- remaining capacity

This can be implemented either:
- by computing from assignments on read, or
- by maintaining derived totals

For Iteration 1, computing on read is acceptable because the focus is correctness and clarity rather than optimization.

---

## Suggested package structure

One clean option:

```text
controller/
  ShipmentController
  DispatchWaveController

dto/
  request/
  response/

entity/
  ShipmentEntity
  DispatchWaveEntity
  AssignmentEntity

repository/
  ShipmentRepository
  DispatchWaveRepository
  AssignmentRepository

service/
  ShipmentService
  DispatchWaveService

exception/
  ...

mapper/
  ...
```

The exact structure can vary, but responsibilities should remain clear.

---

## Iteration 1 self-review checklist

A good Iteration 1 implementation should satisfy most of the following:

- Controllers are resource-oriented and not overloaded.
- Entities reflect the business language.
- DTOs are separated from entities.
- Bean validation is used for request shape validation.
- Business rules are enforced in the service layer.
- Assignment runs in one transaction.
- API errors are structured and consistent.
- The model is simple, but not overly coupled.
- The design leaves room for a future concurrency-safe implementation.

---

# Iteration 2 — Concurrency and correctness under concurrent planner actions

## Why Iteration 2 exists

The Iteration 1 design is functionally correct in the normal case, but it is still vulnerable when multiple requests happen at the same time.

This iteration exists to answer one question:

> How do we prevent invalid assignments when planners or systems submit conflicting requests concurrently?

This is the first place where your backend design starts to look senior-level, because now you must think beyond “does it work?” and move into “does it stay correct under contention?”

---

## Concurrency scenarios you must handle

### Scenario A — same shipment assigned to two different waves
Two planners send requests at almost the same time:
- request 1 assigns shipment `S1` to wave `W1`
- request 2 assigns shipment `S1` to wave `W2`

Naive risk:
- both requests read `S1` as `READY`
- both see no assignment yet
- both insert an assignment
- shipment is double-booked

### Scenario B — same wave over capacity
Two planners assign shipments to the same wave concurrently:
- wave capacity is `100`
- request 1 assigns `40` crates
- request 2 assigns `70` crates
- both read current assigned crates as `0`
- both succeed
- final total becomes `110`

Naive risk:
- capacity is checked on stale state
- application-level validation alone is not sufficient

### Scenario C — wave state changes during assignment
One request is assigning shipments while another request locks or dispatches the wave.

Naive risk:
- the assignment flow starts when wave is `PLANNING`
- before commit, another transaction changes it to `LOCKED`
- depending on transaction design, invalid state transitions may slip through

### Scenario D — shipment state changes during assignment
A shipment is read as `READY`, but another transaction cancels or dispatches it before the first transaction commits.

Naive risk:
- final state may violate business invariants

---

## What you are going to do in Iteration 2

Iteration 2 is **not** about adding new business features. It is about making the existing assignment flow safe.

You are going to:

1. **Identify where Iteration 1 breaks under concurrency**
   - especially the check-then-act pattern
   - especially “read current state, then validate, then write” logic

2. **Choose a concurrency control strategy**
   - optimistic locking
   - pessimistic locking
   - database constraints
   - or a combination of them

3. **Move critical invariants closer to the database where needed**
   - because `synchronized` only protects a single JVM instance
   - and plain service-layer checks are not enough under concurrent transactions

4. **Adjust the transaction design**
   - decide which entities must be locked
   - decide in what order they should be read and locked
   - avoid race conditions without introducing unnecessary contention

5. **Design the API behavior for concurrency failures**
   - what HTTP status to return
   - how to make failures understandable to clients
   - whether clients can safely retry

6. **Add concurrency-focused tests**
   - not just unit tests, but tests that reveal race conditions or locking problems

---

## Key lesson of Iteration 2

This iteration teaches an important backend principle:

> Validation in Java code is necessary, but it is not sufficient when multiple transactions can observe and update the same rows concurrently.

A senior implementation uses the database and transaction model as part of the correctness strategy.

---

## What is likely unsafe in the Iteration 1 version

The following patterns are likely unsafe if implemented naively:

### 1. Read wave, calculate remaining capacity, then write
Unsafe because another transaction may assign more shipments before the current transaction commits.

### 2. Check `shipment.status == READY`, then create assignment
Unsafe because another transaction may assign or update the shipment first.

### 3. Check “assignment does not exist”, then insert
Unsafe because another transaction may insert the same assignment between the check and the insert.

### 4. Using `synchronized` in the service layer as the main protection
Unsafe because:
- it only protects one JVM instance
- it does not help if the service is scaled horizontally
- it does not coordinate with the database transaction state

You may still discuss local locking as a temporary educational mechanism, but not as the final production answer.

---

## Concurrency strategy options

You should be able to discuss the trade-offs of three common strategies.

### Option 1 — Optimistic locking
Add a `@Version` field to entities such as:
- `DispatchWave`
- possibly `Shipment`

Behavior:
- both transactions can read the same row
- at update time, one succeeds and one fails with an optimistic locking conflict

Benefits:
- low lock contention
- good when conflicts are relatively rare
- simpler to scale in many read-heavy systems

Costs:
- conflicts show up late, at commit/update time
- retry logic may be needed
- harder if multiple rows participate in one invariant

Where it may fit here:
- protecting wave state updates
- protecting aggregate-level counters if they are stored on the wave

### Option 2 — Pessimistic locking
Use repository queries with `PESSIMISTIC_WRITE` for rows that must not be modified concurrently.

Candidates:
- load `DispatchWave` with a write lock during assignment
- load target `Shipment` rows with write locks as part of the same transaction

Benefits:
- easier to reason about for strict correctness
- prevents concurrent modification while the transaction is in progress

Costs:
- more lock contention
- slower under high concurrency
- risk of deadlocks if lock acquisition order is inconsistent

Where it may fit here:
- assignment flow when correctness matters more than throughput
- especially in a planning system where conflicts are possible and data integrity is more important than maximal parallelism

### Option 3 — Database constraints plus transaction handling
Examples:
- unique constraint on `assignment.shipment_id`
- check constraints if applicable
- foreign keys and non-null constraints

Benefits:
- strongest final guardrail
- catches race conditions even if application logic misses them

Costs:
- exception-driven control flow if relied on too heavily
- may need careful mapping of DB exceptions into business errors

Where it fits here:
- as a mandatory safety net, regardless of optimistic or pessimistic locking choice

---

## Recommended direction for this scenario

For interview discussion, a strong answer is usually:

- use **database constraints as the final guardrail**
- use **transactional locking for the wave and relevant shipments** in the assignment flow
- keep the lock acquisition order stable
- optionally use **optimistic locking** where contention is lower or where aggregate updates are simpler to detect and retry

A practical iteration-2 solution for this scenario is:

1. lock the target wave row for update
2. lock the shipment rows being assigned, always in a stable order
3. validate status, warehouse, and wave state inside the same transaction
4. rely on a unique assignment constraint to prevent double-booking as a last line of defense
5. map concurrency failures to clear API responses

This is usually easier to justify than a purely optimistic strategy for a planning assignment flow.

---

## Entity and persistence changes for Iteration 2

### Add `@Version` where appropriate
Candidates:
- `DispatchWaveEntity`
- possibly `ShipmentEntity`

Even if you choose pessimistic locking, discussing versioning shows good modeling awareness.

### Add or verify database constraints
Recommended:
- unique constraint on `assignment.shipment_id`
- foreign keys from assignment to shipment and wave
- non-null constraints on fields used in invariants

### Consider storing derived counters on the wave
Optional in Iteration 2:
- `assignedCrates`
- `assignedShipmentCount`

This can make capacity checks more explicit, but it also introduces another concurrency-sensitive field.

For simplicity, you may still compute totals from assignments, but if you choose to store counters, you must explain how they stay consistent under concurrent updates.

---

## Service-layer changes for Iteration 2

The assignment service is the main focus.

A stronger Iteration 2 assignment flow would look like this conceptually:

1. Start transaction
2. Load and lock `DispatchWave`
3. Fail if wave is not `PLANNING`
4. Load and lock target shipments in deterministic order
5. Verify all shipments exist
6. Verify all shipments are `READY`
7. Verify all shipment warehouses match the wave warehouse
8. Verify shipments are not already assigned
9. Recalculate capacity inside the same transaction and fail if exceeded
10. Persist assignment rows
11. Update shipment statuses to `ASSIGNED`
12. Commit transaction

The important difference from Iteration 1 is not the steps themselves, but that they now happen with a concurrency-aware persistence strategy.

---

## Lock ordering and deadlock prevention

If you use pessimistic locking, explain how you avoid deadlocks.

A good answer is:
- always lock wave first
- then lock shipments in sorted ID order

This reduces the chance that two transactions lock rows in opposite order.

This kind of detail is exactly the sort of thing a senior interviewer may ask.

---

## Why `synchronized` is not enough

A common interview trap is to propose `synchronized` on the service method.

Why that is not enough:
- it only protects a single process
- it breaks once there are multiple service instances
- it does not coordinate with the database transaction model
- it cannot protect direct database modifications from other paths

A good answer is:

> `synchronized` may prevent some race conditions in a single-node demo, but the durable correctness boundary for this use case has to be the transaction and database constraints, because the service may scale horizontally and multiple transactions can still conflict at the database level.

---

## API behavior for concurrency conflicts

You should define how the API behaves when concurrent conflicts occur.

Possible mappings:
- `409 Conflict` for shipment already assigned, optimistic locking conflict, or wave no longer assignable
- `400 Bad Request` for pure business rule violations that are not concurrency-driven
- `404 Not Found` when requested wave or shipment does not exist

Recommended error examples:
- `SHIPMENT_ALREADY_ASSIGNED`
- `WAVE_CAPACITY_EXCEEDED`
- `WAVE_STATE_CONFLICT`
- `OPTIMISTIC_LOCK_CONFLICT`

The point is not the exact code names, but consistency and clarity.

---

## Testing expectations for Iteration 2

Iteration 2 needs stronger tests than Iteration 1.

### Unit tests
Still useful for:
- validation rules
- error mapping
- service branching logic

### Integration tests
Important for:
- repository locking behavior
- transaction boundaries
- constraint enforcement
- actual JPA/database interaction

### Concurrency-focused tests
Examples:

#### Test 1 — duplicate assignment race
- create one shipment in `READY`
- create two waves in same warehouse
- run two concurrent assignment attempts for the same shipment
- verify only one succeeds

#### Test 2 — capacity oversubscription race
- create one wave with capacity 100
- run two concurrent requests that individually fit but together exceed capacity
- verify only valid total assignment remains after both complete

#### Test 3 — wave state conflict
- start one assignment transaction
- concurrently lock the wave
- verify assignment fails or is rejected consistently depending on timing and locking strategy

A senior implementation usually benefits from integration tests with a real database, not just mocked repositories.

---

## Interview questions you should be ready for after Iteration 2

- Where exactly is the race condition in your Iteration 1 implementation?
- Why is a transactional method still not enough by itself?
- Why is `synchronized` not sufficient here?
- Would you use optimistic or pessimistic locking, and why?
- What rows do you lock during assignment?
- In what order do you lock them, and why?
- What database constraints do you rely on as guardrails?
- How do you prevent double assignment of a shipment?
- How do you prevent wave over-capacity under concurrent requests?
- What HTTP response should the client get for concurrency conflicts?
- Would your solution still work with two application instances?

---

## Iteration 2 self-review checklist

A good Iteration 2 implementation should satisfy most of the following:

- The race conditions in Iteration 1 are explicitly understood and documented.
- Assignment correctness no longer depends on check-then-act logic alone.
- The transaction boundary remains in the service layer.
- The chosen locking strategy is explicit and justified.
- Critical rows are locked or versioned consistently.
- Database constraints exist as a final correctness guardrail.
- Lock acquisition order is stable and explained.
- Concurrency failures are translated into meaningful API errors.
- The design is correct across multiple JVM instances, not just one.
- There are tests that actually exercise concurrent assignment conflicts.

---

## Known limitations after Iteration 2

Even after Iteration 2, the system is still not complete for a production-scale environment.

Still out of scope:
- asynchronous route preparation
- bounded executors and background workers
- retries, backoff, and backlog reprocessing
- graceful shutdown and restart semantics
- caching and summary optimization
- observability and metrics
- bulk throughput tuning
- distributed workflows or messaging

These belong to later iterations.

---

## What a strong implementation trajectory looks like

### After Iteration 1
You can say:
- the API is clean
- the domain model is explicit
- the service works functionally
- validation and transactions are in place

### After Iteration 2
You can say:
- the service remains correct under concurrent planner actions
- important invariants are enforced transactionally and at the database layer
- the implementation is moving from “works in happy path” to “safe under real operational pressure”

That shift is exactly the kind of maturity interviewers look for.
