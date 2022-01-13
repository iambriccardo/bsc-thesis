import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ObsoleteCoroutinesApi
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.channels.actor
import scala.Tuple2

/* STATE ACTOR */

data class State(
    val bestGlobalPosition: MutablePosition.BestPosition<Double>
)

fun State.mutateBestGlobalPosition(bestGlobalPosition: Tuple2<Position<Double>, Double>): State {
    return this.copy(
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

@OptIn(ObsoleteCoroutinesApi::class)
fun CoroutineScope.stateActor() = actor<StateMessage> {
    // We initialize the shared state as empty.
    var state = State(MutablePosition.BestPosition())

    for (msg in channel) {
        when (msg) {
            is MutateState -> {
                state = msg.mutator(state)
            }
            is GetState -> {
                msg.snapshot.complete(state)
            }
            else -> {}
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

/* AGGREGATOR */

sealed class AggregatorMessage
class Aggregate(val particle: Particle) : AggregatorMessage()
object StopAggregation : AggregatorMessage()

@OptIn(ObsoleteCoroutinesApi::class)
fun CoroutineScope.superRDDAggregator(size: Int, receiver: SendChannel<SuperRDD>) = actor<AggregatorMessage> {
    var aggregatedSize = 0
    val aggregatedParticles = Array<Particle?>(size) { null }

    fun reset() {
        aggregatedSize = 0
    }

    fun toSuperRDD(): SuperRDD {
        return SuperRDD(
            mutableListOf<Particle>().apply {
                aggregatedParticles.forEach { add(it!!) }
            })
    }

    for (msg in channel) {
        when (msg) {
            is Aggregate -> {
                aggregatedParticles[aggregatedSize] = msg.particle
                aggregatedSize++

                // If we have already aggregated at least size particles, we need
                // to send the
                if (aggregatedSize >= size) {
                    receiver.send(toSuperRDD())
                    reset()
                }
            }
            is StopAggregation -> {
                channel.close()
                reset()
            }
            else -> {}
        }
    }
}

suspend fun SendChannel<AggregatorMessage>.aggregate(particle: Particle) {
    this.send(Aggregate(particle))
}

suspend fun SendChannel<AggregatorMessage>.stopAggregation() {
    this.send(StopAggregation)
}