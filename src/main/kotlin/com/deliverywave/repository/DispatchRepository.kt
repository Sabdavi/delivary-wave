package com.deliverywave.repository

import com.deliverywave.entity.DispatchEntity
import jakarta.persistence.LockModeType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository
import java.util.*

@Repository
interface DispatchRepository : JpaRepository<DispatchEntity, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT d FROM DispatchEntity d WHERE d.id = :id")
    fun findByIdForUpdate(id: UUID): DispatchEntity?
}