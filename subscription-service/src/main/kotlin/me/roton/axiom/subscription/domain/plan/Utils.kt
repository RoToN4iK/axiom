package me.roton.axiom.subscription.domain.plan

import io.grpc.Status
import io.grpc.StatusRuntimeException
import me.roton.axiom.common.money.Money
import me.roton.axiom.contracts.common.money

fun Money.toProto(): me.roton.axiom.contracts.common.Money = money {
    amountCents = this@toProto.amountCents
    currency = this@toProto.currency
}

fun me.roton.axiom.contracts.common.BillingCycle.toDomain(): BillingCycle {
    return when (this) {
        me.roton.axiom.contracts.common.BillingCycle.MONTHLY -> BillingCycle.MONTHLY
        me.roton.axiom.contracts.common.BillingCycle.YEARLY -> BillingCycle.YEARLY
        me.roton.axiom.contracts.common.BillingCycle.BILLING_CYCLE_UNSPECIFIED,
        me.roton.axiom.contracts.common.BillingCycle.UNRECOGNIZED -> {
            throw StatusRuntimeException(
                Status.INVALID_ARGUMENT.withDescription("billing_cycle must be MONTHLY or YEARLY")
            )
        }
    }
}