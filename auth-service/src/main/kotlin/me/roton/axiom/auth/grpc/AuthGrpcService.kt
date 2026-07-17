package me.roton.axiom.auth.grpc

import io.grpc.Status
import io.grpc.StatusRuntimeException
import me.roton.axiom.auth.service.ApiKeyService
import me.roton.axiom.contracts.auth.*
import org.springframework.stereotype.Service
import java.util.UUID

@Service
class AuthGrpcService(
    private val apiKeyService: ApiKeyService
) : AuthServiceGrpcKt.AuthServiceCoroutineImplBase() {

    override suspend fun createApiKey(request: CreateApiKeyRequest): CreateApiKeyResponse {
        if (request.companyName.isBlank()) {
            throw StatusRuntimeException(
                Status.INVALID_ARGUMENT.withDescription("company_name is required")
            )
        }

        // currently this has no auth and used by ApiKeyBootstrapRunner
        val generated = apiKeyService.createKey(request.companyName)

        return createApiKeyResponse {
            apiKeyId = generated.apiKeyId.toString()
            companyName = generated.companyName
            rawKey = generated.rawKey
        }
    }

    override suspend fun validateApiKey(request: ValidateApiKeyRequest): ValidateApiKeyResponse {
        val found = apiKeyService.validate(request.rawKey)

        return if (found != null) {
            validateApiKeyResponse {
                valid = true
                companyName = found.companyName
            }
        } else {
            validateApiKeyResponse {
                valid = false
            }
        }
    }

    override suspend fun revokeApiKey(request: RevokeApiKeyRequest): RevokeApiKeyResponse {
        val id = try {
            UUID.fromString(request.apiKeyId)
        } catch (_: IllegalArgumentException) {
            throw StatusRuntimeException(Status.INVALID_ARGUMENT.withDescription("Invalid api_key_id"))
        }

        val revoked = apiKeyService.revoke(id)
        if (!revoked) {
            throw StatusRuntimeException(
                Status.NOT_FOUND.withDescription("No API key with id ${request.apiKeyId}")
            )
        }

        return revokeApiKeyResponse { this.revoked = true }
    }
}