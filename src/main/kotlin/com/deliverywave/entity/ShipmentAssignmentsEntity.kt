package com.deliverywave.entity

import jakarta.persistence.*
import java.time.Instant
import java.util.*

@Entity
@Table(name = "shipment_assignment")
class ShipmentAssignmentsEntity (
    
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    var id : UUID? = null, 
    
    @Column(nullable = false)
    var assignedAt : Instant = Instant.EPOCH,
    
    @OneToMany(mappedBy = "shipmentAssignment", cascade = [CascadeType.ALL], orphanRemoval = true)
    var shipments : MutableList<ShipmentEntity> = mutableListOf(),
    
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "dispatch_id", nullable = false)
    var dispatchWave : DispatchEntity? = null,
) {
    fun addShipment(shipment: ShipmentEntity) {
        shipments.add(shipment)
        shipment.shipmentAssignment = this
    }

    fun removeShipment(shipment: ShipmentEntity) {
        shipments.remove(shipment)
        shipment.shipmentAssignment = null
    }
}
