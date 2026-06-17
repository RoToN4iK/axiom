import me.roton.axiom.contracts.common.BillingCycle
import me.roton.axiom.contracts.common.money
import me.roton.axiom.contracts.plan.CreatePlanRequest
import me.roton.axiom.contracts.plan.createPlanRequest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.assertEquals

class ContractsSmokeTest {

    @Test
    fun `can build CreatePlanRequest with kotlin dsl`() {
        val request = createPlanRequest {
            name = "Pro"
            price = money {
                amountCents = 2900
                currency = "USD"
            }
            billingCycle = BillingCycle.MONTHLY
        }

        assertEquals("Pro", request.name)
        assertEquals(2900L, request.price.amountCents)
        assertEquals(BillingCycle.MONTHLY, request.billingCycle)
    }

    @Test
    fun `survives serialization round trip`() {
        val original = createPlanRequest {
            name = "Pro"
            price = money { amountCents = 2900; currency = "USD" }
            billingCycle = BillingCycle.MONTHLY
        }

        val bytes = original.toByteArray()
        val parsed = CreatePlanRequest.parseFrom(bytes)

        assertEquals(original, parsed)
    }
}