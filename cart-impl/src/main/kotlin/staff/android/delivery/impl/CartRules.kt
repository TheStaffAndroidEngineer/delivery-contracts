package staff.android.delivery.impl

import staff.android.delivery.api.CartRejection

internal class CartRules(
    products: Set<String>,
    private val maximum: Int,
) {
    private val products = products.toSet()

    init {
        require(maximum > 0)
        require(this.products.all { it.isNotBlank() })
    }

    fun rejection(productId: String, desired: Long): CartRejection? =
        when {
            productId !in products -> CartRejection.UNKNOWN_PRODUCT
            desired < 0 -> CartRejection.INVALID_QUANTITY
            desired > maximum -> CartRejection.MAXIMUM_EXCEEDED
            else -> null
        }
}
