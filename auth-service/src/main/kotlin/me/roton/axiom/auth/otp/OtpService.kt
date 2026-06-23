package me.roton.axiom.auth.otp

import org.springframework.beans.factory.annotation.Value
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.stereotype.Service
import java.time.Duration
import kotlin.random.Random

@Service
class OtpService(
    private val redisTemplate: StringRedisTemplate,
    @Value($$"${axiom.otp.ttl-minutes}") private val ttlMinutes: Long,
    @Value($$"${axiom.otp.length}") private val length: Int
) {
    fun generateAndStore(email: String): String {
        val code = (1..length)
            .map { Random.nextInt(0, 10) }
            .joinToString("")

        redisTemplate.opsForValue().set(
            redisKey(email),
            code,
            Duration.ofMinutes(ttlMinutes)
        )

        return code
    }

    fun verify(email: String, submittedCode: String): Boolean {
        val key = redisKey(email)
        val storedCode = redisTemplate.opsForValue().get(key) ?: return false

        val matches = storedCode == submittedCode
        if (matches) {
            redisTemplate.delete(key) // one-time use — consume it on success
        }
        return matches
    }

    private fun redisKey(email: String): String = "auth:otp:$email"
}