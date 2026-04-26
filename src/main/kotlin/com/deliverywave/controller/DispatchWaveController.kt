package com.deliverywave.controller

import com.deliverywave.model.*
import com.deliverywave.service.DispatchService
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import java.util.*

@RestController
@RequestMapping("/dispatch-waves")
class DispatchWaveController(
    private val dispatchService: DispatchService
) {

    @PostMapping
    fun createDispatchWave(@Valid @RequestBody dispatchWaveRequest: DispatchWaveRequest): ResponseEntity<DispatchWaveResponse> {
        val response = dispatchService.createDispatch(dispatchWaveRequest)
        return ResponseEntity.status(HttpStatus.CREATED).body(response)
    }

    @GetMapping("/{waveId}")
    fun getDispatchWave(@PathVariable waveId: UUID): ResponseEntity<DispatchWaveResponse> {
        val response = dispatchService.getDispatch(waveId)
        return ResponseEntity.ok(response)
    }

    @GetMapping("/{waveId}/summary")
    fun getDispatchWaveSummary(@PathVariable waveId: UUID): ResponseEntity<DispatchWaveSummaryResponse> {
        val response = dispatchService.getDispatchSummary(waveId)
        return ResponseEntity.ok(response)
    }

    @PostMapping("/{waveId}/assignments")
    fun assignShipmentToWave(@Valid @RequestBody shipmentAssignmentRequest : ShipmentAssignmentRequest,
                             @PathVariable waveId: UUID
    ) : ResponseEntity<ShipmentAssignmentResponse> {
        val assignShipment = dispatchService.assignShipment(waveId, shipmentAssignmentRequest)
        return ResponseEntity.status(HttpStatus.CREATED).body(assignShipment)
    }
}