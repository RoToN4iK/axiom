package me.roton.axiom.common.money

import java.math.BigDecimal
import java.math.RoundingMode

data class Money(val amountCents: Long, val currency: String) {
    init {
        require(currency.length == 3 && currency == currency.uppercase()) {
            "Currency must be a 3-letter uppercase ISO 4217 code, got: $currency"
        }
    }

    fun toDecimal(): BigDecimal = BigDecimal(amountCents).movePointLeft(2)

    operator fun plus(other: Money): Money {
        require(this.currency == other.currency) { "Currency mismatch: ${this.currency} != ${other.currency}" }
        return Money(amountCents + other.amountCents, currency)
    }

    operator fun minus(other: Money): Money {
        require(currency == other.currency) { "Currency mismatch: ${this.currency} != ${other.currency}" }
        return Money(amountCents - other.amountCents, currency)
    }

    operator fun times(scalar: BigDecimal): Money {
        val newAmount = BigDecimal(amountCents)
            .multiply(scalar)
            .setScale(0, RoundingMode.HALF_UP)
            .longValueExact()
        return Money(newAmount, currency)
    }

    operator fun div(scalar: BigDecimal): Money {
        val newAmount = BigDecimal(amountCents)
            .divide(scalar, 0, RoundingMode.HALF_UP)
            .longValueExact()
        return Money(newAmount, currency)
    }
}