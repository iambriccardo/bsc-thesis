import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlin.coroutines.*

fun main() = runBlocking {
    suspend {
        println("before suspend on ${Thread.currentThread().name}")
        // This function suspends the current coroutine until its continuation resume
        // method is invoked.
        val value = suspendCoroutine<Int> { continuation ->
            // We get access to the continuation of the parent coroutine.
            launch {
                delay(1000L)
                // We resume the parent coroutine from the suspending point.
                continuation.resume(10)
            }
        }
        // We get the value 10 which was passed in the resume method above.
        println("after suspend with value $value")
    }.startCoroutine(
        object : Continuation<Unit> {
            override val context: CoroutineContext = EmptyCoroutineContext

            // This method is called when the coroutine ends.
            override fun resumeWith(result: Result<Unit>) {
                result.onFailure { ex: Throwable -> throw ex }
            }
        }
    )
}