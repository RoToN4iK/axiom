package me.roton.axiom.billing.kafka

import me.roton.axiom.billing.domain.outbox.OutboxEvent
import me.roton.axiom.billing.repository.OutboxRepository
import me.roton.axiom.common.time.Clock
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.context.bean.override.mockito.MockitoBean // <-- NEW IMPORT
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import java.time.Instant
import java.util.concurrent.CompletableFuture

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Testcontainers
class OutboxPollerTest {

    @Autowired
    lateinit var outboxRepository: OutboxRepository

    @Autowired
    lateinit var consumer: SubscriptionUpgradedConsumer

    // Replaces the real KafkaTemplate in the Spring Context
    @MockitoBean
    lateinit var kafkaTemplate: KafkaTemplate<String, String>

    @MockitoBean
    lateinit var clock: Clock

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

    private val fixedNow = Instant.parse("2026-07-14T10:00:00Z")

    @BeforeEach
    fun setUp() {
        // Clean up from previous tests
        outboxRepository.deleteAll()

        // Freeze time so we can accurately assert the publishedAt timestamp
        whenever(clock.now()).thenReturn(fixedNow)

        // Default happy path for KafkaTemplate
        whenever(kafkaTemplate.send(any<String>(), any<String>(), any<String>()))
            .thenReturn(CompletableFuture.completedFuture(null))
    }

    @Test
    fun `poller processes unpublished events and marks them as published`() {
        val event = outboxRepository.save(OutboxEvent(eventType = "InvoiceCreated", payload = "{}"))

        // Manually trigger the scheduled method
        consumer.poller()

        val updatedEvent = outboxRepository.findById(event.id!!).get()
        assertNotNull(updatedEvent.publishedAt, "publishedAt should be set")
        assertEquals(fixedNow, updatedEvent.publishedAt)

        // Verify it actually called Kafka with the right topic, key, and payload
        verify(kafkaTemplate).send(eq("invoice.created"), eq(event.id.toString()), eq("{}"))
    }

    @Test
    fun `poller ignores events that are already published`() {
        val pastEvent = OutboxEvent(eventType = "InvoiceCreated", payload = "{}").apply {
            publishedAt = Instant.now().minusSeconds(3600)
        }
        outboxRepository.save(pastEvent)

        consumer.poller()

        verifyNoInteractions(kafkaTemplate)
    }

    @Test
    fun `poller processes a maximum of 10 events per run`() {
        // Create 15 un-published events
        val events = (1..15).map { OutboxEvent(eventType = "InvoiceCreated", payload = "{}") }
        outboxRepository.saveAll(events)

        consumer.poller()

        // Should only process the first 10
        verify(kafkaTemplate, times(10)).send(any<String>(), any<String>(), any<String>())

        val remainingUnpublished = outboxRepository.findAll().count { it.publishedAt == null }
        assertEquals(5, remainingUnpublished)
    }

    @Test
    fun `poller continues processing remaining events if one fails to publish`() {
        val event1 = outboxRepository.save(OutboxEvent(eventType = "InvoiceCreated", payload = """{"message":"1"}"""))
        val event2 = outboxRepository.save(OutboxEvent(eventType = "InvoiceCreated", payload = """{"message":"2"}"""))

        // Simulate Kafka being down or rejecting ONLY the first message
        whenever(kafkaTemplate.send(any<String>(), eq(event1.id.toString()), any<String>()))
            .thenThrow(RuntimeException("Kafka network error"))

        consumer.poller()

        val updatedEvent1 = outboxRepository.findById(event1.id!!).get()
        val updatedEvent2 = outboxRepository.findById(event2.id!!).get()

        // Event 1 failed, so it should REMAIN unpublished to be retried later
        assertNull(updatedEvent1.publishedAt, "Failed event should not be marked as published")

        // Event 2 succeeded, so it should be marked as published
        assertNotNull(updatedEvent2.publishedAt, "Successful event should be marked as published")
    }
}