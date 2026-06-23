package me.roton.axiom.auth.repository

import me.roton.axiom.auth.domain.Account
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface AccountRepository : JpaRepository<Account, UUID> {
    fun findByEmail(email: String): Account?
}