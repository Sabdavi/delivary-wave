package com.deliverywave.model

data class ShipmentAssignmentResponse(val id : String,
                                      val assignedShipmentIds : List<String>,
                                      val totalAssignmentShipments : Int,
                                      val totalAssignedCrates : Int ,
                                      val remainingCapacity: Int)
