package com.deliverywave.repository

import com.deliverywave.entity.ShipmentEntity
import jakarta.persistence.LockModeType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository
import java.util.*

@Repository
interface ShipmentRepository : JpaRepository<ShipmentEntity, UUID> {
    override fun findAllById(ids: Iterable<UUID>): List<ShipmentEntity>

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM ShipmentEntity s WHERE s.id IN :ids ORDER BY s.id")
    fun findAllByIdForUpdate(ids: List<UUID>): List<ShipmentEntity>
}
