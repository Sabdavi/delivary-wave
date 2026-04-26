package com.deliverywave.extension

import com.deliverywave.entity.ShipmentEntity
import com.deliverywave.model.ShipmentRequest
import com.deliverywave.model.ShipmentResponse

fun ShipmentRequest.toEntity() = ShipmentEntity(
    warehouseId = warehouseId,
    storeId = storeId,
    plannedDepartureAt = plannedDepartureAt,
    crate = crateCount,
    shipmentStatus = shipmentStatus,
)

fun ShipmentEntity.toResponse() = ShipmentResponse(
    id = id.toString(),
    warehouseId = warehouseId,
    storeId = storeId,
    plannedDepartureAt = plannedDepartureAt,
    crate = crate,
    shipmentStatus = shipmentStatus,
)