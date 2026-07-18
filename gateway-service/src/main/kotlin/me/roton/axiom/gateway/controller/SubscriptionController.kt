package me.roton.axiom.gateway.controller

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import me.roton.axiom.contracts.subscription.SubscriptionServiceGrpcKt
import me.roton.axiom.contracts.subscription.cancelSubscriptionRequest
import me.roton.axiom.contracts.subscription.createSubscriptionRequest
import me.roton.axiom.contracts.subscription.getSubscriptionRequest
import me.roton.axiom.contracts.subscription.upgradeSubscriptionRequest
import me.roton.axiom.gateway.annotation.IdempotencyKeyDoc
import me.roton.axiom.gateway.domain.subscription.CreateSubscriptionRequest
import me.roton.axiom.gateway.domain.subscription.SubscriptionResponse
import me.roton.axiom.gateway.domain.subscription.UpgradeSubscriptionRequest
import me.roton.axiom.gateway.utils.toDomain
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RestController

@RestController
@Tag(name = "Subscription", description = "Manage subscription lifecycle — create, cancel, upgrade")
class SubscriptionController(
    private val subscriptionServiceStub: SubscriptionServiceGrpcKt.SubscriptionServiceCoroutineStub
) {

    @PostMapping("/api/subscriptions")
    @Operation(
        summary = "Create a new subscription",
        description = "Subscribes a subscriber to a plan, starting in TRIALING status. Fails with " +
                "409 Conflict if the subscriber already has a live subscription (TRIALING, ACTIVE, " +
                "or PAST_DUE) — use the upgrade endpoint to change plans instead."
    )
    suspend fun createSubscription(
        @IdempotencyKeyDoc @RequestHeader("Idempotency-Key") idempotencyKey: String,
        @Valid @RequestBody request: CreateSubscriptionRequest
    ): SubscriptionResponse {
        val grpcResponse = subscriptionServiceStub.createSubscription(
            createSubscriptionRequest {
                subscriberId = request.subscriberId
                planId = request.planId
            }
        )

        return grpcResponse.toDomain()
    }

    @GetMapping("/api/subscriptions/{id}")
    @Operation(
        summary = "Get a subscription by ID",
        description = "Returns a single subscription's current state — plan, status, and when its " +
                "current billing period ends."
    )
    suspend fun getSubscription(
        @PathVariable id: String
    ): SubscriptionResponse {
        val grpcResponse = subscriptionServiceStub.getSubscription(
            getSubscriptionRequest { subscriptionId = id }
        )

        return grpcResponse.toDomain()
    }

    @PostMapping("/api/subscriptions/{id}/cancel")
    @Operation(
        summary = "Cancel a subscription",
        description = "Marks a subscription as CANCELLED. This is final — a cancelled subscription " +
                "cannot be reactivated; the subscriber must create a new subscription to resume. " +
                "Fails with 409 Conflict if the subscription is already cancelled or superseded by an upgrade."
    )
    suspend fun cancelSubscription(
        @IdempotencyKeyDoc @RequestHeader("Idempotency-Key") idempotencyKey: String,
        @PathVariable id: String
    ): SubscriptionResponse {
        val grpcResponse = subscriptionServiceStub.cancelSubscription(
            cancelSubscriptionRequest { subscriptionId = id }
        )

        return grpcResponse.toDomain()
    }

    @PostMapping("/api/subscriptions/{id}/upgrade")
    @Operation(
        summary = "Upgrade a subscription to a new plan",
        description = "Moves a subscription to a new plan. The existing subscription is marked " +
                "SUPERSEDED and a new one is created on the new plan — it is not modified in place. " +
                "Triggers proration billing asynchronously via billing-service. Fails with 409 Conflict " +
                "if the subscription is not currently in a live status (TRIALING, ACTIVE, or PAST_DUE)."
    )
    suspend fun upgradeSubscription(
        @IdempotencyKeyDoc @RequestHeader("Idempotency-Key") idempotencyKey: String,
        @PathVariable id: String,
        @Valid @RequestBody request: UpgradeSubscriptionRequest
    ): SubscriptionResponse {
        val grpcResponse = subscriptionServiceStub.upgradeSubscription(
            upgradeSubscriptionRequest {
                subscriptionId = id
                newPlanId = request.newPlanId
            }
        )

        return grpcResponse.toDomain()
    }
}