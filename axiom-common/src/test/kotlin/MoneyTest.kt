package me.roton.axiom.common.money

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import java.math.BigDecimal

class MoneyTest {

    @Test
    fun `rejects lowercase currency`() {
        assertThrows(IllegalArgumentException::class.java) {
            Money(1000, "usd")
        }
    }

    @Test
    fun `plus adds same currency`() {
        val result = Money(1000, "USD") + Money(500, "USD")
        assertEquals(1500L, result.amountCents)
    }

    @Test
    fun `plus rejects mismatched currency`() {
        assertThrows(IllegalArgumentException::class.java) {
            Money(1000, "USD") + Money(500, "EUR")
        }
    }

    @Test
    fun `times handles fractional scalar correctly`() {
        val half = Money(1000, "USD") * BigDecimal("0.5")
        assertEquals(500L, half.amountCents)
    }

    @Test
    fun `div handles non-terminating decimal without throwing`() {
        val result = Money(1000, "USD") / BigDecimal(3)
        assertEquals(333L, result.amountCents)
    }

    @Test
    fun `toDecimal converts cents correctly`() {
        assertEquals(BigDecimal("10.00"), Money(1000, "USD").toDecimal())
    }
}