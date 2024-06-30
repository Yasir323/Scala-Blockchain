package SimpleBlockchain.proof

import SimpleBlockchain.crypto.Crypto
import spray.json.DefaultJsonProtocol.StringJsonFormat
import spray.json._

import scala.annotation.tailrec

object ProofOfWork {

    def proofOfWork(lastHash: String): Long = {
    @tailrec
    def powHelper(lastHash: String, proof: Long): Long = {
        if (isProofValid(lastHash, proof))
            proof
        else
            powHelper(lastHash, proof + 1)
    }

    val proof = 0
    powHelper(lastHash, proof)
  }

    def isProofValid(lastHash: String, proof: Long): Boolean = {
        val guess = (lastHash ++ proof.toString).toJson.toString
        val guessHash = Crypto.sha256Hash(guess)
        (guessHash take 4) == "0000"
    }

}
