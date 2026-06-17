package me.roton.axiom.common.idempotency

object IdempotencyKeys {
    fun validate(key: String?): String {
        require(!key.isNullOrBlank()) { "Idempotency-Key header is required" }
        require(key.length <= 255) { "Idempotency-Key too long" }
        return key
    }
}