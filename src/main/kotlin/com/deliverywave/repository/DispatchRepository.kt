package com.deliverywave.repository

import com.deliverywave.entity.DispatchEntity
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.util.*

@Repository
interface DispatchRepository : JpaRepository<DispatchEntity, UUID> {
}