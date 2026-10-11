package staff.android.delivery.transfer

import java.util.Collections

enum class QueueFault {
    NONE, DROP_ON_NO_REPLY, REMOVE_FIRST, DUPLICATE_REVISION,
    INPUT_ALIAS, OUTPUT_ALIAS, SHARED_OWNER, ACCEPT_CONFLICT,
}

fun queueFactory(fault: QueueFault): () -> UploadQueue {
    val shared = FixtureQueue(QueueFault.NONE)
    return if (fault == QueueFault.SHARED_OWNER) { { shared } }
        else { { FixtureQueue(fault) } }
}

private class FixtureQueue(private val fault: QueueFault) : UploadQueue {
    private val rows = linkedMapOf<String, ByteArray>()
    private var revision = 0L
    override fun enqueue(id: String, payload: ByteArray) {
        require(id.isNotBlank())
        val old = rows[id]
        if (old != null) {
            if (!old.contentEquals(payload)) {
                if (fault == QueueFault.ACCEPT_CONFLICT) {
                    rows[id] = payload.copyOf(); revision++; return
                }
                throw IllegalArgumentException("An existing ID has different bytes")
            }
            return
        }
        rows[id] = if (fault == QueueFault.INPUT_ALIAS) payload else payload.copyOf()
        revision++
    }
    override fun finish(id: String, outcome: DeliveryOutcome): Boolean {
        require(id.isNotBlank())
        if (outcome == DeliveryOutcome.NO_REPLY) {
            if (fault == QueueFault.DROP_ON_NO_REPLY && rows.remove(id) != null) revision++
            return false
        }
        if (id !in rows) {
            if (fault == QueueFault.DUPLICATE_REVISION) revision++
            return false
        }
        val removed = if (fault == QueueFault.REMOVE_FIRST) rows.keys.first() else id
        rows.remove(removed)
        revision++
        return true
    }
    override fun snapshot(): QueueSnapshot = QueueSnapshot(
        Collections.unmodifiableList(rows.map { (id, bytes) ->
            QueuedUpload(id, if (fault == QueueFault.OUTPUT_ALIAS) bytes else bytes.copyOf())
        }), revision
    )
}
