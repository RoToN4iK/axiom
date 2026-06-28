package me.roton.axiom.subscription.grpc

import io.grpc.Status
import io.grpc.StatusRuntimeException
import kotlinx.coroutines.test.runTest
import me.roton.axiom.contracts.subscription.cancelSubscriptionRequest
import me.roton.axiom.contracts.subscription.createSubscriptionRequest
import me.roton.axiom.subscription.domain.Subscription
import me.roton.axiom.subscription.domain.SubscriptionStatus
import me.roton.axiom.subscription.domain.plan.BillingCycle
import me.roton.axiom.subscription.domain.plan.Plan
import me.roton.axiom.subscription.repository.OutboxRepository
import me.roton.axiom.subscription.repository.PlanRepository
import me.roton.axiom.subscription.repository.SubscriptionRepository
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
import kotlin.test.assertTrue

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@AutoConfigureTestGrpcTransport
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
class SubscriptionGrpcServiceCreateAndCancelTest {

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

    // Shared helper — every CreateSubscription test needs a real, persisted Plan
    // to point at, since createSubscription now validates planId actually exists.
    private fun createTestPlan(): Plan = planRepository.save(
        Plan(
            name = "Test Plan",
            priceAmountCents = 999,
            priceCurrency = "USD",
            billingCycle = BillingCycle.MONTHLY
        )
    )

    @Test
    fun `creating a subscription for a brand new subscriber succeeds`() = runTest {
        val subscriberId = UUID.randomUUID()
        val plan = createTestPlan()

        val response = subscriptionGrpcService.createSubscription(
            createSubscriptionRequest {
                this.subscriberId = subscriberId.toString()
                this.planId = plan.id.toString()
            }
        )

        assertEquals(subscriberId.toString(), response.subscriberId)
        assertEquals(plan.id.toString(), response.planId)
    }

    @Test
    fun `creating a subscription starts it in TRIALING status`() = runTest {
        val subscriberId = UUID.randomUUID()
        val plan = createTestPlan()

        val response = subscriptionGrpcService.createSubscription(
            createSubscriptionRequest {
                this.subscriberId = subscriberId.toString()
                this.planId = plan.id.toString()
            }
        )

        val saved = subscriptionRepository.findById(UUID.fromString(response.subscriptionId)).get()
        assertEquals(SubscriptionStatus.TRIALING, saved.status)
    }

    @Test
    fun `creating a subscription writes exactly one SubscriptionCreated outbox event`() = runTest {
        val subscriberId = UUID.randomUUID()
        val plan = createTestPlan()

        subscriptionGrpcService.createSubscription(
            createSubscriptionRequest {
                this.subscriberId = subscriberId.toString()
                this.planId = plan.id.toString()
            }
        )

        val matchingEvents = outboxRepository.findAll().filter { it.eventType == "SubscriptionCreated" }
        assertEquals(1, matchingEvents.size)
    }

    @Test
    fun `creating a subscription for a nonexistent plan throws NOT_FOUND`() = runTest {
        // new test — covers the guard clause that was just added
        val subscriberId = UUID.randomUUID()

        val exception = assertFailsWith<StatusRuntimeException> {
            subscriptionGrpcService.createSubscription(
                createSubscriptionRequest {
                    this.subscriberId = subscriberId.toString()
                    this.planId = UUID.randomUUID().toString() // no Plan ever saved with this id
                }
            )
        }

        assertEquals(Status.Code.NOT_FOUND, exception.status.code)
    }

    @Test
    fun `creating a second subscription for a subscriber who already has a live one throws ALREADY_EXISTS`() = runTest {
        val subscriberId = UUID.randomUUID()
        val plan = createTestPlan()

        // first subscription — should succeed
        subscriptionGrpcService.createSubscription(
            createSubscriptionRequest {
                this.subscriberId = subscriberId.toString()
                this.planId = plan.id.toString()
            }
        )

        // the second attempt for the SAME subscriber — should be rejected
        val exception = assertFailsWith<StatusRuntimeException> {
            subscriptionGrpcService.createSubscription(
                createSubscriptionRequest {
                    this.subscriberId = subscriberId.toString()
                    this.planId = plan.id.toString()
                }
            )
        }

        assertEquals(Status.Code.ALREADY_EXISTS, exception.status.code)
    }

