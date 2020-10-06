package media.state.handlers

import scala.concurrent.Future

import akka.{NotUsed, Done}
import akka.stream.scaladsl.{ Flow, Sink, Source }
import akka.stream.OverflowStrategy
import akka.http.scaladsl.model.ws.{ TextMessage, Message }
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.ws.WebSocketRequest
import akka.actor.typed.{ActorSystem, ActorRef}
import akka.stream.typed.scaladsl.ActorSource
import akka.stream.scaladsl.Keep
import akka.http.scaladsl.model.StatusCodes

object SocketClient {

	val req = WebSocketRequest(uri = "ws://localhost:8061/media.v1")

	val source: Source[Message, ActorRef[Message]] = 
		ActorSource.actorRef[Message](
			PartialFunction.empty,
			PartialFunction.empty,
			10,
			OverflowStrategy.fail
		)

	val sink: Sink[Message, NotUsed] = 
		Flow[Message]
			.map(msg => println(s"reply : $msg"))
			.to(Sink.ignore)

	def getFlow()(implicit system: ActorSystem[_]): (ActorRef[Message], Future[Done]) = {
		val flow = Http().webSocketClientFlow(req)
		val ((ws, upgrade), closed) = 
			source
				.viaMat(flow)(Keep.both)
				.toMat(sink)(Keep.both)
				.run()

		val connected = upgrade.flatMap { upg => 
			if(upg.response.status == StatusCodes.SwitchingProtocols)
				Future.successful(Done)
			else 
				throw new RuntimeException(s"Connection failed: ${upg.response.status}")
		}(system.executionContext)

		(ws, connected)
	}
}