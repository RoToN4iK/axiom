package me.roton.axiom.subscription.domain.outbox

import me.roton.axiom.subscription.domain.SubscriptionStatus
import java.util.UUID
import java.time.Instant

data class OutboxUpgradePayload(
    val subscriptionId: UUID,
    val subscriberId: UUID,
    val oldPlanId: UUID,
    val newPlanId: UUID,
    val status: SubscriptionStatus,
    val oldPeriodEnd: Instant
)