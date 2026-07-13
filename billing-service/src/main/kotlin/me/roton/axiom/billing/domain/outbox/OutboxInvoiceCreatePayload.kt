package me.roton.axiom.billing.domain.outbox

import java.util.UUID

data class OutboxInvoiceCreatedPayload(
    val invoiceId: UUID,
    val subscriptionId: UUID,
    val subscriberId: UUID,
    val totalAmountCents: Long,
    val currency: String,
    val lineItems: List<OutboxLineItem>
)
