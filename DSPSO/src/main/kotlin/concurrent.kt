import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.channels.actor
import scala.Tuple2

data class State(
    val evaluationCounter: Int,
    val bestGlobalPosition: MutablePosition.BestPosition
)

fun State.incrementCounter(): State {
    return this.copy(evaluationCounter = this.evaluationCounter + 1)
}

fun State.mutateBestGlobalPosition(bestGlobalPosition: Tuple2<Position<Double>, Double>): State {
    return this.copy(
        evaluationCounter = this.evaluationCounter,
        bestGlobalPosition = this.bestGlobalPosition.apply {
            this@apply.mutate(bestGlobalPosition._1, bestGlobalPosition._2)
        }
    )
}

typealias StateMutator = (state: State) -> State
typealias StateSnapshot = CompletableDeferred<State>

sealed class StateMessage
class MutateState(val mutator: StateMutator) : StateMessage()
class GetState(val snapshot: StateSnapshot) : StateMessage()

fun CoroutineScope.stateActor() = actor<StateMessage> {
    // We initialize the shared state as empty.
    var state = State(0, MutablePosition.BestPosition())

    for (msg in channel) {
        when (msg) {
            is MutateState -> {
                state = msg.mutator(state)
            }
            is GetState -> msg.snapshot.complete(state)
        }
    }
}

suspend fun SendChannel<StateMessage>.mutate(mutator: StateMutator) {
    this.send(MutateState(mutator))
}

suspend fun SendChannel<StateMessage>.snapshot(): State {
    val snapshot = CompletableDeferred<State>()
    this.send(GetState(snapshot))

    return snapshot.await()
}