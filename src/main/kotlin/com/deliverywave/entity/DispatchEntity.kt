package com.deliverywave.entity

import jakarta.persistence.*
import java.time.Instant
import java.util.*

@Entity
@Table(name = "dispatch")
class DispatchEntity (

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    var id : UUID? = null,

    @Column(nullable = false) 
    var warehouseId: String = "",

    @Column(nullable = false)
    var dispatchWindowStart : Instant = Instant.EPOCH,

    @Column(nullable = false)
    var dispatchWindowEnd : Instant = Instant.EPOCH,

    @Column(nullable = false)
    var maxCrate: Int = 0,
    
    @OneToMany(mappedBy = "dispatchWave", cascade = [CascadeType.ALL], orphanRemoval = true)
    var shipmentAssignments : MutableList<ShipmentAssignmentsEntity> = mutableListOf()
){
    fun addAssignment(shipmentAssignment : ShipmentAssignmentsEntity) {
        shipmentAssignments.add(shipmentAssignment)
        shipmentAssignment.dispatchWave = this
    }

    fun removeAssignment(shipmentAssignment: ShipmentAssignmentsEntity) {
        shipmentAssignments.remove(shipmentAssignment)
        shipmentAssignment.dispatchWave = null
    }
}
