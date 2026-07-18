package me.roton.axiom.gateway.domain.plan

import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Pattern

data class CreatePlanRequest(
    @field:NotBlank
    val name: String,

    @field:Min(1)
    val priceAmountCents: Long,

    @field:Pattern(regexp = "^[A-Z]{3}$", message = "currency must be a 3-letter uppercase ISO 4217 code")
    val currency: String,

    // matches the proto BillingCycle enum's real values as plain strings at the
    // REST boundary — validated/converted inside the controller, since a raw
    // string here can't be exhaustiveness-checked the way a real enum can
    @field:Pattern(regexp = "^(MONTHLY|YEARLY)$", message = "billingCycle must be MONTHLY or YEARLY")
    val billingCycle: String
)