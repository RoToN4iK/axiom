package me.roton.axiom.billing

import me.roton.axiom.contracts.plan.GetPlanRequest
import me.roton.axiom.contracts.plan.PlanResponse
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary

@TestConfiguration
class MockGrpcClientConfig {
    @Bean
    @Primary
    fun mockPlanServiceStub(): FakePlanServiceStub = FakePlanServiceStub()
}

class FakePlanServiceStub {
    val plans = mutableMapOf<String, PlanResponse>()

    fun getPlan(request: GetPlanRequest): PlanResponse {
        return plans[request.planId]
            ?: throw IllegalArgumentException("Unexpected planId in test: ${request.planId}")
    }
}