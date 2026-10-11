package staff.android.delivery.checks

import java.io.IOException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.junit.jupiter.api.Assertions.assertEquals
import staff.android.delivery.api.*

fun seed() = CartSnapshot(listOf(CartItem("tea", 3)), 0)

class ScriptedCart : CartRepository {
    private val current = MutableStateFlow(seed())
    override val state = current.asStateFlow()
    val requests = mutableListOf<Pair<String, Int>>()
    override suspend fun add(productId: String, amount: Int): CartEdit {
        requests += productId to amount
        val four = CartSnapshot(listOf(CartItem("tea", 4)), 1)
        current.value = four
        return if (requests.size == 1) CartEdit.Accepted(four)
        else CartEdit.Rejected(four, CartRejection.MAXIMUM_EXCEEDED,
            "Maximum quantity reached.")
    }
    override suspend fun setQuantity(productId: String, quantity: Int): CartEdit =
        error("Unexpected setQuantity")
    fun verifyRequests() = assertEquals(listOf("tea" to 1, "tea" to 1), requests)
}

// Independent behavioral fake, intended for serial tests. Same finite contracts run on it.
class FakeCart : CartRepository {
    private val current = MutableStateFlow(seed())
    override val state = current.asStateFlow()
    override suspend fun add(productId: String, amount: Int): CartEdit =
        if (amount <= 0) rejected(CartRejection.INVALID_QUANTITY)
        else edit(productId, current.value.quantityOf(productId).toLong() + amount)
    override suspend fun setQuantity(productId: String, quantity: Int): CartEdit =
        edit(productId, quantity.toLong())
    private fun rejected(reason: CartRejection): CartEdit.Rejected =
        CartEdit.Rejected(current.value, reason, when (reason) {
            CartRejection.MAXIMUM_EXCEEDED -> "Maximum quantity reached."
            CartRejection.INVALID_QUANTITY -> "Choose a nonnegative quantity."
            CartRejection.UNKNOWN_PRODUCT -> "Unknown product."
        })
    private fun edit(id: String, desired: Long): CartEdit {
        if (id !in setOf("tea", "coffee")) return rejected(CartRejection.UNKNOWN_PRODUCT)
        if (desired < 0) return rejected(CartRejection.INVALID_QUANTITY)
        if (desired > 4) return rejected(CartRejection.MAXIMUM_EXCEEDED)
        val before = current.value
        if (before.quantityOf(id) == desired.toInt()) return CartEdit.Accepted(before)
        val lines = before.lines.filterNot { it.productId == id }.toMutableList()
        if (desired > 0) lines.add(CartItem(id, desired.toInt()))
        val after = CartSnapshot(lines.sortedBy { it.productId }, before.revision + 1)
        current.value = after
        return CartEdit.Accepted(after)
    }
}

class GatedCart(private val delegate: CartRepository) : CartRepository by delegate {
    val entered = CompletableDeferred<Unit>()
    val release = CompletableDeferred<Unit>()
    var calls = 0
        private set
    var fail = false
    override suspend fun add(productId: String, amount: Int): CartEdit {
        calls++
        entered.complete(Unit)
        release.await()
        if (fail) throw IOException("Controlled unavailable provider")
        return delegate.add(productId, amount)
    }
}
