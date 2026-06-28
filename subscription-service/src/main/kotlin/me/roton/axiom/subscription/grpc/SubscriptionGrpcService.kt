package me.roton.axiom.subscription.grpc

import io.grpc.Status
import io.grpc.StatusRuntimeException
import me.roton.axiom.common.time.Clock
import me.roton.axiom.contracts.subscription.CancelSubscriptionRequest
import me.roton.axiom.contracts.subscription.CreateSubscriptionRequest
import me.roton.axiom.contracts.subscription.GetSubscriptionRequest
import me.roton.axiom.contracts.subscription.SubscriptionResponse
import me.roton.axiom.contracts.subscription.SubscriptionServiceGrpcKt
import me.roton.axiom.contracts.subscription.UpgradeSubscriptionRequest
import me.roton.axiom.contracts.subscription.UpgradeSubscriptionResponse
import me.roton.axiom.contracts.subscription.upgradeSubscriptionResponse
import me.roton.axiom.subscription.domain.outbox.OutboxCancelPayload
import me.roton.axiom.subscription.domain.outbox.OutboxEvent
import me.roton.axiom.subscription.domain.outbox.OutboxUpgradePayload
import me.roton.axiom.subscription.domain.Subscription
import me.roton.axiom.subscription.domain.SubscriptionStatus
import me.roton.axiom.subscription.domain.outbox.OutboxCreatePayload
import me.roton.axiom.subscription.domain.plan.nextPeriodEnd
import me.roton.axiom.subscription.domain.toProto
import me.roton.axiom.subscription.domain.toResponse
import me.roton.axiom.subscription.repository.OutboxRepository
import me.roton.axiom.subscription.repository.PlanRepository
import me.roton.axiom.subscription.repository.SubscriptionRepository
import org.slf4j.LoggerFactory
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import tools.jackson.databind.ObjectMapper
import java.util.UUID
import java.util.concurrent.TimeUnit

