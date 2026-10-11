package staff.android.delivery.checks

import org.junit.jupiter.api.Assertions.*
import staff.android.delivery.api.*

suspend fun preservesRejectedState(factory: () -> CartRepository) {
    val cart = factory()
    val initial = cart.state.value
    val accepted = assertInstanceOf(
        CartEdit.Accepted::class.java, cart.add("tea", 1)
    )
    assertEquals(4, accepted.snapshot.quantityOf("tea"))
    assertEquals(1L, accepted.snapshot.revision)
    val rejected = assertInstanceOf(
        CartEdit.Rejected::class.java, cart.add("tea", 1)
    )
    assertEquals(CartRejection.MAXIMUM_EXCEEDED, rejected.reason)
    assertEquals(accepted.snapshot, rejected.snapshot)
    assertEquals(accepted.snapshot, cart.state.value)
    assertEquals(3, initial.quantityOf("tea"))
    assertEquals(0L, initial.revision)
}


suspend fun rejectionDoesNotPublish(factory: () -> CartRepository) {
    val cart = factory()
    val before = cart.state.value
    val rejected = assertInstanceOf(CartEdit.Rejected::class.java, cart.add("tea", 2))
    assertEquals(before, rejected.snapshot)
    assertEquals(before, cart.state.value)
}

suspend fun noOpDoesNotAdvance(factory: () -> CartRepository) {
    val cart = factory()
    val before = cart.state.value
    val answer = assertInstanceOf(CartEdit.Accepted::class.java, cart.setQuantity("tea", 3))
    assertEquals(before, answer.snapshot)
    assertEquals(before, cart.state.value)
}

suspend fun removeAndReadd(factory: () -> CartRepository) {
    val cart = factory()
    cart.setQuantity("tea", 0)
    assertTrue(cart.state.value.lines.isEmpty())
    assertEquals(1L, cart.state.value.revision)
    cart.add("tea", 2)
    assertEquals(2, cart.state.value.quantityOf("tea"))
    assertEquals(2L, cart.state.value.revision)
}

suspend fun invalidCommandsPreserve(factory: () -> CartRepository) {
    val cart = factory()
    val before = cart.state.value
    for (amount in listOf(0, -1, Int.MIN_VALUE)) {
        assertEquals(CartRejection.INVALID_QUANTITY,
            assertInstanceOf(CartEdit.Rejected::class.java, cart.add("tea", amount)).reason)
        assertEquals(before, cart.state.value)
    }
    assertEquals(CartRejection.UNKNOWN_PRODUCT,
        assertInstanceOf(CartEdit.Rejected::class.java, cart.add("other", 1)).reason)
    assertEquals(before, cart.state.value)
    assertInstanceOf(CartEdit.Rejected::class.java, cart.setQuantity("tea", -1))
    assertEquals(before, cart.state.value)
}

suspend fun overflowCannotBecomeAccepted(factory: () -> CartRepository) {
    val cart = factory()
    val before = cart.state.value
    assertInstanceOf(CartEdit.Rejected::class.java, cart.add("tea", Int.MAX_VALUE))
    assertEquals(before, cart.state.value)
}

suspend fun distinctProductsStayDistinct(factory: () -> CartRepository) {
    val cart = factory()
    val old = cart.state.value
    cart.add("coffee", 2)
    assertEquals(listOf(CartItem("coffee", 2), CartItem("tea", 3)), cart.state.value.lines)
    assertEquals(listOf(CartItem("tea", 3)), old.lines)
    assertEquals(0L, old.revision)
}

suspend fun factoriesAreIndependent(factory: () -> CartRepository) {
    val first = factory(); val second = factory()
    first.add("tea", 1)
    assertEquals(seed(), second.state.value)
    assertEquals(4, first.state.value.quantityOf("tea"))
}
