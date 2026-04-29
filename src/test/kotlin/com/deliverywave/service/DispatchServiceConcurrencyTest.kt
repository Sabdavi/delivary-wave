package com.deliverywave.service

import com.deliverywave.entity.DispatchEntity
import com.deliverywave.entity.ShipmentEntity
import com.deliverywave.model.ShipmentAssignmentRequest
import com.deliverywave.model.ShipmentStatus
import com.deliverywave.model.WaveStatus
import com.deliverywave.repository.DispatchRepository
import com.deliverywave.repository.ShipmentAssignmentRepository
import com.deliverywave.repository.ShipmentRepository
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import java.time.Instant
import java.util.*
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit

@SpringBootTest
@ActiveProfiles("test")
class DispatchServiceConcurrencyTest {

    @Autowired
    private lateinit var dispatchService: DispatchService

    @Autowired
    private lateinit var dispatchRepository: DispatchRepository

    @Autowired
    private lateinit var shipmentRepository: ShipmentRepository

    @Autowired
    private lateinit var shipmentAssignmentRepository: ShipmentAssignmentRepository

    @BeforeEach
    fun setUp() {
        shipmentAssignmentRepository.deleteAll()
        shipmentRepository.deleteAll()
        dispatchRepository.deleteAll()
    }

    // ──────────────────────────────────────────────────────────────────────
    // Scenario A — Same shipment assigned to two different waves
    // ──────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("Scenario A: same shipment cannot be assigned to two waves concurrently")
    fun scenarioA_sameShipmentAssignedToTwoWaves_onlyOneSucceeds() {
        // Given: one shipment in READY status
        val shipment = shipmentRepository.save(
            ShipmentEntity(
                warehouseId = "WH-1",
                storeId = "STORE-1",
                plannedDepartureAt = Instant.now().plusSeconds(3600),
                crate = 20,
                shipmentStatus = ShipmentStatus.READY
            )
        )

        // Given: two waves in the same warehouse
        val wave1 = dispatchRepository.save(
            DispatchEntity(
                warehouseId = "WH-1",
                dispatchWindowStart = Instant.now(),
                dispatchWindowEnd = Instant.now().plusSeconds(7200),
                maxCrate = 100,
                waveStatus = WaveStatus.PLANNING
            )
        )
        val wave2 = dispatchRepository.save(
            DispatchEntity(
                warehouseId = "WH-1",
                dispatchWindowStart = Instant.now(),
                dispatchWindowEnd = Instant.now().plusSeconds(7200),
                maxCrate = 100,
                waveStatus = WaveStatus.PLANNING
            )
        )

        // When: two concurrent requests try to assign the same shipment to different waves
        val results = executeConcurrently(
            { dispatchService.assignShipment(wave1.id!!, ShipmentAssignmentRequest(listOf(shipment.id!!))) },
            { dispatchService.assignShipment(wave2.id!!, ShipmentAssignmentRequest(listOf(shipment.id!!))) }
        )

        // Then: exactly one succeeds and one fails
        val successes = results.count { it.isSuccess }
        val failures = results.count { it.isFailure }

        assertThat(successes).isEqualTo(1)
        assertThat(failures).isEqualTo(1)

        // And: shipment is ASSIGNED
        val updatedShipment = shipmentRepository.findById(shipment.id!!).get()
        assertThat(updatedShipment.shipmentStatus).isEqualTo(ShipmentStatus.ASSIGNED)

        // And: only one assignment exists
        assertThat(shipmentAssignmentRepository.count()).isEqualTo(1)
    }

