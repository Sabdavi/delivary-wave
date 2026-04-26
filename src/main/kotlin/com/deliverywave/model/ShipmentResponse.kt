package com.deliverywave.model

import java.time.Instant

data class ShipmentResponse(
    val id: String,
    val warehouseId: String,
    val storeId: String,
    val plannedDepartureAt: Instant,
    val crate: Int,
    val shipmentStatus: ShipmentStatus,
)
