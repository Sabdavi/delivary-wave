package com.deliverywave.model

import jakarta.validation.constraints.NotNull
import java.util.*

data class ShipmentAssignmentRequest(
    @field: NotNull(message = "shipments can't be null")
    val shipments : List<UUID>)
