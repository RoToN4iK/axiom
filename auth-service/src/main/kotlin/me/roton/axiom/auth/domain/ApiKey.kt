package me.roton.axiom.auth.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.hibernate.annotations.CreationTimestamp
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "api_keys")
class ApiKey(
    @Id
    @GeneratedValue
    val id: UUID? = null,

    @Column(name = "company_name", nullable = false)
    val companyName: String,

    @Column(name = "key_hash", nullable = false, unique = true)
    val keyHash: String,

    @Column(nullable = false)
    var active: Boolean = true
) {
    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    var createdAt: Instant? = null
}