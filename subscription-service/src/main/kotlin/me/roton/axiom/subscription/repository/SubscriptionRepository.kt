package me.roton.axiom.subscription.repository

import me.roton.axiom.subscription.domain.Subscription
import me.roton.axiom.subscription.domain.SubscriptionStatus
import org.springframework.data.jpa.repository.JpaRepository
import java.util.*

interface SubscriptionRepository: JpaRepository<Subscription, UUID> {
    fun findBySubscriberIdAndStatusIn(
        subscriberId: UUID,
        statuses: List<SubscriptionStatus> = listOf(
            SubscriptionStatus.TRIALING,
            SubscriptionStatus.ACTIVE,
            SubscriptionStatus.PAST_DUE
        )
    ): List<Subscription>
}