package me.roton.axiom.subscription

import me.roton.axiom.common.bootstrap.AxiomBootstrap
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class SubscriptionServiceApplication {
    companion object {
        init {
            AxiomBootstrap.forceUtc()
        }
    }
}

fun main(args: Array<String>) {
    runApplication<SubscriptionServiceApplication>(*args)
}
