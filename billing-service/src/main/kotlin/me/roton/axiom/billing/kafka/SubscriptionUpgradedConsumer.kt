package me.roton.axiom.billing.kafka

import me.roton.axiom.billing.domain.Invoice
import me.roton.axiom.billing.domain.InvoiceStatus
import me.roton.axiom.billing.domain.LineItem
import me.roton.axiom.billing.domain.outbox.OutboxEvent
import me.roton.axiom.billing.domain.outbox.OutboxInvoiceCreatedPayload
import me.roton.axiom.billing.domain.outbox.OutboxLineItem
import me.roton.axiom.billing.repository.InvoiceRepository
import me.roton.axiom.billing.repository.OutboxRepository
import me.roton.axiom.common.money.Money
import me.roton.axiom.common.time.Clock
import me.roton.axiom.contracts.plan.PlanServiceGrpcKt
import me.roton.axiom.contracts.plan.getPlanRequest
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import tools.jackson.databind.ObjectMapper
import java.math.BigDecimal
import java.time.Duration
import java.util.concurrent.TimeUnit

@Service
class SubscriptionUpgradedConsumer(
    private val objectMapper: ObjectMapper,
    private val invoiceRepository: InvoiceRepository,
    private val outboxRepository: OutboxRepository,
    private val kafkaTemplate: KafkaTemplate<String, String>,
    private val clock: Clock,
    private val planServiceStub: PlanServiceGrpcKt.PlanServiceCoroutineStub
) {
    private val logger = org.slf4j.LoggerFactory.getLogger(SubscriptionUpgradedConsumer::class.java)

    @KafkaListener(topics = ["subscription.upgraded"])
    @Transactional
    suspend fun onSubscriptionUpgraded(message: String) {
        val event = objectMapper.readValue(message, IncomingSubscriptionUpgradedEvent::class.java)
        val oldPlan = planServiceStub.getPlan(getPlanRequest { planId = event.oldPlanId.toString() })
        val newPlan = planServiceStub.getPlan(getPlanRequest { planId = event.newPlanId.toString() })

        val daysRemaining: Long = Duration.between(clock.now(), event.oldPeriodEnd).toDays()

        val oldPlanMoney = Money(oldPlan.price.amountCents, oldPlan.price.currency)
        val newPlanMoney = Money(newPlan.price.amountCents, newPlan.price.currency)

        // Multiply first, then divide to prevent premature rounding errors!
        val credit = (oldPlanMoney * BigDecimal(daysRemaining)) / BigDecimal(30)
        val charge = (newPlanMoney * BigDecimal(daysRemaining)) / BigDecimal(30)
        val total = charge - credit

        val invoice = Invoice(
            subscriptionId = event.subscriptionId,
            subscriberId = event.subscriberId,
            totalAmountCents = total.amountCents,
            currency = total.currency,
            status = InvoiceStatus.PENDING,
        )

        invoice.lineItems.add(
            LineItem(
                invoice = invoice,
                description = "Credit for $daysRemaining days of ${oldPlan.name}",
                amountCents = -credit.amountCents
            )
        )

        invoice.lineItems.add(
            LineItem(
                invoice = invoice,
                description = "Charge for $daysRemaining days of ${newPlan.name}",
                amountCents = charge.amountCents
            )
        )

        invoiceRepository.save(invoice)

        val outboxPayload = OutboxInvoiceCreatedPayload(
            invoiceId = invoice.id!!,
            subscriptionId = invoice.subscriptionId,
            subscriberId = invoice.subscriberId,
            totalAmountCents = invoice.totalAmountCents,
            currency = invoice.currency,
            lineItems = invoice.lineItems.map { OutboxLineItem(it.description, it.amountCents) }
        )

        val outboxEvent = OutboxEvent(
            eventType = "InvoiceCreated",
            payload = objectMapper.writeValueAsString(outboxPayload)
        )

        outboxRepository.save(outboxEvent)
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
        "InvoiceCreated" -> "invoice.created"
        else -> throw IllegalStateException("No topic mapping for event type: $eventType")
    }
}