package me.roton.axiom.billing.domain

import jakarta.persistence.*
import org.hibernate.annotations.CreationTimestamp
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "line_items")
class LineItem(
    @Id
    @GeneratedValue
    val id: UUID? = null,

    @ManyToOne
    @JoinColumn(name = "invoice_id", nullable = false)
    val invoice: Invoice,

    @Column(name = "description", nullable = false)
    val description: String,

    @Column(name = "amount_cents", nullable = false)
    val amountCents: Long
) {
    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    var createdAt: Instant? = null
}