    // ──────────────────────────────────────────────────────────────────────
    // Scenario B — Same wave over capacity
    // ──────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("Scenario B: concurrent assignments cannot exceed wave capacity")
    fun scenarioB_sameWaveOverCapacity_onlyOneSucceeds() {
        // Given: a wave with capacity 100
        val wave = dispatchRepository.save(
            DispatchEntity(
                warehouseId = "WH-1",
                dispatchWindowStart = Instant.now(),
                dispatchWindowEnd = Instant.now().plusSeconds(7200),
                maxCrate = 100,
                waveStatus = WaveStatus.PLANNING
            )
        )

        // Given: two shipments that individually fit but together exceed capacity
        val shipment1 = shipmentRepository.save(
            ShipmentEntity(
                warehouseId = "WH-1",
                storeId = "STORE-1",
                plannedDepartureAt = Instant.now().plusSeconds(3600),
                crate = 70,
                shipmentStatus = ShipmentStatus.READY
            )
        )
        val shipment2 = shipmentRepository.save(
            ShipmentEntity(
                warehouseId = "WH-1",
                storeId = "STORE-2",
                plannedDepartureAt = Instant.now().plusSeconds(3600),
                crate = 40,
                shipmentStatus = ShipmentStatus.READY
            )
        )

        // When: two concurrent requests assign different shipments to the same wave
        val results = executeConcurrently(
            { dispatchService.assignShipment(wave.id!!, ShipmentAssignmentRequest(listOf(shipment1.id!!))) },
            { dispatchService.assignShipment(wave.id!!, ShipmentAssignmentRequest(listOf(shipment2.id!!))) }
        )

        // Then: exactly one succeeds and one fails
        val successes = results.count { it.isSuccess }
        val failures = results.count { it.isFailure }

        assertThat(successes).isEqualTo(1)
        assertThat(failures).isEqualTo(1)

        // And: total assigned crates do not exceed capacity
        val updatedWave = dispatchRepository.findById(wave.id!!).get()
        val totalCrates = updatedWave.shipmentAssignments.flatMap { it.shipments }.sumOf { it.crate }
        assertThat(totalCrates).isLessThanOrEqualTo(100)
    }

    // ──────────────────────────────────────────────────────────────────────
    // Scenario C — Wave state changes during assignment
    // ──────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("Scenario C: assignment fails when wave is not in PLANNING status")
    fun scenarioC_waveStateChangedToLocked_assignmentFails() {
        // Given: a wave in LOCKED status
        val wave = dispatchRepository.save(
            DispatchEntity(
                warehouseId = "WH-1",
                dispatchWindowStart = Instant.now(),
                dispatchWindowEnd = Instant.now().plusSeconds(7200),
                maxCrate = 100,
                waveStatus = WaveStatus.LOCKED
            )
        )

        val shipment = shipmentRepository.save(
            ShipmentEntity(
                warehouseId = "WH-1",
                storeId = "STORE-1",
                plannedDepartureAt = Instant.now().plusSeconds(3600),
                crate = 20,
                shipmentStatus = ShipmentStatus.READY
            )
        )

        // When: trying to assign to a locked wave
        val result = runCatching {
            dispatchService.assignShipment(wave.id!!, ShipmentAssignmentRequest(listOf(shipment.id!!)))
        }

        // Then: assignment is rejected
        assertThat(result.isFailure).isTrue()

        // And: no assignment was created
        assertThat(shipmentAssignmentRepository.count()).isEqualTo(0)

        // And: shipment remains READY
        val updatedShipment = shipmentRepository.findById(shipment.id!!).get()
        assertThat(updatedShipment.shipmentStatus).isEqualTo(ShipmentStatus.READY)
    }

    // ──────────────────────────────────────────────────────────────────────
    // Scenario D — Shipment state changes during assignment
    // ──────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("Scenario D: assignment fails when shipment is not in READY status")
    fun scenarioD_shipmentAlreadyAssigned_secondAssignmentFails() {
        // Given: a shipment that is already ASSIGNED
        val shipment = shipmentRepository.save(
            ShipmentEntity(
                warehouseId = "WH-1",
                storeId = "STORE-1",
                plannedDepartureAt = Instant.now().plusSeconds(3600),
                crate = 20,
                shipmentStatus = ShipmentStatus.ASSIGNED
            )
        )

        val wave = dispatchRepository.save(
            DispatchEntity(
                warehouseId = "WH-1",
                dispatchWindowStart = Instant.now(),
                dispatchWindowEnd = Instant.now().plusSeconds(7200),
                maxCrate = 100,
                waveStatus = WaveStatus.PLANNING
            )
        )

        // When: trying to assign an already-assigned shipment
        val result = runCatching {
            dispatchService.assignShipment(wave.id!!, ShipmentAssignmentRequest(listOf(shipment.id!!)))
        }

        // Then: assignment is rejected
        assertThat(result.isFailure).isTrue()

        // And: no assignment was created
        assertThat(shipmentAssignmentRepository.count()).isEqualTo(0)
    }

