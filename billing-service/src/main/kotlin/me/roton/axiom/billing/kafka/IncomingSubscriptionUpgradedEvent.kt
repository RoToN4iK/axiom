package me.roton.axiom.billing.kafka

import java.time.Instant
import java.util.UUID

// Local, deliberately duplicated view of subscription-service's OutboxUpgradePayload.
data class IncomingSubscriptionUpgradedEvent(
    val subscriptionId: UUID,
    val subscriberId: UUID,
    val oldPlanId: UUID,
    val newPlanId: UUID,
    val status: String,
    val oldPeriodEnd: Instant
)