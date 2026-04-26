package com.deliverywave.model

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Positive
import java.time.Instant

data class DispatchWaveRequest(
    @field: NotBlank (message = "warehouseId should not be blank")
    val warehouseId: String,
    @field: NotNull(message = "dispatchWindowStart should not be blank")
    val dispatchWindowStart : Instant,
    @field: NotNull(message = "dispatchWindowEnd should not be blank")
    val dispatchWindowEnd : Instant,
    @field: Positive(message = "maxCrate should be positive")
    val maxCrate: Int )
