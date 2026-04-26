package com.deliverywave.exception

import java.time.Instant

data class ErrorMessage(val errorMessage: String, val time : Instant)
