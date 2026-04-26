package com.deliverywave.model

data class DispatchWaveSummaryResponse(
    val waveId: String,
    val warehouseId: String,
    val totalAssignments: Int,
    val totalAssignedShipments: Int,
    val totalAssignedCrates: Int,
    val maxCrate: Int,
    val remainingCapacity: Int,
)

