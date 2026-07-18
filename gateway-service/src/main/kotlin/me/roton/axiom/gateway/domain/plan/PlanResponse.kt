package me.roton.axiom.gateway.domain.plan

import java.util.UUID

data class PlanResponse(
    val planId: UUID,
    val name: String,
    val priceAmountCents: Long,
    val currency: String,
    val billingCycle: String
)

data class ListPlansResponse(
    val plans: List<PlanResponse>
)