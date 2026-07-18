package me.roton.axiom.gateway.config

import me.roton.axiom.contracts.auth.AuthServiceGrpcKt
import me.roton.axiom.contracts.plan.PlanServiceGrpcKt
import me.roton.axiom.contracts.subscription.SubscriptionServiceGrpcKt
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.grpc.client.GrpcChannelFactory

@Configuration
class GrpcClientConfig {
    @Bean
    fun authServiceStub(channels: GrpcChannelFactory): AuthServiceGrpcKt.AuthServiceCoroutineStub {
        return AuthServiceGrpcKt.AuthServiceCoroutineStub(channels.createChannel("auth-service"))
    }

    @Bean
    fun subscriptionServiceStub(channels: GrpcChannelFactory): SubscriptionServiceGrpcKt.SubscriptionServiceCoroutineStub {
        return SubscriptionServiceGrpcKt.SubscriptionServiceCoroutineStub(channels.createChannel("subscription-service"))
    }

    @Bean
    fun planServiceStub(channels: GrpcChannelFactory): PlanServiceGrpcKt.PlanServiceCoroutineStub {
        return PlanServiceGrpcKt.PlanServiceCoroutineStub(channels.createChannel("subscription-service"))
    }
}