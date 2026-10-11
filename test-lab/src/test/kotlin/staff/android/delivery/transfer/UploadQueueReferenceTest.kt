package staff.android.delivery.transfer

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.DynamicTest.dynamicTest
import org.junit.jupiter.api.TestFactory
import staff.android.delivery.transfer.solution.*

val contractChecks = linkedMapOf<String, (() -> UploadQueue) -> Unit>(
    "validationAndIdentity" to ::validationAndIdentityContract,
    "enqueueAndDuplicate" to ::enqueueAndDuplicateContract,
    "lostReplyAndExactAck" to ::lostReplyAndExactAckContract,
    "repeatedAck" to ::repeatedAckContract,
    "inputOwnership" to ::inputOwnershipContract,
    "snapshotOwnership" to ::snapshotOwnershipContract,
    "factoryIsolation" to ::factoryIsolationContract,
)
val defectChecks = linkedMapOf(
    QueueFault.DROP_ON_NO_REPLY to "lostReplyAndExactAck",
    QueueFault.REMOVE_FIRST to "lostReplyAndExactAck",
    QueueFault.DUPLICATE_REVISION to "repeatedAck",
    QueueFault.INPUT_ALIAS to "inputOwnership",
    QueueFault.OUTPUT_ALIAS to "snapshotOwnership",
    QueueFault.SHARED_OWNER to "factoryIsolation",
    QueueFault.ACCEPT_CONFLICT to "enqueueAndDuplicate",
)

class UploadQueueReferenceTest {
    @TestFactory fun reference() = contractChecks.map { (name, check) ->
        dynamicTest(name) { check(queueFactory(QueueFault.NONE)) }
    }
    @TestFactory fun detectedDefects() = defectChecks.map { (fault, name) ->
        dynamicTest("$fault/$name") {
            val failed = assertThrows(AssertionError::class.java) {
                contractChecks.getValue(name)(queueFactory(fault))
            }
            println("DETECTED $fault by $name: ${failed.message}")
        }
    }
}

class UploadQueueExerciseTest {
    @TestFactory fun learner() = listOf(QueueFault.NONE, *defectChecks.keys.toTypedArray()).map { fault ->
        dynamicTest(fault.toString()) {
            val check: ((() -> UploadQueue) -> Unit) = { factory ->
                staff.android.delivery.transfer.exercise.verifyUploadQueue(factory)
            }
            if (fault == QueueFault.NONE) {
                assertDoesNotThrow { check(queueFactory(fault)) }
            } else {
                assertThrows(AssertionError::class.java, {
                    check(queueFactory(fault))
                }, "SURVIVED $fault: learner suite did not reject its changed behavior")
            }
        }
    }
}
