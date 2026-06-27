package me.roton.axiom.common.bootstrap

import java.util.TimeZone

object AxiomBootstrap {
    fun forceUtc() {
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
    }
}