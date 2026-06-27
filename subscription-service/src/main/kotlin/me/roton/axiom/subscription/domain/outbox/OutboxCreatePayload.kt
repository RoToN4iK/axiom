package me.roton.axiom.subscription.domain.outbox

import java.time.Instant
import java.util.UUID

data class OutboxCreatePayload(
    val subscriptionId: UUID,
    val subscriberId: UUID,
    val planId: UUID,
    val currentPeriodEnd: Instant
)