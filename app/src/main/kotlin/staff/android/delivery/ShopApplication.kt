package staff.android.delivery

import android.app.Application
import staff.android.delivery.api.CartItem
import staff.android.delivery.api.CartRepository
import staff.android.delivery.impl.createCart
import staff.android.delivery.impl.createBrokenCart

class ShopApplication : Application() {
    val cart: CartRepository by lazy {
        val create = if (BuildConfig.BROKEN_PROVIDER) ::createBrokenCart else ::createCart
        create(setOf("tea", "coffee"), 4, listOf(CartItem("tea", 3)))
    }
}
