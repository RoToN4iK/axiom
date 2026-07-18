package me.roton.axiom.gateway.utils

import me.roton.axiom.common.money.Money
import me.roton.axiom.contracts.common.money
import me.roton.axiom.contracts.subscription.SubscriptionResponse
import me.roton.axiom.contracts.subscription.UpgradeSubscriptionResponse
import me.roton.axiom.gateway.domain.subscription.SubscriptionResponse as SubscriptionResponseDomain
import java.time.Instant
import java.util.UUID

fun Money.toProto(): me.roton.axiom.contracts.common.Money = money {
    amountCents = this@toProto.amountCents
    currency = this@toProto.currency
}

fun SubscriptionResponse.toDomain(): SubscriptionResponseDomain {
    return SubscriptionResponseDomain(
        subscriptionId = UUID.fromString(this.subscriptionId),
        subscriberId = UUID.fromString(this.subscriberId),
        planId = UUID.fromString(this.planId),
        status = this.status.toString(),
        currentPeriodEnd = Instant.parse(this.currentPeriodEnd)
    )
}

fun UpgradeSubscriptionResponse.toDomain(): SubscriptionResponseDomain {
    return SubscriptionResponseDomain(
        subscriptionId = UUID.fromString(this.subscriptionId),
        subscriberId = UUID.fromString(this.subscriberId),
        planId = UUID.fromString(this.planId),
        status = this.status.toString(),
        currentPeriodEnd = Instant.parse(this.currentPeriodEnd)
    )
}