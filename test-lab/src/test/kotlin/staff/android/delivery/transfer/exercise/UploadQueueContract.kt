package staff.android.delivery.transfer.exercise

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import staff.android.delivery.transfer.*

fun verifyUploadQueue(factory: () -> UploadQueue) {
    val queue = factory()
    assertTrue(queue.snapshot().pending.isEmpty())
    queue.enqueue("visit-a", byteArrayOf(1))
    assertEquals(listOf("visit-a"), queue.snapshot().pending.map { it.id })
    // Complete the histories and assertions in the chapter's contract.
}
