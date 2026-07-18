package me.roton.axiom.gateway.domain.auth

import java.util.UUID

data class CreateApiKeyResponse(
    val apiKeyId: UUID,
    val companyName: String,
    val rawKey: String
)