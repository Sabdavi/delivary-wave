package com.deliverywave.service

import com.deliverywave.entity.DispatchEntity
import com.deliverywave.entity.ShipmentAssignmentsEntity
import com.deliverywave.entity.ShipmentEntity
import com.deliverywave.exception.DatesAreNotValidException
import com.deliverywave.exception.DispatchWaveStateException
import com.deliverywave.exception.DuplicateShipmentFoundException
import com.deliverywave.exception.NotDispatchWaveFoundException
import com.deliverywave.exception.NotEnoughCapacityException
import com.deliverywave.exception.ShipmentNotFoundException
import com.deliverywave.extension.toEntity
import com.deliverywave.extension.toResponse
import com.deliverywave.model.*
import com.deliverywave.repository.DispatchRepository
import com.deliverywave.repository.ShipmentAssignmentRepository
import com.deliverywave.repository.ShipmentRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.*

@Service
class DispatchService(
    private val dispatchRepository: DispatchRepository,
    private val shipmentRepository: ShipmentRepository,
    private val shipmentAssignmentRepository: ShipmentAssignmentRepository
) {

    @Transactional
    fun createDispatch(dispatchWaveRequest: DispatchWaveRequest): DispatchWaveResponse {
        validateDispatchRequest(dispatchWaveRequest)
        val dispatchEntity = dispatchWaveRequest.toEntity()
        val savedEntity = dispatchRepository.save(dispatchEntity)
        return savedEntity.toResponse()
    }

    private fun validateDispatchRequest(dispatchWaveRequest: DispatchWaveRequest) {
        if (dispatchWaveRequest.dispatchWindowStart.isAfter(dispatchWaveRequest.dispatchWindowEnd)) {
            throw DatesAreNotValidException("dispatchWindowEnd should be after dispatchWindowStart")
        }
    }

    @Transactional(readOnly = true)
    fun getDispatch(waveId: UUID): DispatchWaveResponse {
        val dispatch = findDispatchOrThrow(waveId)
        return dispatch.toResponse()
    }

    @Transactional(readOnly = true)
    fun getDispatchSummary(waveId: UUID): DispatchWaveSummaryResponse {
        val dispatch = findDispatchOrThrow(waveId)
        val assignedShipments = dispatch.shipmentAssignments.flatMap { it.shipments }
        val totalAssignedCrates = assignedShipments.sumOf { it.crate }

        return DispatchWaveSummaryResponse(
            waveId = dispatch.id.toString(),
            warehouseId = dispatch.warehouseId,
            totalAssignments = dispatch.shipmentAssignments.size,
            totalAssignedShipments = assignedShipments.size,
            totalAssignedCrates = totalAssignedCrates,
            maxCrate = dispatch.maxCrate,
            remainingCapacity = dispatch.maxCrate - totalAssignedCrates,
        )
    }

    @Transactional
    fun assignShipment(
        waveId: UUID,
        shipmentAssignmentRequest: ShipmentAssignmentRequest
    ): ShipmentAssignmentResponse {

        // 1. Lock the wave row — no other transaction can modify it until we commit
        val dispatchWave = findDispatchForUpdateOrThrow(waveId)

        // 2. Lock shipment rows in sorted order — prevents deadlocks
        val sortedShipmentIds = shipmentAssignmentRequest.shipments.sorted()
        val shipmentEntities = shipmentRepository.findAllByIdForUpdate(sortedShipmentIds)

        // 3. All validation happens on locked rows — data is guaranteed fresh
        validateAssignment(dispatchWave, shipmentEntities, shipmentAssignmentRequest.shipments)

        // 4. Safe to mutate — we hold exclusive locks on all relevant rows
        val shipmentAssignmentsEntity =
            ShipmentAssignmentsEntity(assignedAt = Instant.now(), dispatchWave = dispatchWave)
        shipmentEntities.forEach { shipmentAssignmentsEntity.addShipment(it) }
        shipmentEntities.forEach { it.shipmentStatus = ShipmentStatus.ASSIGNED }

        shipmentAssignmentRepository.save(shipmentAssignmentsEntity)

        // 5. Calculate remaining capacity
        val assignedCapacity = dispatchWave.shipmentAssignments.flatMap { it.shipments }.sumOf { it.crate }
        val remainingCapacity = dispatchWave.maxCrate - assignedCapacity
        val requestedCapacity = shipmentEntities.sumOf { it.crate }

        return ShipmentAssignmentResponse(
            shipmentAssignmentsEntity.id.toString(),
            assignedShipmentIds = shipmentEntities.map { it.id.toString() },
            totalAssignmentShipments = shipmentEntities.size,
            totalAssignedCrates = requestedCapacity,
            remainingCapacity = remainingCapacity - requestedCapacity
        )
        // 6. Transaction commits here — all locks released
    }

    private fun validateAssignment(
        dispatchEntity: DispatchEntity,
        shipmentEntities: List<ShipmentEntity>,
        shipmentRequestIds: List<UUID>
    ) {
        validateShipmentExistence(shipmentEntities, shipmentRequestIds)
        validateWaveStatus(dispatchEntity)
        validateShipmentStatus(shipmentEntities)
        validateCapacity(dispatchEntity, shipmentEntities)
        validateDuplicateShipments(dispatchEntity, shipmentEntities)
    }

    private fun validateWaveStatus(dispatchEntity: DispatchEntity) {
        if (dispatchEntity.waveStatus != WaveStatus.PLANNING) {
            throw DispatchWaveStateException("Wave is not in PLANNING status, current status: ${dispatchEntity.waveStatus}")
        }
    }

    private fun validateShipmentStatus(shipmentEntities: List<ShipmentEntity>) {
        val notReadyShipments = shipmentEntities.filter { it.shipmentStatus != ShipmentStatus.READY }
        if (notReadyShipments.isNotEmpty()) {
            val ids = notReadyShipments.map { it.id }.joinToString(", ")
            throw DuplicateShipmentFoundException("Shipments not in READY status: $ids")
        }
    }

    private fun validateShipmentExistence(
        shipmentEntities: List<ShipmentEntity>,
        shipmentRequests: List<UUID>
    ) {
        val existingShipments = shipmentEntities.map { it.id }.toSet()
        val notExistingShipmentsIds = shipmentRequests.filterNot { it in existingShipments }
        if (notExistingShipmentsIds.isNotEmpty()) {
            throw ShipmentNotFoundException("shipments not found $notExistingShipmentsIds")
        }
    }

    private fun validateDuplicateShipments(
        dispatchEntity: DispatchEntity,
        shipmentEntities: List<ShipmentEntity>
    ) {
        val assignedShipments = dispatchEntity.shipmentAssignments.flatMap { it.shipments }.map { it.id }.toSet()
        val requestedAssignments = shipmentEntities.map { it.id }

        val duplicatedShipmentIds = requestedAssignments.filter { it in assignedShipments }
        if (duplicatedShipmentIds.isNotEmpty()) {
            val duplicateIds = duplicatedShipmentIds.joinToString(", ")
            throw DuplicateShipmentFoundException("duplicate shipment ids found: $duplicateIds")
        }
    }

    private fun validateCapacity(
        dispatchEntity: DispatchEntity,
        shipmentEntities: List<ShipmentEntity>
    ) {
        val usedCapacity = dispatchEntity.shipmentAssignments.flatMap { it.shipments }.sumOf { it.crate }
        val requestedCapacity = shipmentEntities.sumOf { it.crate }

        if (requestedCapacity > dispatchEntity.maxCrate - usedCapacity) {
            throw NotEnoughCapacityException("Not enough capacity available")
        }
    }

    private fun findDispatchOrThrow(waveId: UUID) =
        dispatchRepository.findById(waveId)
            .orElseThrow { NotDispatchWaveFoundException("Dispatch wave not found: $waveId") }

    private fun findDispatchForUpdateOrThrow(waveId: UUID) =
        dispatchRepository.findByIdForUpdate(waveId)
            ?: throw NotDispatchWaveFoundException("Dispatch wave not found: $waveId")
}