package me.roton.axiom.gateway.controller

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import me.roton.axiom.common.money.Money
import me.roton.axiom.contracts.common.BillingCycle
import me.roton.axiom.contracts.plan.PlanServiceGrpcKt
import me.roton.axiom.contracts.plan.createPlanRequest
import me.roton.axiom.contracts.plan.getPlanRequest
import me.roton.axiom.contracts.plan.listPlansRequest
import me.roton.axiom.gateway.annotation.IdempotencyKeyDoc
import me.roton.axiom.gateway.domain.plan.CreatePlanRequest
import me.roton.axiom.gateway.domain.plan.ListPlansResponse
import me.roton.axiom.gateway.domain.plan.PlanResponse
import me.roton.axiom.gateway.utils.toProto
import org.springframework.web.bind.annotation.*
import java.util.*

@RestController
@Tag(name = "Plan", description = "Manage subscription plans — pricing, billing cycle")
class PlanController(
    private val planServiceStub: PlanServiceGrpcKt.PlanServiceCoroutineStub
) {

    @PostMapping("/api/plans")
    @Operation(
        summary = "Create a new plan",
        description = "Creates a new subscription plan with a fixed price and billing cycle. " +
                "Existing subscribers on other plans are unaffected."
    )
    suspend fun createPlan(
        @IdempotencyKeyDoc @RequestHeader("Idempotency-Key") idempotencyKey: String,
        @Valid @RequestBody request: CreatePlanRequest
    ): PlanResponse {
        val price = Money(request.priceAmountCents, request.currency)

        val grpcResponse = planServiceStub.createPlan(
            createPlanRequest {
                this.name = request.name
                this.price = price.toProto()
                this.billingCycle = BillingCycle.valueOf(request.billingCycle)
            }
        )

        return PlanResponse(
            planId = UUID.fromString(grpcResponse.planId),
            name = grpcResponse.name,
            priceAmountCents = grpcResponse.price.amountCents,
            currency = grpcResponse.price.currency,
            billingCycle = grpcResponse.billingCycle.toString()
        )
    }

    @GetMapping("/api/plans/{id}")
    @Operation(
        summary = "Get a plan by ID",
        description = "Returns a single plan's details, including its current price and billing cycle."
    )
    suspend fun getPlan(
        @PathVariable id: String
    ): PlanResponse {
        val grpcResponse = planServiceStub.getPlan(
            getPlanRequest { planId = id }
        )

        return PlanResponse(
            planId = UUID.fromString(grpcResponse.planId),
            name = grpcResponse.name,
            priceAmountCents = grpcResponse.price.amountCents,
            currency = grpcResponse.price.currency,
            billingCycle = grpcResponse.billingCycle.toString()
        )
    }

    @GetMapping("/api/plans")
    @Operation(
        summary = "List active plans",
        description = "Returns every currently active plan. Inactive/retired plans are omitted — " +
                "existing subscribers on a retired plan keep their subscription, but it no longer " +
                "appears here for new signups."
    )
    suspend fun listPlans(): ListPlansResponse {
        val grpcResponse = planServiceStub.listPlans(
            listPlansRequest {  }
        )

        return ListPlansResponse(plans = grpcResponse.plansList.map {
            PlanResponse(
                planId = UUID.fromString(it.planId),
                name = it.name,
                priceAmountCents = it.price.amountCents,
                currency = it.price.currency,
                billingCycle = it.billingCycle.toString()
            )
        }
        )
    }
}