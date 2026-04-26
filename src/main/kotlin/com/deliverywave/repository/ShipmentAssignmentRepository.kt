package com.deliverywave.repository

import com.deliverywave.entity.ShipmentAssignmentsEntity
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.util.*

@Repository
interface ShipmentAssignmentRepository : JpaRepository<ShipmentAssignmentsEntity, UUID> {
}