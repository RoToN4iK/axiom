package me.roton.axiom.gateway.annotation

import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.enums.ParameterIn

@Target(AnnotationTarget.VALUE_PARAMETER)
@Retention(AnnotationRetention.RUNTIME)
@Parameter(
    name = "Idempotency-Key",
    `in` = ParameterIn.HEADER,
    required = true,
    description = "Client-generated unique key to safely retry this request without double-processing"
)
annotation class IdempotencyKeyDoc