package me.roton.axiom.subscription.repository

import me.roton.axiom.subscription.domain.plan.Plan
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface PlanRepository : JpaRepository<Plan, UUID> {
    fun findPlansByActive(active: Boolean = true): MutableList<Plan>
}