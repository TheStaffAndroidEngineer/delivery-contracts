package staff.android.delivery.impl

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import staff.android.delivery.api.CartEdit
import staff.android.delivery.api.CartItem
import staff.android.delivery.api.CartRejection
import staff.android.delivery.api.CartRepository
import staff.android.delivery.api.CartSnapshot

fun createBrokenCart(
    products: Set<String>,
    maximumPerProduct: Int,
    initial: List<CartItem>,
): CartRepository =
    PublishBeforeValidateCart(CartRules(products, maximumPerProduct), initial)

internal class PublishBeforeValidateCart(
    private val rules: CartRules,
    initial: List<CartItem>,
) : CartRepository {
    private val mutex = Mutex()
    private val current =
        MutableStateFlow(
            CartSnapshot(initial.sortedBy { it.productId }, 0)
        )
    override val state = current.asStateFlow()

    init {
        require(
            current.value.lines.all {
                rules.rejection(it.productId, it.quantity.toLong()) ==
                    null
            }
        )
    }

    override suspend fun add(productId: String, amount: Int): CartEdit =
        mutex.withLock {
            if (amount <= 0) {
                CartEdit.Rejected(
                    current.value,
                    CartRejection.INVALID_QUANTITY,
                    rejectionMessage(CartRejection.INVALID_QUANTITY),
                )
            } else {
                val desired =
                    current.value.quantityOf(productId).toLong() +
                        amount.toLong()
                commit(productId, desired)
            }
        }

    override suspend fun setQuantity(
        productId: String,
        quantity: Int,
    ): CartEdit = mutex.withLock {
        commit(productId, quantity.toLong())
    }

    private fun commit(productId: String, desired: Long): CartEdit {
        val before = current.value
        val rejection = rules.rejection(productId, desired)
        // Deliberate defect, only for a known positive representable quantity.
        if (rejection != null && rejection != CartRejection.MAXIMUM_EXCEEDED)
            return CartEdit.Rejected(before, rejection, rejectionMessage(rejection))
        if (desired > Int.MAX_VALUE)
            return CartEdit.Rejected(before, CartRejection.MAXIMUM_EXCEEDED,
                rejectionMessage(CartRejection.MAXIMUM_EXCEEDED))
        if (before.quantityOf(productId) == desired.toInt())
            return CartEdit.Accepted(before)
        val next =
            before.lines
                .filterNot { it.productId == productId }
                .toMutableList()
        if (desired > 0) next += CartItem(productId, desired.toInt())
        val after =
            CartSnapshot(
                next.sortedBy { it.productId },
                Math.addExact(before.revision, 1),
            )
        current.value = after
        if (rejection != null)
            return CartEdit.Rejected(after, rejection, rejectionMessage(rejection))
        return CartEdit.Accepted(after)
    }
}

private fun rejectionMessage(reason: CartRejection): String =
    when (reason) {
        CartRejection.UNKNOWN_PRODUCT -> "Unknown product."
        CartRejection.INVALID_QUANTITY -> "Choose a nonnegative quantity."
        CartRejection.MAXIMUM_EXCEEDED -> "Maximum quantity reached."
    }
