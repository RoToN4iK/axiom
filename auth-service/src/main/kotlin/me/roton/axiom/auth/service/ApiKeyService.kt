package me.roton.axiom.auth.service

import me.roton.axiom.auth.domain.ApiKey
import me.roton.axiom.auth.repository.ApiKeyRepository
import org.springframework.stereotype.Service
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import java.util.UUID

data class GeneratedApiKey(val apiKeyId: UUID, val companyName: String, val rawKey: String)

@Service
class ApiKeyService(
    private val apiKeyRepository: ApiKeyRepository
) {
    private val secureRandom = SecureRandom()

    fun createKey(companyName: String): GeneratedApiKey {
        val rawKey = generateRawKey()
        val hash = hash(rawKey)

        val saved = apiKeyRepository.save(
            ApiKey(
                companyName = companyName,
                keyHash = hash
            )
        )

        return GeneratedApiKey(
            apiKeyId = saved.id!!,
            companyName = saved.companyName,
            rawKey = rawKey
        )
    }

    fun validate(rawKey: String): ApiKey? {
        val hash = hash(rawKey)
        val found = apiKeyRepository.findByKeyHash(hash) ?: return null
        return if (found.active) found else null
    }

    fun revoke(apiKeyId: UUID): Boolean {
        val key = apiKeyRepository.findById(apiKeyId).orElse(null) ?: return false
        key.active = false
        apiKeyRepository.save(key)
        return true
    }

    // 32 random bytes, base64url-encoded — comfortably high entropy, URL-safe
    // (no padding characters that need escaping in headers/URLs).
    private fun generateRawKey(): String {
        val bytes = ByteArray(32)
        secureRandom.nextBytes(bytes)
        return "axk_" + Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }

    private fun hash(rawKey: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val bytes = digest.digest(rawKey.toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }
}