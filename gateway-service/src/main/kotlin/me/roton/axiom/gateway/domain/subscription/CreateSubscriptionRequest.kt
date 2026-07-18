package me.roton.axiom.gateway.domain.subscription

import jakarta.validation.constraints.NotBlank

data class CreateSubscriptionRequest(
    @field:NotBlank
    val subscriberId: String,

    @field:NotBlank
    val planId: String
)