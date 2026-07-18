package me.roton.axiom.gateway.controller

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import me.roton.axiom.contracts.auth.AuthServiceGrpcKt
import me.roton.axiom.contracts.auth.createApiKeyRequest
import me.roton.axiom.contracts.auth.revokeApiKeyRequest
import me.roton.axiom.gateway.annotation.IdempotencyKeyDoc
import me.roton.axiom.gateway.domain.auth.CreateApiKeyRequest
import me.roton.axiom.gateway.domain.auth.CreateApiKeyResponse
import me.roton.axiom.gateway.domain.auth.RevokeApiKeyResponse
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RestController
import java.util.*

@RestController
@Tag(name = "Auth", description = "API key management for companies integrating Axiom")
class AuthController(
    private val authServiceStub: AuthServiceGrpcKt.AuthServiceCoroutineStub
) {

    @PostMapping("/api/auth/keys")
    @Operation(
        summary = "Create a new API key",
        description = "Generates a new API key for a company. The raw key is returned exactly once — " +
                "store it immediately, it cannot be retrieved again."
    )
    suspend fun createApiKey(
        @IdempotencyKeyDoc @RequestHeader("Idempotency-Key") idempotencyKey: String,
        @Valid @RequestBody request: CreateApiKeyRequest
    ): CreateApiKeyResponse {
        val grpcResponse = authServiceStub.createApiKey(
            createApiKeyRequest { companyName = request.companyName }
        )

        return CreateApiKeyResponse(
            apiKeyId = UUID.fromString(grpcResponse.apiKeyId),
            companyName = grpcResponse.companyName,
            rawKey = grpcResponse.rawKey
        )
    }

    @PostMapping("/api/auth/keys/{id}/revoke")
    @Operation(
        summary = "Revoke an API key",
        description = "Immediately invalidates an API key. Cannot be undone — a new key must be created to replace it."
    )
    suspend fun revokeApiKey(
        @IdempotencyKeyDoc @RequestHeader("Idempotency-Key") idempotencyKey: String,
        @PathVariable id: String
    ): RevokeApiKeyResponse {
        val grpcResponse = authServiceStub.revokeApiKey(
            revokeApiKeyRequest { apiKeyId = id }
        )

        return RevokeApiKeyResponse(revoked = grpcResponse.revoked)
    }
}