package me.roton.axiom.gateway.domain.auth

import jakarta.validation.constraints.NotBlank

data class CreateApiKeyRequest(
    @field:NotBlank
    val companyName: String
)