import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlin.coroutines.*

fun main() = runBlocking {
    val channel = Channel()

    launch(Dispatchers.IO) {
        channel.send()
        println("Sent first on ${Thread.currentThread().name}")
        channel.send()
        println("Sent second on ${Thread.currentThread().name}")
    }

    launch(Dispatchers.IO) {
        channel.receive()
        println("Received first on ${Thread.currentThread().name}")
        channel.receive()
        println("Received second on ${Thread.currentThread().name}")
    }

    return@runBlocking
}

class Channel {
    private val size: Int = 1
    private var currentSize: Int = 0

    var sendIsSuspended: Boolean = false
    var sendContinuation: Continuation<Unit>? = null
    var receiveIsSuspended: Boolean = false
    var receiveContinuation: Continuation<Unit>? = null

    suspend fun send() {
        if (currentSize >= size) {
            sendIsSuspended = true
            suspendCoroutine<Unit> {
                sendContinuation = it
            }
            sendContinuation = null
            sendIsSuspended = false
        }

        currentSize++
        receiveContinuation?.resume(Unit)
    }

    suspend fun receive() {
        if (currentSize == 0) {
            receiveIsSuspended = true
            suspendCoroutine<Unit> {
                receiveContinuation = it
            }
            receiveContinuation = null
            receiveIsSuspended = false
        }

        currentSize--
        sendContinuation?.resume(Unit)
    }
}