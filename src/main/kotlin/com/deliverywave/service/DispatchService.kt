package com.deliverywave.service

import com.deliverywave.entity.DispatchEntity
import com.deliverywave.entity.ShipmentAssignmentsEntity
import com.deliverywave.entity.ShipmentEntity
import com.deliverywave.exception.DatesAreNotValidException
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
    open fun getDispatch(waveId: UUID): DispatchWaveResponse {
        val dispatch = findDispatchOrThrow(waveId)
        return dispatch.toResponse()
    }

    @Transactional(readOnly = true)
    open fun getDispatchSummary(waveId: UUID): DispatchWaveSummaryResponse {
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
    open fun assignShipment(
        waveId: UUID,
        shipmentAssignmentRequest: ShipmentAssignmentRequest
    ): ShipmentAssignmentResponse {

        val dispatchWave = findDispatchOrThrow(waveId)
        val shipmentEntities = shipmentRepository.findAllById(shipmentAssignmentRequest.shipments)
        val shipmentAssignmentsEntity =
            ShipmentAssignmentsEntity(assignedAt = Instant.now(), dispatchWave = dispatchWave)

        validateAssignment(dispatchWave, shipmentEntities, shipmentAssignmentRequest.shipments)
        shipmentEntities.forEach { shipmentAssignmentsEntity.addShipment(it) }

        val assignedCapacity = dispatchWave.shipmentAssignments.flatMap { it.shipments }.sumOf { it.crate }
        val remainingCapacity = dispatchWave.maxCrate - assignedCapacity
        val requestedCapacity = shipmentEntities.sumOf { it.crate }

        shipmentAssignmentRepository.save(shipmentAssignmentsEntity)


        return ShipmentAssignmentResponse(
            shipmentAssignmentsEntity.id.toString(),
            assignedShipmentIds = shipmentEntities.map { it.id.toString() },
            totalAssignmentShipments = shipmentEntities.size,
            totalAssignedCrates = shipmentEntities.sumOf { it.crate },
            remainingCapacity = remainingCapacity - requestedCapacity
        )
    }

    private fun validateAssignment(
        dispatchEntity: DispatchEntity,
        shipmentEntities: List<ShipmentEntity>,
        shipmentRequestIds: List<UUID>
    ) {
        validateShipmentExistence(shipmentEntities, shipmentRequestIds)
        validateCapacity(dispatchEntity, shipmentEntities)
        validateDuplicateShipments(dispatchEntity, shipmentEntities)
    }

    private fun validateShipmentExistence(
        shipmentEntities: List<ShipmentEntity>,
        shipmentRequests: List<UUID>
    ) {
        val existingShipments = shipmentEntities.map { it.id }.toSet()
        val notExistingShipmentsIds = shipmentRequests.filterNot {it in existingShipments }
        if(notExistingShipmentsIds.isNotEmpty()) {
            throw ShipmentNotFoundException("shipments not found $notExistingShipmentsIds")
        }
    }

    private fun validateDuplicateShipments(
        dispatchEntity: DispatchEntity,
        shipmentEntities: List<ShipmentEntity>
    ) {
        val assignedShipments = dispatchEntity.shipmentAssignments.flatMap { it.shipments }.map { it.id }.toSet()
        val requestedAssignments = shipmentEntities.map { it.id }

        val duplicatedShipmentIds = requestedAssignments.filter { assignedShipments.contains(it) }
        if (duplicatedShipmentIds.isNotEmpty()) {
            val duplicateIds = duplicatedShipmentIds.joinToString(", ")
            throw DuplicateShipmentFoundException("duplicate shipment ids found : $duplicateIds")
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
}