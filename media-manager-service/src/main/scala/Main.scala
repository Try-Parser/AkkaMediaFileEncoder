package media

import scala.jdk.CollectionConverters._
import akka.actor.AddressFromURIString
import akka.actor.typed.ActorSystem

import com.typesafe.config.ConfigFactory

object ServerService {
	def main(args: Array[String]): Unit = {
		startNode(args.headOption)
	}

	private def startNode(port: Option[String]): Unit = (port match {
		case Some(p) => Seq(p.toInt)
		case None => ConfigFactory.load()
				.getStringList("media-manager-service.node.port")
				.asScala.toList.map(_.toInt)
	}).foreach { nodePort =>
		val httpPort = ("80" + nodePort.toString.takeRight(2)).toInt

		println(s"- Akka Http Port : ${httpPort + 10}")
		println(s"- Node Port : $nodePort")

		ActorSystem[Nothing](
			media.service.guards.ServiceGuardian(httpPort), 
			"media-service", 
			ConfigFactory.parseString(s"""
				akka.remote.artery.canonical.port = $nodePort
				media-manager-service.http.port = $httpPort
			""").withFallback(ConfigFactory.load()))
	}
}	