    @Test
    fun `creating a subscription for a subscriber whose only existing subscription is cancelled succeeds`() = runTest {
        // this proves the live-status check is correctly scoped — a subscriber with
        // only a CANCELLED row should be treated as having no current subscription
        val subscriberId = UUID.randomUUID()
        val plan = createTestPlan()

        subscriptionRepository.save(
            Subscription(
                subscriberId = subscriberId,
                planId = plan.id!!,
                status = SubscriptionStatus.CANCELLED,
                currentPeriodEnd = Instant.now().minusSeconds(3600)
            )
        )

        val response = subscriptionGrpcService.createSubscription(
            createSubscriptionRequest {
                this.subscriberId = subscriberId.toString()
                this.planId = plan.id.toString()
            }
        )

        assertTrue(response.subscriptionId.isNotBlank())
    }

    @Test
    fun `cancelling an active subscription marks it CANCELLED`() = runTest {
        val subscription = subscriptionRepository.save(
            Subscription(
                subscriberId = UUID.randomUUID(),
                planId = UUID.randomUUID(),
                status = SubscriptionStatus.ACTIVE,
                currentPeriodEnd = Instant.now().plusSeconds(3600)
            )
        )

        subscriptionGrpcService.cancelSubscription(
            cancelSubscriptionRequest {
                subscriptionId = subscription.id.toString()
            }
        )

        val updated = subscriptionRepository.findById(subscription.id!!).get()
        assertEquals(SubscriptionStatus.CANCELLED, updated.status)
    }

    @Test
    fun `cancelling a trialing subscription is allowed`() = runTest {
        val subscription = subscriptionRepository.save(
            Subscription(
                subscriberId = UUID.randomUUID(),
                planId = UUID.randomUUID(),
                status = SubscriptionStatus.TRIALING,
                currentPeriodEnd = Instant.now().plusSeconds(3600)
            )
        )

        subscriptionGrpcService.cancelSubscription(
            cancelSubscriptionRequest {
                subscriptionId = subscription.id.toString()
            }
        )

        val updated = subscriptionRepository.findById(subscription.id!!).get()
        assertEquals(SubscriptionStatus.CANCELLED, updated.status)
    }

    @Test
    fun `cancelling writes exactly one SubscriptionCancelled outbox event`() = runTest {
        val subscription = subscriptionRepository.save(
            Subscription(
                subscriberId = UUID.randomUUID(),
                planId = UUID.randomUUID(),
                status = SubscriptionStatus.ACTIVE,
                currentPeriodEnd = Instant.now().plusSeconds(3600)
            )
        )

        subscriptionGrpcService.cancelSubscription(
            cancelSubscriptionRequest {
                subscriptionId = subscription.id.toString()
            }
        )

        val matchingEvents = outboxRepository.findAll().filter { it.eventType == "SubscriptionCancelled" }
        assertEquals(1, matchingEvents.size)
    }

    @Test
    fun `cancelling a nonexistent subscription throws NOT_FOUND`() = runTest {
        val exception = assertFailsWith<StatusRuntimeException> {
            subscriptionGrpcService.cancelSubscription(
                cancelSubscriptionRequest {
                    subscriptionId = UUID.randomUUID().toString()
                }
            )
        }

        assertEquals(Status.Code.NOT_FOUND, exception.status.code)
    }

    @Test
    fun `cancelling an already cancelled subscription throws FAILED_PRECONDITION`() = runTest {
        val subscription = subscriptionRepository.save(
            Subscription(
                subscriberId = UUID.randomUUID(),
                planId = UUID.randomUUID(),
                status = SubscriptionStatus.CANCELLED,
                currentPeriodEnd = Instant.now().minusSeconds(3600)
            )
        )

        val exception = assertFailsWith<StatusRuntimeException> {
            subscriptionGrpcService.cancelSubscription(
                cancelSubscriptionRequest {
                    subscriptionId = subscription.id.toString()
                }
            )
        }

        assertEquals(Status.Code.FAILED_PRECONDITION, exception.status.code)
    }

    @Test
    fun `cancelling a superseded subscription throws FAILED_PRECONDITION`() = runTest {
        // a superseded row is "dead" in the same way a cancelled one is —
        // upgrading creates a new row and marks the old one SUPERSEDED,
        // so the old row should never be cancellable either
        val subscription = subscriptionRepository.save(
            Subscription(
                subscriberId = UUID.randomUUID(),
                planId = UUID.randomUUID(),
                status = SubscriptionStatus.SUPERSEDED,
                currentPeriodEnd = Instant.now().minusSeconds(3600)
            )
        )

        val exception = assertFailsWith<StatusRuntimeException> {
            subscriptionGrpcService.cancelSubscription(
                cancelSubscriptionRequest {
                    subscriptionId = subscription.id.toString()
                }
            )
        }

        assertEquals(Status.Code.FAILED_PRECONDITION, exception.status.code)
    }
}