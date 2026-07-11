package me.roton.axiom.subscription.grpc

import io.grpc.Status
import io.grpc.StatusRuntimeException
import kotlinx.coroutines.test.runTest
import me.roton.axiom.contracts.subscription.upgradeSubscriptionRequest
import me.roton.axiom.subscription.domain.Subscription
import me.roton.axiom.subscription.domain.SubscriptionStatus
import me.roton.axiom.subscription.domain.plan.BillingCycle
import me.roton.axiom.subscription.domain.plan.Plan
import me.roton.axiom.subscription.repository.OutboxRepository
import me.roton.axiom.subscription.repository.PlanRepository
import me.roton.axiom.subscription.repository.SubscriptionRepository
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.grpc.test.autoconfigure.AutoConfigureTestGrpcTransport
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import java.time.Instant
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@AutoConfigureTestGrpcTransport
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
class SubscriptionGrpcServiceTest {

    @Autowired
    lateinit var subscriptionRepository: SubscriptionRepository

    @Autowired
    lateinit var outboxRepository: OutboxRepository

    @Autowired
    lateinit var planRepository: PlanRepository

    @Autowired
    lateinit var subscriptionGrpcService: SubscriptionGrpcService

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

    private fun createTestPlan(name: String = "Test Plan"): Plan = planRepository.save(
        Plan(
            name = name,
            priceAmountCents = 999,
            priceCurrency = "USD",
            billingCycle = BillingCycle.MONTHLY
        )
    )

    private lateinit var original: Subscription
    private lateinit var newPlan: Plan
    private var responseSubscriptionId: String = ""
    private var responseSubscriberId: String = ""
    private var responsePlanId: String = ""

    @BeforeEach
    fun setUp() = runTest {
        val oldPlan = createTestPlan("Old Plan")
        newPlan = createTestPlan("New Plan")

        original = subscriptionRepository.save(
            Subscription(
                subscriberId = UUID.randomUUID(),
                planId = oldPlan.id!!,
                status = SubscriptionStatus.ACTIVE,
                currentPeriodEnd = Instant.now().plusSeconds(3600)
            )
        )

        val response = subscriptionGrpcService.upgradeSubscription(
            upgradeSubscriptionRequest {
                subscriptionId = original.id.toString()
                this.newPlanId = newPlan.id.toString()
            }
        )

        responseSubscriptionId = response.subscriptionId
        responseSubscriberId = response.subscriberId
        responsePlanId = response.planId
    }

    @Test
    fun `old subscription is marked superseded`() {
        val oldSubscription = subscriptionRepository.findById(original.id!!).get()
        assertEquals(SubscriptionStatus.SUPERSEDED, oldSubscription.status)
    }

    @Test
    fun `new subscription keeps the same subscriber`() {
        val oldSubscription = subscriptionRepository.findById(original.id!!).get()
        assertEquals(oldSubscription.subscriberId, UUID.fromString(responseSubscriberId))
    }

    @Test
    fun `new subscription has the requested plan`() {
        assertEquals(newPlan.id.toString(), responsePlanId)
    }

    @Test
    fun `new subscription is active`() {
        val newSubscription = subscriptionRepository.findById(UUID.fromString(responseSubscriptionId)).get()
        assertEquals(SubscriptionStatus.ACTIVE, newSubscription.status)
    }

    @Test
    fun `exactly one outbox event is created`() {
        val outboxEvents = outboxRepository.findAll().filter { it.eventType == "SubscriptionUpgraded" }
        assertEquals(1, outboxEvents.size)
    }

    @Test
    fun `outbox event has the correct event type`() {
        val outboxEvents = outboxRepository.findAll().filter { it.eventType == "SubscriptionUpgraded" }
        assertEquals("SubscriptionUpgraded", outboxEvents[0].eventType)
    }

    @Test
    fun `upgrading a nonexistent subscription throws NOT_FOUND`() = runTest {
        val newPlanForThisTest = createTestPlan("Plan for NOT_FOUND test")

        val exception = assertFailsWith<StatusRuntimeException> {
            subscriptionGrpcService.upgradeSubscription(upgradeSubscriptionRequest {
                subscriptionId = UUID.randomUUID().toString()
                this.newPlanId = newPlanForThisTest.id.toString()
            })
        }

        assertEquals(Status.Code.NOT_FOUND, exception.status.code)
    }

    @Test
    fun `upgrading a subscription that is not in a live status throws FAILED_PRECONDITION`() = runTest {
        val newPlanForThisTest = createTestPlan("Plan for FAILED_PRECONDITION test")

        val cancelledSubscription = subscriptionRepository.save(
            Subscription(
                subscriberId = UUID.randomUUID(),
                planId = createTestPlan("Old cancelled plan").id!!,
                status = SubscriptionStatus.CANCELLED,
                currentPeriodEnd = Instant.now().minusSeconds(3600)
            )
        )

        val exception = assertFailsWith<StatusRuntimeException> {
            subscriptionGrpcService.upgradeSubscription(upgradeSubscriptionRequest {
                subscriptionId = cancelledSubscription.id.toString()
                this.newPlanId = newPlanForThisTest.id.toString()
            })
        }

        assertEquals(Status.Code.FAILED_PRECONDITION, exception.status.code)
    }
}