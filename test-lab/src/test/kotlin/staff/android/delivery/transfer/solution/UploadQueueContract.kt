package staff.android.delivery.transfer.solution

import staff.android.delivery.transfer.*

import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue

fun verifyUploadQueue(factory: () -> UploadQueue) {
    validationAndIdentityContract(factory)
    enqueueAndDuplicateContract(factory)
    lostReplyAndExactAckContract(factory)
    repeatedAckContract(factory)
    inputOwnershipContract(factory)
    snapshotOwnershipContract(factory)
    factoryIsolationContract(factory)
}

fun enqueueAndDuplicateContract(factory: () -> UploadQueue) {
    val queue = factory()
    assertEquals(0L, queue.snapshot().revision)
    assertTrue(queue.snapshot().pending.isEmpty())
    queue.enqueue("visit-a", byteArrayOf(1, 2))
    queue.enqueue("visit-a", byteArrayOf(1, 2))
    val duplicate = queue.snapshot()
    assertEquals(listOf("visit-a"), duplicate.pending.map { it.id })
    assertArrayEquals(byteArrayOf(1, 2), duplicate.pending.single().payload)
    assertEquals(1L, duplicate.revision)
    assertThrows(IllegalArgumentException::class.java) {
        queue.enqueue("visit-a", byteArrayOf(9))
    }
    val afterConflict = queue.snapshot()
    assertEquals(listOf("visit-a"), afterConflict.pending.map { it.id })
    assertArrayEquals(byteArrayOf(1, 2), afterConflict.pending.single().payload)
    assertEquals(1L, afterConflict.revision)
    queue.enqueue("visit-b", byteArrayOf())
    assertEquals(listOf("visit-a", "visit-b"), queue.snapshot().pending.map { it.id })
    assertEquals(2L, queue.snapshot().revision)
}

fun lostReplyAndExactAckContract(factory: () -> UploadQueue) {
    val queue = factory()
    queue.enqueue("visit-a", byteArrayOf(1))
    queue.enqueue("visit-b", byteArrayOf(2))
    assertFalse(queue.finish("visit-b", DeliveryOutcome.NO_REPLY))
    assertFalse(queue.finish("unknown", DeliveryOutcome.ACKNOWLEDGED))
    assertFalse(queue.finish("unknown", DeliveryOutcome.NO_REPLY))
    val waiting = queue.snapshot()
    assertEquals(listOf("visit-a", "visit-b"), waiting.pending.map { it.id })
    assertArrayEquals(byteArrayOf(1), waiting.pending[0].payload)
    assertArrayEquals(byteArrayOf(2), waiting.pending[1].payload)
    assertEquals(2L, waiting.revision)
    assertTrue(queue.finish("visit-b", DeliveryOutcome.ACKNOWLEDGED))
    val remaining = queue.snapshot()
    assertEquals(listOf("visit-a"), remaining.pending.map { it.id })
    assertArrayEquals(byteArrayOf(1), remaining.pending.single().payload)
    assertEquals(3L, remaining.revision)
}

fun repeatedAckContract(factory: () -> UploadQueue) {
    val queue = factory()
    queue.enqueue("visit-a", byteArrayOf(1))
    assertTrue(queue.finish("visit-a", DeliveryOutcome.ACKNOWLEDGED))
    assertEquals(2L, queue.snapshot().revision)
    assertFalse(queue.finish("visit-a", DeliveryOutcome.ACKNOWLEDGED))
    assertFalse(queue.finish("visit-a", DeliveryOutcome.NO_REPLY))
    assertTrue(queue.snapshot().pending.isEmpty())
    assertEquals(2L, queue.snapshot().revision)
    queue.enqueue("visit-b", byteArrayOf(2))
    queue.enqueue("visit-a", byteArrayOf(3))
    val reused = queue.snapshot()
    assertEquals(listOf("visit-b", "visit-a"), reused.pending.map { it.id })
    assertArrayEquals(byteArrayOf(3), reused.pending[1].payload)
    assertEquals(4L, reused.revision)
}

fun inputOwnershipContract(factory: () -> UploadQueue) {
    val queue = factory()
    val source = byteArrayOf(1, 2)
    queue.enqueue("visit-a", source)
    source[0] = 9
    val accepted = queue.snapshot()
    assertArrayEquals(byteArrayOf(1, 2), accepted.pending.single().payload)
    assertEquals(1L, accepted.revision)
}

fun snapshotOwnershipContract(factory: () -> UploadQueue) {
    val queue = factory()
    queue.enqueue("visit-a", byteArrayOf(1, 2))
    val first = queue.snapshot()
    val second = queue.snapshot()
    first.pending.single().payload[0] = 9
    assertArrayEquals(byteArrayOf(1, 2), second.pending.single().payload)
    assertArrayEquals(byteArrayOf(1, 2), queue.snapshot().pending.single().payload)
    assertEquals(1L, queue.snapshot().revision)
    assertTrue(queue.finish("visit-a", DeliveryOutcome.ACKNOWLEDGED))
    assertEquals(listOf("visit-a"), second.pending.map { it.id })
    assertArrayEquals(byteArrayOf(1, 2), second.pending.single().payload)
    assertEquals(1L, second.revision)
}

fun factoryIsolationContract(factory: () -> UploadQueue) {
    val first = factory()
    val second = factory()
    first.enqueue("visit-a", byteArrayOf(1))
    assertTrue(second.snapshot().pending.isEmpty())
    assertEquals(0L, second.snapshot().revision)
    second.enqueue("visit-b", byteArrayOf(2))
    assertEquals(listOf("visit-a"), first.snapshot().pending.map { it.id })
    assertEquals(1L, first.snapshot().revision)
    assertEquals(listOf("visit-b"), second.snapshot().pending.map { it.id })
    assertEquals(1L, second.snapshot().revision)
}

fun validationAndIdentityContract(factory: () -> UploadQueue) {
    val queue = factory()
    queue.enqueue("visit-a", byteArrayOf(1))
    for (blank in listOf("", " ")) {
        assertThrows(IllegalArgumentException::class.java) {
            queue.enqueue(blank, byteArrayOf(2))
        }
        for (outcome in DeliveryOutcome.entries) {
            assertThrows(IllegalArgumentException::class.java) {
                queue.finish(blank, outcome)
            }
        }
    }
    val unchanged = queue.snapshot()
    assertEquals(listOf("visit-a"), unchanged.pending.map { it.id })
    assertArrayEquals(byteArrayOf(1), unchanged.pending.single().payload)
    assertEquals(1L, unchanged.revision)
    queue.enqueue(" visit-a ", byteArrayOf())
    queue.enqueue("VISIT-A", byteArrayOf(3))
    val distinct = queue.snapshot()
    assertEquals(listOf("visit-a", " visit-a ", "VISIT-A"), distinct.pending.map { it.id })
    assertArrayEquals(byteArrayOf(), distinct.pending[1].payload)
    assertArrayEquals(byteArrayOf(3), distinct.pending[2].payload)
    assertEquals(3L, distinct.revision)
}
