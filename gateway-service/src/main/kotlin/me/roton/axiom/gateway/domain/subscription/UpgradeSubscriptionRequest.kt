package me.roton.axiom.gateway.domain.subscription

import jakarta.validation.constraints.NotBlank

data class UpgradeSubscriptionRequest(
    @field:NotBlank
    val newPlanId: String
)