@Service
class SubscriptionGrpcService(
    private val subscriptionRepository: SubscriptionRepository,
    private val outboxRepository: OutboxRepository,
    private val objectMapper: ObjectMapper,
    private val kafkaTemplate: KafkaTemplate<String, String>,
    private val clock: Clock,
    private val planRepository: PlanRepository
): SubscriptionServiceGrpcKt.SubscriptionServiceCoroutineImplBase() {
    private val logger = LoggerFactory.getLogger(SubscriptionGrpcService::class.java)

    @Transactional
    override suspend fun upgradeSubscription(request: UpgradeSubscriptionRequest): UpgradeSubscriptionResponse {
        val matches = subscriptionRepository.findById(UUID.fromString(request.subscriptionId))
         if (matches.isEmpty) {
            throw StatusRuntimeException(
                Status.NOT_FOUND.withDescription("Subscription with id ${request.subscriptionId} not found")
            )
        }

        val oldSubscription = matches.get()

        if (oldSubscription.status !in listOf(
                SubscriptionStatus.TRIALING,
                SubscriptionStatus.ACTIVE,
                SubscriptionStatus.PAST_DUE)
            ) {
            throw StatusRuntimeException(
                Status.FAILED_PRECONDITION.withDescription("Cannot upgrade subscription ${request.subscriptionId}")
            )
        }

        oldSubscription.status = SubscriptionStatus.SUPERSEDED

        subscriptionRepository.save(oldSubscription)

        val newSubscription = Subscription(
            subscriberId = oldSubscription.subscriberId,
            status = SubscriptionStatus.ACTIVE,
            planId = UUID.fromString(request.newPlanId),
            currentPeriodEnd = clock.now() //TODO: change to real plan time
        )
        subscriptionRepository.save(newSubscription)

        val outboxPayload = OutboxUpgradePayload(
            subscriptionId = newSubscription.id!!,
            subscriberId = newSubscription.subscriberId,
            oldPlanId = oldSubscription.planId,
            newPlanId = newSubscription.planId,
            status = newSubscription.status,
            currentPeriodEnd = newSubscription.currentPeriodEnd
        )

        val outboxEvent = OutboxEvent(
            eventType = "SubscriptionUpgraded",
            payload = objectMapper.writeValueAsString(outboxPayload)
        )

        outboxRepository.save(outboxEvent)

        return upgradeSubscriptionResponse {
            subscriptionId = newSubscription.id.toString()
            subscriberId = newSubscription.subscriberId.toString()
            planId = newSubscription.planId.toString()
            status = newSubscription.status.toProto()
            currentPeriodEnd = newSubscription.currentPeriodEnd.toString()
        }
    }

    override suspend fun getSubscription(request: GetSubscriptionRequest): SubscriptionResponse {
        val matches = subscriptionRepository.findById(UUID.fromString(request.subscriptionId))

        if (matches.isEmpty) {
            throw StatusRuntimeException(
                Status.NOT_FOUND.withDescription("Subscription with id ${request.subscriptionId} not found")
            )
        }

        val subscription = matches.get()
        return subscription.toResponse()
    }

    @Transactional
    override suspend fun cancelSubscription(request: CancelSubscriptionRequest): SubscriptionResponse {
        val matches = subscriptionRepository.findById(UUID.fromString(request.subscriptionId))
        if (matches.isEmpty) {
            throw StatusRuntimeException(
                Status.NOT_FOUND.withDescription("Subscription with id ${request.subscriptionId} not found")
            )
        }

        val subscription = matches.get()

        if (subscription.status !in listOf(
                SubscriptionStatus.TRIALING,
                SubscriptionStatus.ACTIVE,
                SubscriptionStatus.PAST_DUE)
            ) {
            throw StatusRuntimeException(
                Status.FAILED_PRECONDITION.withDescription("Cannot cancel already cancelled subscription ${request.subscriptionId}")
            )
        }

        subscription.status = SubscriptionStatus.CANCELLED
        subscriptionRepository.save(subscription)

        val outboxPayload = OutboxCancelPayload(
            subscriptionId = subscription.id!!,
            subscriberId = subscription.subscriberId,
            planId = subscription.planId,
            currentPeriodEnd = subscription.currentPeriodEnd
        )

        val outboxEvent = OutboxEvent(
            eventType = "SubscriptionCancelled",
            payload = objectMapper.writeValueAsString(outboxPayload)
        )

        outboxRepository.save(outboxEvent)

        return subscription.toResponse()
    }

    @Transactional
    override suspend fun createSubscription(request: CreateSubscriptionRequest): SubscriptionResponse {
        val matchingSubscriptions = subscriptionRepository.findBySubscriberIdAndStatusIn(UUID.fromString(request.subscriberId))
        if (!matchingSubscriptions.isEmpty()) {
            throw StatusRuntimeException(
                Status.ALREADY_EXISTS.withDescription("User already have subscription: ${matchingSubscriptions[0].id}")
            )
        }

        val matchingPlan = planRepository.findById(UUID.fromString(request.planId))
        if (matchingPlan.isEmpty) {
            throw StatusRuntimeException(
                Status.NOT_FOUND.withDescription("There is no plan: ${request.planId}")
            )
        }

        val subscription = Subscription(
            subscriberId = UUID.fromString(request.subscriberId),
            planId = UUID.fromString(request.planId),
            status = SubscriptionStatus.TRIALING,
            currentPeriodEnd = matchingPlan.get().billingCycle.nextPeriodEnd(clock.now())
        )

        subscriptionRepository.save(subscription)

        val outboxPayload = OutboxCreatePayload(
            subscriptionId = subscription.id!!,
            subscriberId = subscription.subscriberId,
            planId = subscription.planId,
            currentPeriodEnd = subscription.currentPeriodEnd
        )

        val outboxEvent = OutboxEvent(
            eventType = "SubscriptionCreated",
            payload = objectMapper.writeValueAsString(outboxPayload),
        )

        outboxRepository.save(outboxEvent)

        return subscription.toResponse()
    }

    @Scheduled(fixedRate = 15, timeUnit = TimeUnit.SECONDS)
    fun poller() {
        val outboxEvents = outboxRepository.findFirst10ByPublishedAtIsNullOrderByCreatedAtAsc()

        outboxEvents.forEach { event ->
            val topic = resolveTopic(event.eventType)

            try {
                kafkaTemplate.send(topic, event.id.toString(), event.payload).get()
                event.publishedAt = clock.now()
                outboxRepository.save(event)
            } catch (e: Exception) {
                logger.error("Failed to publish outbox event ${event.id}", e)
            }
        }
    }

    private fun resolveTopic(eventType: String): String = when (eventType) {
        "SubscriptionUpgraded" -> "subscription.upgraded"
        "SubscriptionCreated" -> "subscription.created"
        "SubscriptionCancelled" -> "subscription.cancelled"
        else -> throw IllegalStateException("No topic mapping for event type: $eventType")
    }
}