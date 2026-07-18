package me.roton.axiom.gateway.filter

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import me.roton.axiom.common.idempotency.IdempotencyKeys
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter

@Component
class IdempotencyFilter(
    private val redisTemplate: StringRedisTemplate
) : OncePerRequestFilter() {

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain
    ) {
        if (request.method !in listOf("POST", "PUT", "PATCH")) {
            filterChain.doFilter(request, response)
            return
        }

        val idempotencyHeader = request.getHeader("Idempotency-Key")
        if (idempotencyHeader == null) {
            filterChain.doFilter(request, response)
            return
        }

        val idempotencyKey = try {
            IdempotencyKeys.validate(idempotencyHeader)
        } catch (e: IllegalArgumentException) {
            response.status = HttpServletResponse.SC_BAD_REQUEST
            response.writer.write(e.message ?: "Invalid Idempotency Key")
            return
        }

        val redisKey = "gateway:idempotency:$idempotencyKey"
        val cached = redisTemplate.opsForValue().get(redisKey)

        if (cached != null) {
            response.contentType = "application/json"
            response.writer.write(cached)
            return // Skip controller execution entirely!
        }

        filterChain.doFilter(request, response)
    }
}