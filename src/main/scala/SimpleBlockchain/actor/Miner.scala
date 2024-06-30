package SimpleBlockchain.actor

import SimpleBlockchain.exception.{InvalidProofException, MinerBusyException}
import SimpleBlockchain.proof.ProofOfWork
import akka.actor.Status.{Failure, Success}
import akka.actor.{Actor, ActorLogging, Props}

import scala.concurrent.Future

object Miner {

    sealed trait MinerMessage

    case object Ready extends MinerMessage

    case class Mine(hash: String) extends MinerMessage
    case class Validate(hash: String, proof: Long) extends MinerMessage

    val props: Props = Props(new Miner)

}

case class Miner() extends Actor with ActorLogging {

    import Miner._
    import context._

    def validate: Receive = {
        case Validate(hash, proof) =>
            log.info(s"Validating proof $proof")
            if (ProofOfWork.isProofValid(hash, proof)) {
                log.info("Proof is valid")
                sender() ! Success
            } else {
                log.info("Proof is not valid")
                sender() ! Failure(new InvalidProofException(hash, proof))
            }
    }

    def ready: Receive = validate orElse {
        case Mine(hash: String) =>
            log.info(s"Mining hash $hash...")
            val proof = Future {
                ProofOfWork.proofOfWork(hash)
            }
            sender() ! proof
            become(busy)
        case Ready =>
            log.info("Ready to mine!")
            sender() ! Success("OK")
    }

    def busy: Receive = validate orElse {
        case Mine(_) =>
            log.info("Already busy mining...")
            sender() ! Failure(new MinerBusyException("Miner is busy"))
        case Ready =>
            log.info("Ready to mine a new block")
            become(ready)
    }

    override def receive: Receive = {
        case Ready => become(ready)  // Set the Miner state to Ready
    }
}