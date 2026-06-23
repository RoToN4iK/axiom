package me.roton.axiom.auth.jwt

import io.jsonwebtoken.Jwts
import io.jsonwebtoken.security.Keys
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.time.Duration
import java.util.Date
import java.util.UUID
import javax.crypto.SecretKey

data class TokenPair(val accessToken: String, val refreshToken: String)

@Component
class JwtService(
    @Value($$"${axiom.jwt.secret}") secret: String,
    @Value($$"${axiom.jwt.access-token-ttl-minutes}") private val accessTtlMinutes: Long,
    @Value($$"${axiom.jwt.refresh-token-ttl-days}") private val refreshTtlDays: Long
) {
    private val key: SecretKey = Keys.hmacShaKeyFor(secret.toByteArray())

    fun generateTokenPair(accountId: UUID, role: String): TokenPair {
        val access = buildToken(accountId, role, Duration.ofMinutes(accessTtlMinutes), tokenType = "access")
        val refresh = buildToken(accountId, role, Duration.ofDays(refreshTtlDays), tokenType = "refresh")
        return TokenPair(access, refresh)
    }

    fun validateAndExtractAccountId(token: String, expectedType: String): UUID? {
        return try {
            val claims = Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .payload

            if (claims["type"] != expectedType) return null

            UUID.fromString(claims.subject)
        } catch (e: Exception) {
            // expired, malformed, bad signature — all collapse to "invalid token"
            null
        }
    }

    private fun buildToken(accountId: UUID, role: String, ttl: Duration, tokenType: String): String {
        val now = Date()
        val expiry = Date(now.time + ttl.toMillis())

        return Jwts.builder()
            .subject(accountId.toString())
            .claim("role", role)
            .claim("type", tokenType)
            .issuedAt(now)
            .expiration(expiry)
            .signWith(key)
            .compact()
    }
}