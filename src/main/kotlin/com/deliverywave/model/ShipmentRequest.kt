package com.deliverywave.model

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Positive
import java.time.Instant

data class ShipmentRequest(
    @field: NotBlank(message = "warehouseId is required")
    val warehouseId: String,
    @field: NotBlank(message = "storeId is required")
    val storeId: String,
    @field: NotNull(message = "plannedDepartureAt is required")
    val plannedDepartureAt: Instant,
    @field: Positive(message = "crateCount is required")
    val crateCount: Int,
    @field: NotNull(message = "status is required")
    val shipmentStatus: ShipmentStatus,
    )
