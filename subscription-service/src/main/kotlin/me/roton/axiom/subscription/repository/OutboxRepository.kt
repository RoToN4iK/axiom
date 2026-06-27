package me.roton.axiom.subscription.repository

import me.roton.axiom.subscription.domain.outbox.OutboxEvent
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface OutboxRepository: JpaRepository<OutboxEvent, UUID> {
    fun findFirst10ByPublishedAtIsNullOrderByCreatedAtAsc(): List<OutboxEvent>

}