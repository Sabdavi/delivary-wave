# Delivery Wave

A Spring Boot microservice for managing dispatch waves and shipment assignments in a warehouse delivery context. Built with Kotlin and Java 21.

## Domain Overview

This service models the dispatch process for a grocery delivery warehouse (e.g., Albert Heijn). The core concepts are:

- **Shipment** — A delivery destined for a specific store, containing a number of crates. Each shipment has a planned departure time and a lifecycle status (`PLANNED`, `IN_PROGRESS`, `DELIVERED`).
- **Dispatch Wave** — A time-bounded batch of shipments dispatched from a warehouse. Each wave has a maximum crate capacity.
- **Shipment Assignment** — The act of assigning one or more shipments to a dispatch wave. Assignments are validated against the wave's remaining crate capacity and checked for duplicates.

## API Endpoints

### Shipments

#### Create Shipment
```
POST /shipments
```
**Request:**
```json
{
  "warehouseId": "WH-AMS-1",
  "storeId": "STORE-101",
  "plannedDepartureAt": "2026-04-22T14:30:00Z",
  "crateCount": 20,
  "status": "PLANNED"
}
```
**Response:** `201 Created`
```json
{
  "id": "a1b2c3d4-...",
  "warehouseId": "WH-AMS-1",
  "storeId": "STORE-101",
  "plannedDepartureAt": "2026-04-22T14:30:00Z",
  "crate": 20,
  "status": "PLANNED"
}
```

### Dispatch Waves

#### Create Dispatch Wave
```
POST /dispatch-waves
```
**Request:**
```json
{
  "warehouseId": "WH-AMS-1",
  "dispatchWindowStart": "2026-04-22T06:00:00Z",
  "dispatchWindowEnd": "2026-04-22T12:00:00Z",
  "maxCrate": 100
}
```
**Response:** `201 Created`
```json
{
  "id": "e5f6g7h8-...",
  "warehouseId": "WH-AMS-1",
  "dispatchWindowStart": "2026-04-22T06:00:00Z",
  "dispatchWindowEnd": "2026-04-22T12:00:00Z",
  "maxCrate": 100
}
```

#### Get Dispatch Wave
```
GET /dispatch-waves/{waveId}
```
**Response:** `200 OK` — same structure as create response.

#### Get Dispatch Wave Summary
```
GET /dispatch-waves/{waveId}/summary
```
**Response:** `200 OK`
```json
{
  "waveId": "e5f6g7h8-...",
  "warehouseId": "WH-AMS-1",
  "totalAssignments": 2,
  "totalAssignedShipments": 5,
  "totalAssignedCrates": 75,
  "maxCrate": 100,
  "remainingCapacity": 25
}
```

#### Assign Shipments to Wave
```
POST /dispatch-waves/{waveId}/assignments
```
**Request:**
```json
{
  "shipments": [
    "a1b2c3d4-...",
    "b2c3d4e5-..."
  ]
}
```
**Response:** `201 Created`
```json
{
  "id": "f6g7h8i9-...",
  "assignedShipmentIds": ["a1b2c3d4-...", "b2c3d4e5-..."],
  "totalAssignmentShipments": 2,
  "totalAssignedCrates": 40,
  "remainingCapacity": 60
}
```

## Validation Rules

| Rule | Endpoint | Error |
|------|----------|-------|
| `warehouseId` must not be blank | Create shipment, Create wave | 400 Bad Request |
| `storeId` must not be blank | Create shipment | 400 Bad Request |
| `crateCount` must be positive | Create shipment | 400 Bad Request |
| `maxCrate` must be positive | Create wave | 400 Bad Request |
| `dispatchWindowEnd` must be after `dispatchWindowStart` | Create wave | 400 Bad Request |
| Total assigned crates must not exceed `maxCrate` | Assign shipments | 400 Bad Request |
| Shipments already assigned to the wave are rejected | Assign shipments | 400 Bad Request |

## Tech Stack

- **Language:** Kotlin 1.9.25
- **Runtime:** Java 21
- **Framework:** Spring Boot 3.3.5
- **Persistence:** Spring Data JPA + Hibernate
- **Database:** MySQL
- **Build:** Maven
- **Testing:** JUnit 5 + Mockito-Kotlin + Spring MockMvc

## Prerequisites

- Java 21
- Maven 3.8+
- MySQL 8+ running on `localhost:3306`

## Database Setup

Create the database and user:

```sql
CREATE DATABASE ah;
CREATE USER 'ah'@'localhost' IDENTIFIED BY 'ah123!';
GRANT ALL PRIVILEGES ON ah.* TO 'ah'@'localhost';
```

Hibernate will auto-create/update tables on startup (`ddl-auto: update`).

## Quick Start

```bash
# Build and run tests
mvn clean test

# Run the application
mvn spring-boot:run
```

The application starts on `http://localhost:8080`.

## Project Structure

```
src/main/kotlin/com/deliverywave/
├── App.kt                          # Application entry point
├── controller/
│   ├── DispatchWaveController.kt   # Dispatch wave REST endpoints
│   └── ShipmentController.kt       # Shipment REST endpoints
├── entity/
│   ├── DispatchEntity.kt           # Dispatch wave JPA entity
│   ├── ShipmentAssignmentsEntity.kt# Assignment join entity
│   ├── ShipmentEntity.kt           # Shipment JPA entity
│   └── ShipmentItemEntity.kt       # Shipment item JPA entity
├── exception/
│   ├── DatesAreNotValidException.kt
│   ├── DuplicateShipmentFoundException.kt
│   ├── ErrorMessage.kt             # Error response DTO
│   ├── GlobalExceptionHandler.kt   # Centralized error handling
│   ├── NotDispatchWaveFoundException.kt
│   └── NotEnoughCapacityException.kt
├── extension/
│   ├── DispatcherExtension.kt      # DispatchEntity <-> DTO mapping
│   └── ShipmentExtention.kt        # ShipmentEntity <-> DTO mapping
├── model/
│   ├── DispatchWaveRequest.kt
│   ├── DispatchWaveResponse.kt
│   ├── DispatchWaveSummaryResponse.kt
│   ├── ShipmentAssignmentRequest.kt
│   ├── ShipmentAssignmentResponse.kt
│   ├── ShipmentRequest.kt
│   ├── ShipmentResponse.kt
│   └── Status.kt                   # Shipment status enum
├── repository/
│   ├── DispatchRepository.kt
│   ├── ShipmentAssignmentRepository.kt
│   └── ShipmentRepository.kt
└── service/
    ├── DispatchService.kt          # Dispatch wave business logic
    └── ShipmentService.kt          # Shipment business logic
```

## Design Decisions

- **Extension functions for mapping** — Entity-to-DTO and DTO-to-entity conversions use Kotlin extension functions, keeping services clean and mapping logic reusable.
- **ShipmentAssignmentsEntity as a join entity** — Rather than a direct many-to-many between dispatch waves and shipments, a dedicated assignment entity captures the `assignedAt` timestamp.
- **Validation at multiple levels** — Jakarta Bean Validation on DTOs for field-level constraints, plus business validation in the service layer for capacity and duplicate checks.
- **Centralized exception handling** — `@RestControllerAdvice` maps domain exceptions to consistent HTTP error responses.
