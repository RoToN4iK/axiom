package me.roton.axiom.subscription.domain.plan

import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset

enum class BillingCycle {
    MONTHLY,
    YEARLY
}

fun BillingCycle.toProto(): me.roton.axiom.contracts.common.BillingCycle {
    return when (this) {
        BillingCycle.MONTHLY -> me.roton.axiom.contracts.common.BillingCycle.MONTHLY
        BillingCycle.YEARLY -> me.roton.axiom.contracts.common.BillingCycle.YEARLY
    }
}

fun BillingCycle.nextPeriodEnd(from: Instant): Instant {
    val dateTime = LocalDateTime.ofInstant(from, ZoneId.of("UTC"))

    return when(this) {
        BillingCycle.MONTHLY -> dateTime.plusMonths(1).toInstant(ZoneOffset.UTC)
        BillingCycle.YEARLY -> dateTime.plusYears(1).toInstant(ZoneOffset.UTC)
    }
}