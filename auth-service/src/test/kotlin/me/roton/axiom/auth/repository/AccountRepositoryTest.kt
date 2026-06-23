package me.roton.axiom.auth.repository

import me.roton.axiom.auth.domain.Account
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
class AccountRepositoryTest {

    @Autowired
    lateinit var accountRepository: AccountRepository

    companion object {
        @Container
        @JvmStatic
        val postgres = PostgreSQLContainer("postgres:15")

        @DynamicPropertySource
        @JvmStatic
        fun configureProperties(registry: DynamicPropertyRegistry) {
            println("configureProperties called, jdbcUrl = ${postgres.jdbcUrl}")
            registry.add("spring.datasource.url", postgres::getJdbcUrl)
            registry.add("spring.datasource.username", postgres::getUsername)
            registry.add("spring.datasource.password", postgres::getPassword)
        }
    }

    @Test
    fun `saves and retrieves account by email`() {
        val account = Account(
            email = "test@axiom.dev",
            passwordHash = "hashed_password_placeholder"
        )

        accountRepository.save(account)

        val found = accountRepository.findByEmail("test@axiom.dev")

        assertNotNull(found)
        assertEquals("test@axiom.dev", found?.email)
    }

    @Test
    fun `returns null for unknown email`() {
        val found = accountRepository.findByEmail("nobody@axiom.dev")
        assertNull(found)
    }

    @Test
    fun `enforces unique email constraint`() {
        accountRepository.save(Account(email = "dup@axiom.dev", passwordHash = "hash1"))

        assertThrows(Exception::class.java) {
            accountRepository.save(Account(email = "dup@axiom.dev", passwordHash = "hash2"))
            accountRepository.flush() // force the constraint check immediately
        }
    }
}