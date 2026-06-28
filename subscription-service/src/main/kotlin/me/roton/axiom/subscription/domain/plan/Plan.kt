package me.roton.axiom.subscription.domain.plan

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.Id
import jakarta.persistence.Table
import me.roton.axiom.common.money.Money
import me.roton.axiom.contracts.plan.PlanResponse
import me.roton.axiom.contracts.plan.planResponse
import org.hibernate.annotations.CreationTimestamp
import org.hibernate.annotations.UpdateTimestamp
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "plans")
class Plan(
    @Id
    @GeneratedValue
    val id: UUID? = null,

    @Column(name = "name", nullable = false)
    val name: String,

    @Column(name = "price_amount_cents", nullable = false)
    val priceAmountCents: Long,

    @Column(name = "price_currency", nullable = false)
    val priceCurrency: String,

    @Enumerated(EnumType.STRING)
    @Column(name = "billing_cycle", nullable = false)
    val billingCycle: BillingCycle = BillingCycle.MONTHLY,

    @Column(name = "active")
    var active: Boolean = true
) {
    init {
        Money(priceAmountCents, priceCurrency)
    }

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    var createdAt: Instant? = null

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant? = null
}

fun Plan.price(): Money = Money(priceAmountCents, priceCurrency)

fun Plan.toResponse(): PlanResponse {
    return planResponse {
        planId = id.toString()
        name = this@toResponse.name
        price = this@toResponse.price().toProto()
        billingCycle = this@toResponse.billingCycle.toProto()
    }
}