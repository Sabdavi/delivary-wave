package com.deliverywave.controller

import com.deliverywave.model.ShipmentRequest
import com.deliverywave.model.ShipmentResponse
import com.deliverywave.service.ShipmentService
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController()
@RequestMapping("/shipments")
class ShipmentController(
    private val shipmentService: ShipmentService,
) {

    @PostMapping
    fun createShipment(@Valid @RequestBody shipmentRequest: ShipmentRequest ) : ResponseEntity<ShipmentResponse> {
        val createShipment = shipmentService.createShipment(shipmentRequest)
        return ResponseEntity.status(HttpStatus.CREATED).body(createShipment)
    }
}