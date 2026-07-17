package me.roton.axiom.auth.repository

import me.roton.axiom.auth.domain.ApiKey
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface ApiKeyRepository : JpaRepository<ApiKey, UUID> {
    fun findByKeyHash(keyHash: String): ApiKey?
    fun existsByActiveTrue(): Boolean
}