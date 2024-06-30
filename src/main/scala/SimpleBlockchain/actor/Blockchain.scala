package SimpleBlockchain.actor

import SimpleBlockchain.blockchain._
import akka.actor.{ActorLogging, Props}
import akka.persistence._

object Blockchain {

    sealed trait BlockchainEvent
    case class AddBlockEvent(transactions: List[Transaction], proof: Long, timestamp: Long) extends BlockchainEvent


    sealed trait BlockchainMessage
    // case object IncrementDifficulty extends BlockchainMessage
    case class AddBlock(transactions: List[Transaction], proof: Long, timestamp: Long) extends BlockchainMessage
    case object GetChain extends BlockchainMessage
    case object GetLatestHash extends BlockchainMessage
    case object GetLatestIndex extends BlockchainMessage

    case class State(chain: Chain)

    def props(chain: Chain, nodeId: String): Props = Props(new Blockchain(chain, nodeId))

}

class Blockchain(chain: Chain, nodeId: String) extends PersistentActor with ActorLogging {

    import Blockchain._

    var state: State = State(chain)

    override  def persistenceId: String = s"chainer-$nodeId"

    def updateState(event: BlockchainEvent): Unit = event match {
        case AddBlockEvent(transactions, proof, timestamp) =>
            state = State(ChainLink(state.chain.index + 1, proof, transactions, timestamp = timestamp) :: state.chain)
            log.info(s"Added block ${state.chain.index} containing ${transactions.size} transactions")
    }

    override def receiveRecover: Receive = {
        case SnapshotOffer(metadata, snapshot: State) =>
            log.info(s"Recovering from snapshot ${metadata.sequenceNr} at block ${snapshot.chain.index}")
            state = snapshot
        case RecoveryCompleted => log.info("Recovery completed")
        case evt: AddBlockEvent => updateState(evt)
    }

    override def receiveCommand: Receive = {
        case SaveSnapshotSuccess(metadata) => log.info(s"Snapshot ${metadata.sequenceNr} saved successfully")
        case SaveSnapshotFailure(metadata, reason) => log.error(s"Error saving snapshit ${metadata.sequenceNr}: ${reason.getMessage}")
        case AddBlock(transactions: List[Transaction], proof: Long, timestamp: Long) =>
            persist(AddBlockEvent(transactions, proof, timestamp)) {event =>
                updateState(event)
            }
            deferAsync(Nil) { _ =>
                saveSnapshot(state)
                sender() ! state.chain.index
            }
        case AddBlock(_, _, _) => log.error("Invalid add block command")
        case GetChain => sender() ! state.chain
        case GetLatestHash => sender() ! state.chain.hash
        case GetLatestIndex => sender() ! state.chain.index

    }
}