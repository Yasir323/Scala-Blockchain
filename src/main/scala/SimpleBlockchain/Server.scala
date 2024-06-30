package SimpleBlockchain

import SimpleBlockchain.actor.Node
import SimpleBlockchain.api.NodeRoutes
import SimpleBlockchain.cluster.ClusterManager
import akka.actor.{ActorRef, ActorSystem}
import akka.cluster.pubsub.DistributedPubSub
import akka.http.scaladsl.Http
import akka.http.scaladsl.server.Route
import akka.stream.Materializer
import akka.http.scaladsl.server.Directives._
import com.typesafe.config.{Config, ConfigFactory}

import scala.concurrent.Await
import scala.concurrent.duration.Duration

object Server extends App with NodeRoutes {

    val config: Config = ConfigFactory.load()

    val address = config.getString("http.ip")
    val port = config.getInt("http.port")
    val nodeId = config.getString("blockchain.node.id")

    implicit val system: ActorSystem = ActorSystem("SimpleBlockchain")
    implicit val materializer: Materializer = Materializer(system)

    val clusterManager: ActorRef = system.actorOf(ClusterManager.props(nodeId), "clusterManager")
    val mediator: ActorRef = DistributedPubSub(system).mediator
    val node: ActorRef = system.actorOf(Node.props(nodeId, mediator), "node")

    lazy val routes: Route = statusRoutes ~ transactionRoutes ~ mineRoutes

    Http().newServerAt(address, port).bind(routes)
    println(s"Server online at http://$address:$port/")
    Await.result(system.whenTerminated, Duration.Inf)

}
