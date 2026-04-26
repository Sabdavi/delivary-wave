package com.deliverywave.extension

import com.deliverywave.entity.DispatchEntity
import com.deliverywave.model.DispatchWaveRequest
import com.deliverywave.model.DispatchWaveResponse

fun DispatchWaveRequest.toEntity() = DispatchEntity(
    warehouseId = warehouseId,
    dispatchWindowStart = dispatchWindowStart,
    dispatchWindowEnd = dispatchWindowEnd,
    maxCrate = maxCrate
)

fun DispatchEntity.toResponse() = DispatchWaveResponse(
    id = id.toString(),
    warehouseId = warehouseId,
    dispatchWindowStart = dispatchWindowStart,
    dispatchWindowEnd = dispatchWindowEnd,
    maxCrate = maxCrate
)