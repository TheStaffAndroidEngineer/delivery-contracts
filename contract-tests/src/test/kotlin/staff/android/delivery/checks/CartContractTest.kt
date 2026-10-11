package staff.android.delivery.checks

import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.DynamicTest.dynamicTest
import org.junit.jupiter.api.TestFactory
import staff.android.delivery.api.*
import staff.android.delivery.impl.createCart
import staff.android.delivery.impl.createBrokenCart

fun realCart(): CartRepository = createCart(setOf("tea", "coffee"), 4, seed().lines)
fun selectedCart(): CartRepository =
    if (System.getProperty("cart.provider") == "broken")
        createBrokenCart(setOf("tea", "coffee"), 4, seed().lines)
    else realCart()

class CartContractTest {
    @TestFactory fun providers() = listOf(
        "real" to ::selectedCart,
        "fake" to { FakeCart() },
    ).flatMap { (provider, factory) ->
        listOf<Pair<String, suspend (() -> CartRepository) -> Unit>>(
            "preservesRejectedState" to ::preservesRejectedState,
            "rejectionDoesNotPublish" to ::rejectionDoesNotPublish,
            "noOpDoesNotAdvance" to ::noOpDoesNotAdvance,
            "removeAndReadd" to ::removeAndReadd,
            "invalidCommandsPreserve" to ::invalidCommandsPreserve,
            "overflowCannotBecomeAccepted" to ::overflowCannotBecomeAccepted,
            "distinctProductsStayDistinct" to ::distinctProductsStayDistinct,
            "factoriesAreIndependent" to ::factoriesAreIndependent,
        ).map { (name, check) -> dynamicTest("$provider/$name") {
            runTest { check(factory) }
        } }
    }
}
