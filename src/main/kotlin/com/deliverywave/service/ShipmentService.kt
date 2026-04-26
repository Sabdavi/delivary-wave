package com.deliverywave.service

import com.deliverywave.extension.toEntity
import com.deliverywave.extension.toResponse
import com.deliverywave.model.ShipmentRequest
import com.deliverywave.model.ShipmentResponse
import com.deliverywave.repository.ShipmentRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class ShipmentService(
    private val repository: ShipmentRepository,
) {
    @Transactional
    fun createShipment(shipmentRequest: ShipmentRequest): ShipmentResponse {
        val shipmentEntity = shipmentRequest.toEntity()
        val savedEntity = repository.save(shipmentEntity)
        return savedEntity.toResponse()
    }
}