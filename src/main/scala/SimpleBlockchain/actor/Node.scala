package SimpleBlockchain.actor

import SimpleBlockchain.actor.Broker.Clear
import SimpleBlockchain.actor.Miner.{Ready, Validate}
import SimpleBlockchain.blockchain.{EmptyChain, Transaction}
import akka.actor.{Actor, ActorLogging, ActorRef, Props}
import akka.cluster.pubsub.DistributedPubSubMediator.{Publish, Subscribe}
import akka.pattern.ask
import akka.util.Timeout

import scala.concurrent.duration.DurationInt
import scala.util.{Failure, Success}
import scala.concurrent.{ExecutionContext, Future}

object Node {

    sealed trait NodeMessage

    case class AddTransaction(transaction: Transaction) extends NodeMessage
    case class TransactionMessage(transaction: Transaction, nodeId: String) extends NodeMessage
    case class CheckProofOfWork(proof: Long) extends NodeMessage
    case class AddBlock(proof: Long) extends NodeMessage
    case class AddBlockMessage(proof: Long, tansactions: List[Transaction], timestamp: Long) extends NodeMessage

    case object GetTransactions extends NodeMessage
    case object Mine extends NodeMessage
    case object StopMining extends NodeMessage
    case object GetStatus extends NodeMessage
    case object GetLatestBlockIndex extends NodeMessage
    case object GetLatestBlockHash extends NodeMessage

    def props(nodeId: String, mediator: ActorRef): Props = Props(new Node(nodeId, mediator))

    def createCoinbaseTransaction(nodeId: String): Transaction = Transaction("coinbase", nodeId, 100)

}

class Node(nodeId: String, mediator: ActorRef) extends Actor with ActorLogging {

    import Node._

    implicit lazy val timeout: Timeout = Timeout(5.seconds)
    implicit val executor: ExecutionContext = ExecutionContext.global

    mediator ! Subscribe("newBlock", self)
    mediator ! Subscribe("transaction", self)

    val broker: ActorRef = context.actorOf(Broker.props)
    val miner: ActorRef = context.actorOf(Miner.props)
    val blockchain: ActorRef = context.actorOf(Blockchain.props(EmptyChain, nodeId))

    miner ! Ready


    override def receive: Receive = {
        case AddTransaction(transaction: Transaction)                                       => addTransaction(transaction)
        case TransactionMessage(transaction: Transaction, messageNodeId: String)            => addTransactionFromOtherNode(transaction, messageNodeId)
        case CheckProofOfWork(proof: Long)                                                  => checkProofOfWork(proof)
        case AddBlock(proof: Long)                                                          => addBlock(proof)
        case AddBlockMessage(proof: Long, transactions: List[Transaction], timestamp: Long) => addBlockFromOtherNode(proof, transactions, timestamp)
        case Mine                                                                           => mine()
        case GetTransactions                                                                => broker     forward Broker.GetTransactions
        case GetStatus                                                                      => blockchain forward Blockchain.GetChain  // Another syntax
        case GetLatestBlockIndex                                                            => blockchain forward Blockchain.GetLatestIndex
        case GetLatestBlockHash                                                             => blockchain forward Blockchain.GetLatestHash
    }

    def addTransaction(transaction: Transaction): Unit = {
        val node = sender()
        broker ! Broker.AddTransaction(transaction)
        (blockchain ? GetLatestBlockIndex).mapTo[Int] onComplete {
            case Success(index)     => node ! (index + 1)
            case Failure(exception) => node ! akka.actor.Status.Failure(exception)
        }
    }

    def addTransactionFromOtherNode(transaction: Transaction, messageNodeId: String): Unit = {
        log.info(s"Received transaction message from $messageNodeId")
        if (messageNodeId != nodeId) {
            broker ! Broker.AddTransaction(transaction)
        }
    }

    def checkProofOfWork(proof: Long): Unit = {
        val node = sender()
        (blockchain ? GetLatestBlockHash).mapTo[String] onComplete {
            case Success(hash: String)  => miner.tell(Validate(hash, proof), node)  // We send the address of the sender so that the miner can respond directly to it
            case Failure(exception)     => node ! akka.actor.Status.Failure(exception)
        }
    }

    def addBlock(proof: Long): Unit = {
        val node = sender()
        (self ? CheckProofOfWork(proof)) onComplete {
            case Success(_) =>
                (broker ? Broker.GetTransactions).mapTo[List[Transaction]] onComplete {
                    case Success(transactions: List[Transaction]) => blockchain.tell(Blockchain.AddBlock(transactions, proof, timestamp = System.currentTimeMillis()), node)
                    case Failure(exception)                       => node ! akka.actor.Status.Failure(exception)
                }
                broker ! Clear
            case Failure(exception) => node ! akka.actor.Status.Failure(exception)
        }
    }

    def addBlockFromOtherNode(proof: Long, transactions: List[Transaction], timestamp: Long): Unit = {
        val node = sender()
        (self ? CheckProofOfWork(proof)) onComplete {
        case Failure(e) => node ! akka.actor.Status.Failure(e)
        case Success(_) =>
            broker ! Broker.DiffTransaction(transactions)
            blockchain.tell(Blockchain.AddBlock(transactions, proof, timestamp), node)
            miner ! Ready
        }
    }

    def mine(): Unit = {
        val node = sender()
        (blockchain ? Blockchain.GetLatestHash).mapTo[String] onComplete {
            case Success(hash: String) => (miner ? Miner.Mine(hash)).mapTo[Future[Long]] onComplete {
                case Success(eventualProof: Future[Long])   => waitForProof(eventualProof)
                case Failure(exception)     => node ! akka.actor.Status.Failure(exception)
            }
            case Failure(exception) => node ! akka.actor.Status.Failure(exception)
        }
    }

    def waitForProof(eventualProof: Future[Long]): Unit = {
        eventualProof onComplete {
            case Success(proof: Long) =>
                val node = sender()
                val ts = System.currentTimeMillis()
                broker ! Broker.AddTransaction(createCoinbaseTransaction(nodeId))
                (broker ? Broker.GetTransactions).mapTo[List[Transaction]] onComplete {
                    case Success(transactions) => mediator ! Publish("newBlock", AddBlockMessage(proof, transactions, ts))
                    case Failure(e) => node ! akka.actor.Status.Failure(e)
                }
            case Failure(exception) => log.error(s"Error finding PoW solution: ${exception.getMessage}")
        }
    }
}