package SimpleBlockchain.actor

import SimpleBlockchain.blockchain._
import akka.actor.{Actor, ActorLogging, Props}
import akka.cluster.pubsub.DistributedPubSubMediator.{Subscribe, SubscribeAck}

object Broker {
    sealed trait BrokerMessage

    case object GetTransactions extends BrokerMessage
    case object Clear extends BrokerMessage

    case class AddTransaction(transaction: Transaction) extends BrokerMessage
    case class TransactionMessage(transaction: Transaction) extends BrokerMessage
    case class DiffTransaction(transactions: List[Transaction]) extends BrokerMessage

    val props: Props = Props(new Broker)

}

class Broker extends Actor with ActorLogging {
    import Broker._

    var pending: List[Transaction] = List()

    override def receive: Receive = {
        case AddTransaction(transaction: Transaction) =>
            pending = transaction :: pending
            log.info(s"Added $transaction to pending Transactions")
        case GetTransactions =>
            log.info(s"Getting pending transactions")
            sender() ! pending
        case DiffTransaction(externalTransactions) =>
            pending = pending diff externalTransactions
        case Clear =>
            pending = List()
            log.info("Pending transactions list cleared!")
        case SubscribeAck(Subscribe("transaction", None, `self`)) =>
            log.info("subscribing")
    }
}

