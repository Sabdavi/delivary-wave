package com.deliverywave.entity

import jakarta.persistence.*
import java.util.*

@Entity
@Table(name = "shipment_item")
class ShipmentItemEntity (

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    var id : UUID? =  null,

    @Column(nullable = false)
    var name : String? = "",

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "shipment_id")
    var shipmentEntity : ShipmentEntity? = null
)
