package me.roton.axiom.billing

import me.roton.axiom.billing.domain.InvoiceStatus
import me.roton.axiom.billing.repository.InvoiceRepository
import me.roton.axiom.billing.repository.OutboxRepository
import me.roton.axiom.contracts.plan.PlanResponse
import me.roton.axiom.contracts.plan.planResponse
import me.roton.axiom.contracts.plan.GetPlanRequest
import me.roton.axiom.contracts.common.money
import me.roton.axiom.contracts.common.BillingCycle
import kotlinx.coroutines.test.runTest
import me.roton.axiom.billing.kafka.IncomingSubscriptionUpgradedEvent
import me.roton.axiom.billing.kafka.SubscriptionUpgradedConsumer
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.kotlin.any
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import tools.jackson.databind.ObjectMapper
import java.time.Instant
import java.util.UUID

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
class SubscriptionUpgradedConsumerTest {

    @Autowired
    lateinit var invoiceRepository: InvoiceRepository

    @Autowired
    lateinit var outboxRepository: OutboxRepository

    @Autowired
    lateinit var objectMapper: ObjectMapper

    @Autowired
    lateinit var consumer: SubscriptionUpgradedConsumer

    @Autowired
    lateinit var planServiceStub: me.roton.axiom.contracts.plan.PlanServiceGrpcKt.PlanServiceCoroutineStub

    companion object {
        @Container
        @JvmStatic
        val postgres = PostgreSQLContainer("postgres:15")

        @DynamicPropertySource
        @JvmStatic
        fun configureProperties(registry: DynamicPropertyRegistry) {
            registry.add("spring.datasource.url", postgres::getJdbcUrl)
            registry.add("spring.datasource.username", postgres::getUsername)
            registry.add("spring.datasource.password", postgres::getPassword)
        }
    }

    // Replaces the real planServiceStub bean with a mock for this test class only,
    // so GetPlan calls return fixed, known prices instead of hitting a real
    // subscription-service instance over the network.
    @TestConfiguration
    class MockGrpcClientConfig {
        @Bean
        @Primary
        fun mockPlanServiceStub(): me.roton.axiom.contracts.plan.PlanServiceGrpcKt.PlanServiceCoroutineStub {
            return mock(me.roton.axiom.contracts.plan.PlanServiceGrpcKt.PlanServiceCoroutineStub::class.java)
        }
    }

    private val oldPlanId = UUID.randomUUID()
    private val newPlanId = UUID.randomUUID()

    private fun stubPlan(planId: UUID, name: String, priceCents: Long): PlanResponse = planResponse {
        this.planId = planId.toString()
        this.name = name
        this.price = money {
            amountCents = priceCents
            currency = "USD"
        }
        this.billingCycle = BillingCycle.MONTHLY
    }

    @BeforeEach
    fun setUp() = runTest {
        // Fix: Clean the database so data from previous tests doesn't cause size assertions to fail
        invoiceRepository.deleteAll()
        outboxRepository.deleteAll()

        // Fix: Add a second any() matcher to account for the implicit gRPC 'headers' parameter
        whenever(planServiceStub.getPlan(any(), any())).thenAnswer { invocation ->
            val request = invocation.getArgument<GetPlanRequest>(0)
            when (request.planId) {
                oldPlanId.toString() -> stubPlan(oldPlanId, "Basic", 1000)
                newPlanId.toString() -> stubPlan(newPlanId, "Pro", 3000)
                else -> throw IllegalArgumentException("Unexpected planId in test: ${request.planId}")
            }
        }
    }

    private fun buildEventJson(daysRemaining: Long): String {
        val event = IncomingSubscriptionUpgradedEvent(
            subscriptionId = UUID.randomUUID(),
            subscriberId = UUID.randomUUID(),
            oldPlanId = oldPlanId,
            newPlanId = newPlanId,
            status = "ACTIVE",
            // Fix: Added a 60-second buffer. This prevents `Duration.toDays()` in the consumer
            // from truncating 14.999 days down to 14 days due to execution latency.
            oldPeriodEnd = Instant.now().plusSeconds((daysRemaining * 24 * 60 * 60) + 60)
        )
        return objectMapper.writeValueAsString(event)
    }

    @Test
    fun `creates an invoice with the correct net total for a mid-cycle upgrade`() = runTest {
        // 15 days remaining, Basic $10, Pro $30
        // credit = (10/30) * 15 = 5.00, charge = (30/30) * 15 = 15.00, net = 10.00
        consumer.onSubscriptionUpgraded(buildEventJson(daysRemaining = 15))

        val invoices = invoiceRepository.findAll()
        assertEquals(1, invoices.size)
        assertEquals(1000L, invoices[0].totalAmountCents) // $10.00 in cents
    }

    @Test
    fun `invoice starts in PENDING status`() = runTest {
        consumer.onSubscriptionUpgraded(buildEventJson(daysRemaining = 15))

        val invoice = invoiceRepository.findAll().first()
        assertEquals(InvoiceStatus.PENDING, invoice.status)
    }

    @Test
    fun `invoice has exactly two line items - one credit, one charge`() = runTest {
        consumer.onSubscriptionUpgraded(buildEventJson(daysRemaining = 15))

        val invoice = invoiceRepository.findByIdWithLineItems(invoiceRepository.findAll().first().id!!)
        assertEquals(2, invoice?.lineItems?.size)
    }

    @Test
    fun `credit line item is negative, charge line item is positive`() = runTest {
        consumer.onSubscriptionUpgraded(buildEventJson(daysRemaining = 15))

        val invoice = invoiceRepository.findByIdWithLineItems(invoiceRepository.findAll().first().id!!)
        val amounts = invoice?.lineItems?.map { it.amountCents } ?: emptyList()

        assertTrue(amounts.any { it < 0 }, "expected at least one negative (credit) line item")
        assertTrue(amounts.any { it > 0 }, "expected at least one positive (charge) line item")
    }

    @Test
    fun `line items sum to the invoice total`() = runTest {
        consumer.onSubscriptionUpgraded(buildEventJson(daysRemaining = 15))

        val invoice = invoiceRepository.findByIdWithLineItems(invoiceRepository.findAll().first().id!!)!!
        val sumOfLineItems = invoice.lineItems.sumOf { it.amountCents }

        assertEquals(invoice.totalAmountCents, sumOfLineItems)
    }

    @Test
    fun `writes exactly one InvoiceCreated outbox event`() = runTest {
        consumer.onSubscriptionUpgraded(buildEventJson(daysRemaining = 15))

        val matchingEvents = outboxRepository.findAll().filter { it.eventType == "InvoiceCreated" }
        assertEquals(1, matchingEvents.size)
    }

    @Test
    fun `an upgrade near the end of the cycle produces a small net charge`() = runTest {
        // 1 day remaining — credit = (10/30)*1 ≈ 0.33, charge = (30/30)*1 = 1.00, net ≈ 0.67
        consumer.onSubscriptionUpgraded(buildEventJson(daysRemaining = 1))

        val invoice = invoiceRepository.findAll().first()
        // net total should be small and positive — not the full Pro price
        assertTrue(invoice.totalAmountCents in 1..300)
    }
}