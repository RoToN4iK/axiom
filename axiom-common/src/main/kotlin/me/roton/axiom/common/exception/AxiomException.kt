package me.roton.axiom.common.exception

sealed class AxiomException(message: String): RuntimeException(message)

class ResourceNotFoundException(resource: String, id: String) : AxiomException("$resource with id=$id not found")

class InvalidStateTransitionException(message: String) : AxiomException(message)

class IdempotencyConflictException(key: String) : AxiomException("Request with idempotency key=$key already processed")