    // ──────────────────────────────────────────────────────────────────────
    // Scenario A+B combined — multiple concurrent assignments
    // ──────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("Multiple concurrent assignments to same wave respect capacity")
    fun multipleConcurrentAssignments_respectCapacity() {
        // Given: a wave with capacity 50
        val wave = dispatchRepository.save(
            DispatchEntity(
                warehouseId = "WH-1",
                dispatchWindowStart = Instant.now(),
                dispatchWindowEnd = Instant.now().plusSeconds(7200),
                maxCrate = 50,
                waveStatus = WaveStatus.PLANNING
            )
        )

        // Given: 5 shipments of 20 crates each (total 100, only 2-3 can fit)
        val shipments = (1..5).map { i ->
            shipmentRepository.save(
                ShipmentEntity(
                    warehouseId = "WH-1",
                    storeId = "STORE-$i",
                    plannedDepartureAt = Instant.now().plusSeconds(3600),
                    crate = 20,
                    shipmentStatus = ShipmentStatus.READY
                )
            )
        }

        // When: 5 concurrent requests each try to assign one shipment
        val executor = Executors.newFixedThreadPool(5)
        val latch = CountDownLatch(1)

        val futures = shipments.map { shipment ->
            executor.submit<Result<Any>> {
                latch.await() // all threads start together
                runCatching {
                    dispatchService.assignShipment(wave.id!!, ShipmentAssignmentRequest(listOf(shipment.id!!)))
                }
            }
        }

        latch.countDown() // release all threads
        val results = futures.map { it.get(10, TimeUnit.SECONDS) }
        executor.shutdown()

        // Then: total assigned crates never exceed capacity
        val updatedWave = dispatchRepository.findById(wave.id!!).get()
        val totalCrates = updatedWave.shipmentAssignments.flatMap { it.shipments }.sumOf { it.crate }
        assertThat(totalCrates).isLessThanOrEqualTo(50)

        // And: at most 2 assignments succeeded (2 × 20 = 40 ≤ 50, 3 × 20 = 60 > 50)
        val successes = results.count { it.isSuccess }
        assertThat(successes).isBetween(1, 2)

        // And: all assigned shipments have ASSIGNED status
        val assignedShipments = shipmentRepository.findAll().filter { it.shipmentStatus == ShipmentStatus.ASSIGNED }
        assertThat(assignedShipments.size).isEqualTo(successes)
    }

    // ──────────────────────────────────────────────────────────────────────
    // Helper: execute two tasks concurrently with a latch for synchronization
    // ──────────────────────────────────────────────────────────────────────

    private fun <T> executeConcurrently(
        task1: () -> T,
        task2: () -> T
    ): List<Result<T>> {
        val executor = Executors.newFixedThreadPool(2)
        val latch = CountDownLatch(1) // ensures both threads start at the same time

        val future1: Future<Result<T>> = executor.submit<Result<T>> {
            latch.await()
            runCatching { task1() }
        }

        val future2: Future<Result<T>> = executor.submit<Result<T>> {
            latch.await()
            runCatching { task2() }
        }

        latch.countDown() // release both threads simultaneously

        val results = listOf(
            future1.get(10, TimeUnit.SECONDS),
            future2.get(10, TimeUnit.SECONDS)
        )

        executor.shutdown()
        return results
    }
}

