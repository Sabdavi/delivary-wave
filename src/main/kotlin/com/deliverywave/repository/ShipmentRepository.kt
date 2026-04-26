package com.deliverywave.repository

import com.deliverywave.entity.ShipmentEntity
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.util.*

@Repository
interface ShipmentRepository : JpaRepository<ShipmentEntity, UUID> {
    override fun findAllById(ids: Iterable<UUID>): List<ShipmentEntity>
}
