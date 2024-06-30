package SimpleBlockchain.blockchain

import java.security.InvalidParameterException
import SimpleBlockchain.utils.JsonSupport._
import spray.json._
import SimpleBlockchain.crypto.Crypto


sealed trait Chain {

    val index: Int
    val hash: String
    val transactions: List[Transaction]
    val proof: Long
    val timestamp: Long

    def ::(link: Chain): Chain = link match {
        case l:ChainLink => ChainLink(l.index, l.proof, l.transactions, this.hash, l.timestamp, this)
        case _ => throw new InvalidParameterException("Cannot add invalid link to chain")
    }

}

object Chain {

    def apply[T](b: Chain*): Chain = {
        if (b.isEmpty) EmptyChain
        else {
            val link = b.head.asInstanceOf[ChainLink]
            ChainLink(link.index, link.proof, link.transactions, link.previousHash, link.timestamp, apply(b.tail: _*))
        }
    }

}

case class ChainLink(index: Int, proof: Long, transactions: List[Transaction], previousHash: String = "", timestamp: Long = System.currentTimeMillis(), tail: Chain = EmptyChain) extends Chain {
    val hash: String = Crypto.sha256Hash(this.toJson.toString)
}

case object EmptyChain extends Chain {
    val index = 0
    val hash = "1"
    val transactions: List[Transaction] = Nil
    val proof = 100L
    val timestamp = 0L
}