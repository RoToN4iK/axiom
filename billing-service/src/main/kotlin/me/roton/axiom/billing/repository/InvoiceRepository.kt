package me.roton.axiom.billing.repository

import me.roton.axiom.billing.domain.Invoice
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import java.util.UUID

interface InvoiceRepository : JpaRepository<Invoice, UUID> {
    @Query("SELECT i FROM Invoice i JOIN FETCH i.lineItems WHERE i.id = :id")
    fun findByIdWithLineItems(id: UUID): Invoice?
}