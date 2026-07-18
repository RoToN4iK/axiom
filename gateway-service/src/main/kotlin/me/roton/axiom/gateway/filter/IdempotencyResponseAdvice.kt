package me.roton.axiom.gateway.filter

import org.springframework.core.MethodParameter
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.http.MediaType
import org.springframework.http.converter.HttpMessageConverter
import org.springframework.http.server.ServerHttpRequest
import org.springframework.http.server.ServerHttpResponse
import org.springframework.http.server.ServletServerHttpRequest
import org.springframework.web.bind.annotation.ControllerAdvice
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice
import tools.jackson.databind.ObjectMapper
import java.time.Duration

@ControllerAdvice
class IdempotencyResponseAdvice(
    private val redisTemplate: StringRedisTemplate,
    private val objectMapper: ObjectMapper
) : ResponseBodyAdvice<Any> {

    override fun supports(
        returnType: MethodParameter,
        converterType: Class<out HttpMessageConverter<*>>
    ): Boolean = true

    override fun beforeBodyWrite(
        body: Any?,
        returnType: MethodParameter,
        selectedContentType: MediaType,
        selectedConverterType: Class<out HttpMessageConverter<*>>,
        request: ServerHttpRequest,
        response: ServerHttpResponse
    ): Any? {
        // This runs safely AFTER your coroutine finishes, but BEFORE the client gets the data
        if (request is ServletServerHttpRequest && body != null) {
            val idempotencyKey = request.servletRequest.getHeader("Idempotency-Key")

            if (idempotencyKey != null) {
                val redisKey = "gateway:idempotency:$idempotencyKey"
                val jsonBody = objectMapper.writeValueAsString(body)

                // Save the actual JSON to Redis!
                redisTemplate.opsForValue().set(redisKey, jsonBody, Duration.ofHours(24))
            }
        }
        return body
    }
}