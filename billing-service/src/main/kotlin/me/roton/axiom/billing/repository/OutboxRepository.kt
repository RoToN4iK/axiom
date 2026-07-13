package me.roton.axiom.billing.repository

import me.roton.axiom.billing.domain.outbox.OutboxEvent
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface OutboxRepository: JpaRepository<OutboxEvent, UUID> {
    fun findFirst10ByPublishedAtIsNullOrderByCreatedAtAsc(): List<OutboxEvent>
}