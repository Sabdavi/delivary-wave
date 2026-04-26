package com.deliverywave.service

import com.deliverywave.entity.ShipmentEntity
import com.deliverywave.model.ShipmentRequest
import com.deliverywave.model.Status
import com.deliverywave.repository.ShipmentRepository
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.*
import java.time.Instant
import java.util.*

@ExtendWith(MockitoExtension::class)
class ShipmentServiceTest {

    @Mock
    lateinit var shipmentRepository: ShipmentRepository

    lateinit var shipmentService: ShipmentService

    @BeforeEach
    fun setUp() {
        shipmentService = ShipmentService(shipmentRepository)
    }

    @Test
    fun withCorrectInput_shouldCallRepositoryAndReturnCorrectResponse() {
        val shipmentRequest = ShipmentRequest(
            "warehouse-01",
            "store-01",
            Instant.now(),
            20,
            Status.IN_PROGRESS)

        val shipmentEntity = ShipmentEntity(
            UUID.randomUUID(),
            "warehouse-01",
            "store-01",
            Instant.now(),
            20,
            Status.IN_PROGRESS)

        val entityCaptor = argumentCaptor<ShipmentEntity>()
        whenever(shipmentRepository.save(any())).thenReturn(shipmentEntity)
        shipmentService.createShipment(shipmentRequest)


        verify(shipmentRepository, times(1)).save(entityCaptor.capture())

        val entity = entityCaptor.firstValue
        assertThat(entity.warehouseId).isEqualTo(shipmentRequest.warehouseId)
        assertThat(entity.storeId).isEqualTo(shipmentRequest.storeId)
        assertThat(entity.status).isEqualTo(shipmentRequest.status)
        assertThat(entity.plannedDepartureAt).isEqualTo(shipmentRequest.plannedDepartureAt)
        assertThat(entity.id).isNull()
    }
}