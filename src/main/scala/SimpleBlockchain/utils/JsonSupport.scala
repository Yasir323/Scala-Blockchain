package SimpleBlockchain.utils

import SimpleBlockchain.blockchain.{Chain, Transaction, ChainLink, EmptyChain}
import spray.json._

object JsonSupport extends DefaultJsonProtocol {

    implicit object TransactionJsonFormat extends RootJsonFormat[Transaction] {
        def write(t: Transaction): JsObject = JsObject(
            "sender" -> JsString(t.sender),
            "recipient" -> JsString(t.recipient),
            "amount" -> JsNumber(t.amount),
        )

        def read(value: JsValue): Transaction = {
            value.asJsObject.getFields("sender", "recipient", "amount") match {
                case Seq(JsString(sender), JsString(recipient), JsNumber(amount)) =>
                    Transaction(sender, recipient, amount.toLong)
                case _ => throw DeserializationException("Transaction expected")
            }
        }
    }

    implicit object ChainLinkJsonFormat extends RootJsonFormat[ChainLink] {
        override def read(json: JsValue): ChainLink = json.asJsObject.getFields("index", "proof", "transactions", "previousHash", "tail", "timestamp") match {
            case Seq(JsNumber(index), JsNumber(proof), values, JsString(previousHash), JsNumber(timestamp), tail) =>
                ChainLink(index.toInt, proof.toLong, values.convertTo[List[Transaction]], previousHash, tail = tail.convertTo(ChainJsonFormat), timestamp = timestamp.toLong)
            case _ => throw DeserializationException("Cannot deserialize: Chainlink expected")
        }

        override def write(obj: ChainLink): JsValue = JsObject(
            "index" -> JsNumber(obj.index),
            "proof" -> JsNumber(obj.proof),
            "transactions" -> JsArray(obj.transactions.map(_.toJson).toVector),
            "previousHash" -> JsString(obj.previousHash),
            "tail" -> ChainJsonFormat.write(obj.tail),
            "timestamp" -> JsNumber(obj.timestamp),
        )
    }

    implicit object ChainJsonFormat extends RootJsonFormat[Chain] {
        def write(obj: Chain): JsValue = obj match {
            case link: ChainLink => link.toJson
            case EmptyChain => JsObject(
                "index" -> JsNumber(EmptyChain.index),
                "hash" -> JsString(EmptyChain.hash),
                "transactions" -> JsArray(),
                "proof" -> JsNumber(EmptyChain.proof),
                "timeStamp" -> JsNumber(EmptyChain.timestamp)
            )
        }

        def read(json: JsValue): Chain = {
            json.asJsObject.getFields("previousHash") match {
                case Seq(_) => json.convertTo[ChainLink]
                case Seq() => EmptyChain
            }
        }
    }

}
