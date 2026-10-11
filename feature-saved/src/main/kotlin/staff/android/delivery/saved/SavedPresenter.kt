package staff.android.delivery.saved

import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import staff.android.delivery.api.CartEdit
import staff.android.delivery.api.CartRepository

data class SavedStatus(val busy: Boolean = false, val message: String = "Ready")

class SavedPresenter(private val cart: CartRepository) {
    private val current = MutableStateFlow(SavedStatus())
    val status = current.asStateFlow()

    // This screen owner is confined to one UI dispatcher.
    suspend fun addTea() {
        if (current.value.busy) return
        current.value = SavedStatus(busy = true, message = "Adding…")
        try {
            current.value = when (val result = cart.add("tea", 1)) {
                is CartEdit.Accepted -> SavedStatus(message =
                    "Added. Cart has ${result.snapshot.quantityOf("tea")}.")
                is CartEdit.Rejected -> SavedStatus(message = result.message)
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failed: IOException) {
            current.value = SavedStatus(message = "Could not add. Try again.")
        } finally {
            current.value = current.value.copy(busy = false)
        }
    }
}
