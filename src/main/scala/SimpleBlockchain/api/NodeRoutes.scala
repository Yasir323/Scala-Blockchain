package SimpleBlockchain.api

import SimpleBlockchain.actor.Node._
import SimpleBlockchain.blockchain.Chain
import SimpleBlockchain.blockchain.Transaction

import SimpleBlockchain.utils.JsonSupport._
import akka.actor.{ActorRef, ActorSystem}
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Directives.pathPrefix
import akka.http.scaladsl.server.Route
import akka.http.scaladsl.server.Directives._
import akka.pattern.ask
import akka.util.Timeout

import scala.concurrent.Future
import scala.concurrent.duration.DurationInt

trait NodeRoutes extends SprayJsonSupport {

    implicit def system: ActorSystem

    def node: ActorRef

    implicit lazy val timeout: Timeout = Timeout(5.seconds)

    lazy val statusRoutes: Route = pathPrefix("status") {
        concat(
            pathEnd {
                concat(
                    get {
                        val statusFuture: Future[Chain] = (node ? GetStatus).mapTo[Chain]
                        onSuccess(statusFuture) { status =>
                            complete(StatusCodes.OK, status)
                        }
                    }
                )
            }
        )
    }

    lazy val transactionRoutes: Route = pathPrefix("transactions") {
        concat(
            pathEnd {
                concat(
                    get {
                        val eventualTransactions: Future[List[Transaction]] =
                            (node ? GetTransactions).mapTo[List[Transaction]]
                        onSuccess(eventualTransactions) { transactions =>
                            complete(transactions.toList)
                        }
                    },
                    post {
                        entity(as[Transaction]) { transaction =>
                            val transactionCreated: Future[Int] =
                                (node ? AddTransaction(transaction)).mapTo[Int]
                            onSuccess(transactionCreated) { done =>
                                complete((StatusCodes.Created, done.toString))
                            }
                        }
                    }
                )
            }
        )
    }

    lazy val mineRoutes: Route = pathPrefix("mine") {
        concat(
            pathEnd {
                concat(
                    get {
                        node ! Mine
                        complete(StatusCodes.OK)
                    }
                )
            }
        )
    }

}
