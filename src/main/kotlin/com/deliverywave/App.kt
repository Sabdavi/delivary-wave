package com.deliverywave

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
 class ShipmentApp

fun main(args : Array<String>) {
    runApplication<ShipmentApp>(*args)
}

