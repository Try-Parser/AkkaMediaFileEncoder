package media

import java.util.concurrent.CountDownLatch

import akka.actor.AddressFromURIString
import akka.actor.typed.ActorSystem
import akka.cluster.typed.Cluster
import com.typesafe.config.ConfigFactory
import media.state.cassandra.CassandraDB

import scala.jdk.CollectionConverters.ListHasAsScala

object ServerState {
	def main(args: Array[String]): Unit = {
		args.headOption match {
			case Some("cassandra") => startDb
			case Some(port) => startNode(Option(port))
			case _ => startNode(None) 
		}
	}

	private def startDb(): Unit = {
		CassandraDB.startCassandraDatabase()
		println("Started Cassandra, press Ctrl + C to kill")
		new CountDownLatch(1).await
	}

	private def startNode(port: Option[String]): Unit = {
		val roles: List[String] = ConfigFactory.load()
			.getStringList("akka.cluster.roles")
			.asScala.toList

		(port match {
			case Some(p) => Seq(p.toInt)
			case None => (ConfigFactory.load()
				.getStringList("akka.cluster.seed-nodes")
				.asScala
				.flatMap { case AddressFromURIString(s) => s.port })
		}).view.zipWithIndex.foreach { case (nodePort, i) =>

			val system = ActorSystem[Nothing](
				media.state.guards.StateGuardian(),
				"state",
				ConfigFactory.parseString(s"""
				akka.cluster.roles.0 = ${roles(i)}
				akka.remote.artery.canonical.port = $nodePort
				"""
			).withFallback(ConfigFactory.load()))

			if(Cluster(system).selfMember.hasRole("read-model"))
				CassandraDB.createTables(system)
		}
	}
}
