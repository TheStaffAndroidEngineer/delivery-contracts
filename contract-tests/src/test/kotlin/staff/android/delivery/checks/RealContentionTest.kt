package staff.android.delivery.checks

import java.util.concurrent.CyclicBarrier
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import staff.android.delivery.api.CartEdit

class RealContentionTest {
    @Test fun twoRealWorkersCannotBothTakeLastPlace() = runBlocking {
        Executors.newFixedThreadPool(2).asCoroutineDispatcher().use { workers ->
            repeat(50) {
                val cart = realCart()
                val ready = CyclicBarrier(2)
                val answers = List(2) { async(workers) {
                    ready.await(5, TimeUnit.SECONDS)
                    cart.add("tea", 1)
                } }.awaitAll()
                assertEquals(1, answers.count { it is CartEdit.Accepted })
                assertEquals(1, answers.count { it is CartEdit.Rejected })
                assertEquals(4, cart.state.value.quantityOf("tea"))
                assertEquals(1L, cart.state.value.revision)
            }
        }
    }
}
