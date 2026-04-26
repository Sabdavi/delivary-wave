package com.deliverywave.model

import java.time.Instant

data class DispatchWaveResponse (
    val id : String,
    val warehouseId: String,
    val dispatchWindowStart: Instant,
    val dispatchWindowEnd: Instant,
    val maxCrate: Int
)
