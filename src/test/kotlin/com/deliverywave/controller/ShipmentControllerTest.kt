package com.deliverywave.controller

import com.deliverywave.model.ShipmentResponse
import com.deliverywave.model.Status
import com.deliverywave.service.ShipmentService
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.post
import java.time.Instant
import java.util.*


@WebMvcTest(controllers = [ShipmentController::class])
class ShipmentControllerTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @MockBean
    private lateinit var shipmentService: ShipmentService

    @Test
    fun shipment_with_correctInput_shouldReturnValidShipment() {
        val shipmentResponse = ShipmentResponse(
            UUID.randomUUID().toString(),
            "warehouse-001",
            "store-001",
            Instant.now(),
            20,
            Status.IN_PROGRESS
        )
        whenever(shipmentService.createShipment(any())).thenReturn(shipmentResponse)

        mockMvc.post("/shipments") {
            contentType = MediaType.APPLICATION_JSON
            content = """
                {
                   "warehouseId": "WH-AMS-1",
                   "storeId": "STORE-101",
                   "plannedDepartureAt": "2026-04-22T14:30:00Z",
                   "crateCount": 20,
                   "status": "IN_PROGRESS"
                }
            """.trimIndent()
        }.andExpect {
            status{isCreated()}
            content {contentTypeCompatibleWith(MediaType.APPLICATION_JSON)}
            jsonPath("$.id"){exists() }
            jsonPath("$.warehouseId"){equals("WH-AMS-1")}
            jsonPath("$.storeId"){equals("STORE-101")}
            jsonPath("$.plannedDepartureAt"){equals("2026-04-22T14:30:00")}
            jsonPath("$.crateCount"){equals(20)}
            jsonPath("$.status"){equals("IN_PROGRESS")}
        }
    }

    @Test
    fun withBadInput_shouldReturnValidationError() {
        mockMvc.post("/shipments") {
            contentType = MediaType.APPLICATION_JSON
            content = """
                {
                   "warehouseId": "",
                   "storeId": "STORE-101",
                   "plannedDepartureAt": "2026-04-22T14:30:00Z",
                   "crateCount": 20,
                   "status": "IN_PROGRESS"
                }
            """.trimIndent()
        }.andExpect {
            status{isBadRequest() }
            content {contentTypeCompatibleWith(MediaType.APPLICATION_JSON)}
            jsonPath("$.errorMessage"){isNotEmpty()}
            jsonPath("$.time"){isNotEmpty()}
        }.andDo {print()}
    }

    @Test
    fun withBadInput_withNoValues_shouldReturnValidationError() {
        mockMvc.post("/shipments") {
            contentType = MediaType.APPLICATION_JSON
            content = """
                {
                   "storeId": "STORE-101",
                   "plannedDepartureAt": "2026-04-22T14:30:00Z",
                   "crateCount": 20,
                   "status": "IN_PROGRESS"
                }
            """.trimIndent()
        }.andExpect {
            status{isBadRequest() }
            content {contentTypeCompatibleWith(MediaType.APPLICATION_JSON)}
            jsonPath("$.errorMessage"){isNotEmpty()}
            jsonPath("$.time"){isNotEmpty()}
        }.andDo {print()}
    }
}
