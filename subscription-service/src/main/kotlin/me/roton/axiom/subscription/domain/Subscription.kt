package me.roton.axiom.subscription.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.Id
import jakarta.persistence.Table
import me.roton.axiom.contracts.subscription.SubscriptionResponse
import me.roton.axiom.contracts.subscription.subscriptionResponse
import org.hibernate.annotations.CreationTimestamp
import org.hibernate.annotations.UpdateTimestamp
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "subscriptions")
class Subscription(
    @Id
    @GeneratedValue
    val id: UUID? = null,

    @Column(name = "subscriber_id", nullable = false)
    val subscriberId: UUID,

    @Column(name = "plan_id", nullable = false)
    val planId: UUID,

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    var status: SubscriptionStatus = SubscriptionStatus.TRIALING,

    @Column(name = "current_period_end", nullable = false)
    var currentPeriodEnd: Instant,
) {
    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    var createdAt: Instant? = null

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant? = null
}

fun Subscription.toResponse(): SubscriptionResponse {
    return subscriptionResponse {
        subscriptionId = id.toString()
        subscriberId = this@toResponse.subscriberId.toString()
        planId = this@toResponse.planId.toString()
        status = this@toResponse.status.toProto()
        currentPeriodEnd = this@toResponse.currentPeriodEnd.toString()
    }
}