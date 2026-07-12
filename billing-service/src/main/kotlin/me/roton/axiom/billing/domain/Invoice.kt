package me.roton.axiom.billing.domain

import jakarta.persistence.*
import org.hibernate.annotations.CreationTimestamp
import org.hibernate.annotations.UpdateTimestamp
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "invoices")
class Invoice(
    @Id
    @GeneratedValue
    val id: UUID? = null,

    @Column(name = "subscription_id", nullable = false)
    val subscriptionId: UUID,

    @Column(name = "subscriber_id", nullable = false)
    val subscriberId: UUID,

    @Column(name = "total_amount_cents", nullable = false)
    var totalAmountCents: Long,

    @Column(name = "currency", nullable = false)
    val currency: String,

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    var status: InvoiceStatus = InvoiceStatus.PENDING,

    @OneToMany(mappedBy = "invoice", cascade = [CascadeType.ALL], orphanRemoval = true)
    val lineItems: MutableList<LineItem> = mutableListOf()
) {
    init {
        me.roton.axiom.common.money.Money(totalAmountCents, currency)
    }

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    var createdAt: Instant? = null

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant? = null
}