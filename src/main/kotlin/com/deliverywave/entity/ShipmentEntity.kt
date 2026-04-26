package com.deliverywave.entity

import com.deliverywave.model.Status
import jakarta.persistence.*
import java.time.Instant
import java.util.*

@Entity
@Table(name = "shipment")
class ShipmentEntity (

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    var id: UUID? = null,

    @Column(nullable = false)
    var warehouseId: String = "",

    @Column(nullable = false)
    var storeId: String = "",

    @Column(nullable = false)
    var plannedDepartureAt: Instant = Instant.EPOCH,

    @Column(nullable = false)
    var crate: Int = 0,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var status: Status = Status.IN_PROGRESS,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "shipment_assignment_id")
    var shipmentAssignment: ShipmentAssignmentsEntity? = null,

    @OneToMany(mappedBy = "shipmentEntity", cascade = [CascadeType.ALL], orphanRemoval = true)
    var shipmentItems : MutableList<ShipmentItemEntity> = mutableListOf()
) {
    fun addShipmentItem(shipmentItem : ShipmentItemEntity) {
        shipmentItems.add(shipmentItem)
        shipmentItem.shipmentEntity = this
    }

    fun removeShipmentItem(shipmentItem : ShipmentItemEntity) {
        shipmentItems.remove(shipmentItem)
        shipmentItem.shipmentEntity = null
    }
}