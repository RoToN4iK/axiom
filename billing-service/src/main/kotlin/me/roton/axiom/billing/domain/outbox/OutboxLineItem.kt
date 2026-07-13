package me.roton.axiom.billing.domain.outbox

data class OutboxLineItem(
    val description: String,
    val amountCents: Long
)
