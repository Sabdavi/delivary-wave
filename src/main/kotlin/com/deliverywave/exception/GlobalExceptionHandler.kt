package com.deliverywave.exception

import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import java.time.Instant

@RestControllerAdvice
class GlobalExceptionHandler {

    @ExceptionHandler
    fun handleValidationExceptions(ex: MethodArgumentNotValidException): ResponseEntity<ErrorMessage> {
        val errorMessage = ex.bindingResult.fieldErrors.associate { it.field to it.defaultMessage }.entries.joinToString { "${it.key}: ${it.value}" }
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ErrorMessage(errorMessage, Instant.now()))
    }
    
    @ExceptionHandler
    fun handleParserExceptions(ex: HttpMessageNotReadableException): ResponseEntity<ErrorMessage> {
        val errorMessage = ex.message ?: "NO MESSAGE"
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ErrorMessage(errorMessage, Instant.now()))

    }

    @ExceptionHandler
    fun handleNotFound(ex: NoSuchElementException): ResponseEntity<ErrorMessage> {
        val errorMessage = ex.message ?: "Resource not found"
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ErrorMessage(errorMessage, Instant.now()))
    }

    @ExceptionHandler
    fun handleCapacityException(ex: NotEnoughCapacityException): ResponseEntity<ErrorMessage> {
        val errorMessage = ex.message ?: "Not enough capacity"
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ErrorMessage(errorMessage, Instant.now()))
    }

    @ExceptionHandler
    fun handleNoDispatchWaveFindException(ex: NotDispatchWaveFoundException) : ResponseEntity<ErrorMessage> {
        val errorMessage = ex.message ?: "Dispatch wave not found"
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ErrorMessage(errorMessage, Instant.now()))
    }

    @ExceptionHandler
    fun handleDuplicateShipmentsException(ex: DuplicateShipmentFoundException) : ResponseEntity<ErrorMessage> {
        val errorMessage = ex.message ?: "Duplicate ids found"
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ErrorMessage(errorMessage, Instant.now()))
    }

    @ExceptionHandler
    fun handleDateValidationException(ex: DatesAreNotValidException) : ResponseEntity<ErrorMessage> {
        val errorMessage = ex.message ?: "dispatchWindowEnd should be after dispatchWindowStart"
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ErrorMessage(errorMessage, Instant.now()))
    }

    @ExceptionHandler
    fun handleShipmentNotFoundException(ex: ShipmentNotFoundException) : ResponseEntity<ErrorMessage> {
        val errorMessage = ex.message ?: "Shipment Not found"
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ErrorMessage(errorMessage, Instant.now()))
    }
}