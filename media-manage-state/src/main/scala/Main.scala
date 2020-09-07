package media

import java.util.concurrent.CountDownLatch

import akka.actor.AddressFromURIString
import akka.actor.typed.ActorSystem
import akka.cluster.typed.Cluster
import com.typesafe.config.ConfigFactory
import media.state.cassandra.CassandraDB.{createTables, startCassandraDatabase}

import scala.concurrent.{ExecutionContext, Future}
import scala.jdk.CollectionConverters.ListHasAsScala

object ServerState {
	def main(args: Array[String]): Unit = {
		startDb()
		startNode(args.headOption)
	}

	private def startDb(): Unit = {
		startCassandraDatabase()
		println("Started Cassandra, press Ctrl + C to kill")
		Future(new CountDownLatch(1))(ExecutionContext.global)
	}

	private def startNode(port: Option[String]): Unit = {
		(port match {
			case Some(p) => Seq(p.toInt)
			case None => (ConfigFactory.load()
				.getStringList("akka.cluster.seed-nodes")
				.asScala
				.flatMap { case AddressFromURIString(s) => s.port })
		}).foreach { nodePort =>
			val httpPort = ("80" + nodePort.toString.takeRight(2)).toInt

			val system = ActorSystem[Nothing](
				media.state.guards.StateGuardian(httpPort),
				"state",
				ConfigFactory.parseString(s"""
				akka.remote.artery.canonical.port = $nodePort
				shopping.http.port = $httpPort
			""").withFallback(ConfigFactory.load()))

			if(Cluster(system).selfMember.hasRole("read-model"))
				createTables(system)
		}
	}
}
