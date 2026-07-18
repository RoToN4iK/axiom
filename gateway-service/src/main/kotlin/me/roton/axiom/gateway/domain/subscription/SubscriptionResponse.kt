package me.roton.axiom.gateway.domain.subscription

import java.time.Instant
import java.util.UUID

data class SubscriptionResponse(
    val subscriptionId: UUID,
    val subscriberId: UUID,
    val planId: UUID,
    val status: String,
    val currentPeriodEnd: Instant
)