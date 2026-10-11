package staff.android.delivery.checks

import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import staff.android.delivery.saved.SavedPresenter

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class SavedPresenterTest {
    @Test fun programmedRepliesCanHideProviderDefect() = runTest {
        val scripted = ScriptedCart()
        val screen = SavedPresenter(scripted)
        screen.addTea()
        assertEquals("Added. Cart has 4.", screen.status.value.message)
        screen.addTea()
        assertEquals("Maximum quantity reached.", screen.status.value.message)
        scripted.verifyRequests()
    }

    @Test fun featureAndRealProviderAgree() = runTest {
        val cart = selectedCart()
        val screen = SavedPresenter(cart)
        screen.addTea(); screen.addTea()
        assertEquals("Maximum quantity reached.", screen.status.value.message)
        assertEquals(4, cart.state.value.quantityOf("tea"))
        assertEquals(1L, cart.state.value.revision)
    }

    @Test fun pendingCommandExcludesDuplicateAndThenCompletes() = runTest {
        val cart = realCart()
        val gate = GatedCart(cart)
        val screen = SavedPresenter(gate)
        val first = launch { screen.addTea() }
        runCurrent()
        assertTrue(gate.entered.isCompleted)
        assertTrue(screen.status.value.busy)
        assertEquals(3, cart.state.value.quantityOf("tea"))
        screen.addTea()
        assertEquals(1, gate.calls)
        gate.release.complete(Unit)
        first.join()
        assertFalse(screen.status.value.busy)
        assertEquals("Added. Cart has 4.", screen.status.value.message)
        assertEquals(4, cart.state.value.quantityOf("tea"))
    }

    @Test fun failureClearsBusyAndPreservesAcceptedState() = runTest {
        val cart = realCart(); val gate = GatedCart(cart).apply { fail = true }
        val screen = SavedPresenter(gate)
        val work = launch { screen.addTea() }
        runCurrent()
        gate.release.complete(Unit); work.join()
        assertFalse(screen.status.value.busy)
        assertEquals("Could not add. Try again.", screen.status.value.message)
        assertEquals(seed(), cart.state.value)
        gate.fail = false
        screen.addTea()
        assertEquals(4, cart.state.value.quantityOf("tea"))
    }

    @Test fun cancellationIsPropagatedAndCleansBusy() = runTest {
        val cart = realCart(); val gate = GatedCart(cart)
        val screen = SavedPresenter(gate)
        var returnedNormally = false
        val work = async { screen.addTea(); returnedNormally = true }
        runCurrent(); work.cancelAndJoin()
        assertTrue(work.isCancelled)
        assertFalse(returnedNormally)
        assertFalse(screen.status.value.busy)
        assertEquals(seed(), cart.state.value)
    }
}
