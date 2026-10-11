package staff.android.delivery.transfer

enum class DeliveryOutcome { ACKNOWLEDGED, NO_REPLY }
data class QueuedUpload(val id: String, val payload: ByteArray)
data class QueueSnapshot(val pending: List<QueuedUpload>, val revision: Long)
interface UploadQueue {
    fun enqueue(id: String, payload: ByteArray)
    fun finish(id: String, outcome: DeliveryOutcome): Boolean
    fun snapshot(): QueueSnapshot
}
