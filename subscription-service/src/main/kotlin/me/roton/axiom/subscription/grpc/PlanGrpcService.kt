package me.roton.axiom.subscription.grpc

import io.grpc.Status
import io.grpc.StatusRuntimeException
import me.roton.axiom.contracts.plan.CreatePlanRequest
import me.roton.axiom.contracts.plan.GetPlanRequest
import me.roton.axiom.contracts.plan.ListPlansRequest
import me.roton.axiom.contracts.plan.ListPlansResponse
import me.roton.axiom.contracts.plan.PlanResponse
import me.roton.axiom.contracts.plan.PlanServiceGrpcKt
import me.roton.axiom.contracts.plan.listPlansResponse
import me.roton.axiom.subscription.domain.plan.Plan
import me.roton.axiom.subscription.domain.plan.toDomain
import me.roton.axiom.subscription.domain.plan.toResponse
import me.roton.axiom.subscription.repository.PlanRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
class PlanGrpcService(
    private val planRepository: PlanRepository
) : PlanServiceGrpcKt.PlanServiceCoroutineImplBase() {

    @Transactional
    override suspend fun createPlan(request: CreatePlanRequest): PlanResponse {
        val price = request.price

        val plan = Plan(
            name = request.name,
            priceAmountCents = price.amountCents,
            priceCurrency = price.currency,
            billingCycle = request.billingCycle.toDomain()
        )

        planRepository.save(plan)

        return plan.toResponse()
    }

    override suspend fun getPlan(request: GetPlanRequest): PlanResponse {
        val matches = planRepository.findById(UUID.fromString(request.planId))
        if (matches.isEmpty) {
            throw StatusRuntimeException(
                Status.NOT_FOUND.withDescription("Plan with id ${request.planId} not found")
            )
        }

        val plan = matches.get()
        return plan.toResponse()
    }

    override suspend fun listPlans(request: ListPlansRequest): ListPlansResponse {
        val activePlans = planRepository.findPlansByActive()

        return listPlansResponse {
            plans.addAll(activePlans.map { it.toResponse() })
        }
    }
}