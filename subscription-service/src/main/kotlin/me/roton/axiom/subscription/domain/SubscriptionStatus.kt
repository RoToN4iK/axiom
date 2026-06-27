package me.roton.axiom.subscription.domain

enum class SubscriptionStatus {
    TRIALING,
    ACTIVE,
    PAST_DUE,
    CANCELLED,
    SUPERSEDED
}

fun SubscriptionStatus.toProto(): me.roton.axiom.contracts.common.SubscriptionStatus {
    return when (this) {
        SubscriptionStatus.TRIALING -> me.roton.axiom.contracts.common.SubscriptionStatus.TRIALING
        SubscriptionStatus.ACTIVE -> me.roton.axiom.contracts.common.SubscriptionStatus.ACTIVE
        SubscriptionStatus.PAST_DUE -> me.roton.axiom.contracts.common.SubscriptionStatus.PAST_DUE
        SubscriptionStatus.CANCELLED -> me.roton.axiom.contracts.common.SubscriptionStatus.CANCELLED
        SubscriptionStatus.SUPERSEDED -> me.roton.axiom.contracts.common.SubscriptionStatus.SUPERSEDED
    }
}