package me.roton.axiom.billing.config

import me.roton.axiom.contracts.plan.PlanServiceGrpcKt
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.grpc.client.GrpcChannelFactory

@Configuration
class GrpcClientConfig {
    @Bean
    fun planServiceStub(channels: GrpcChannelFactory): PlanServiceGrpcKt.PlanServiceCoroutineStub {
        return PlanServiceGrpcKt.PlanServiceCoroutineStub(
            channels.createChannel("subscription-service")
        )
    }
}