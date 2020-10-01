package media.service.sinks

import scala.concurrent.ExecutionContext
import java.util.UUID

import akka.actor.typed.{ActorRef, Behavior}
import akka.actor.typed.scaladsl.Behaviors
import akka.util.ByteString

import media.service.handlers.FileActorHandler

trait FileSink

object FileSink extends FileSink {

	sealed trait Ack
	sealed trait Protocol

	case object Ack extends Ack
	case object Complete extends Protocol

	final case class Init(ackTo: ActorRef[Ack]) extends Protocol
	final case class Message(ackTo: ActorRef[Ack], msg: ByteString) extends Protocol
	final case class Failure(ex: Throwable) extends Protocol

	def messageAdapter = (rar: ActorRef[Ack], element: ByteString) => Message(rar, element)
	def onInitMessage = (rar: ActorRef[Ack]) => Init(rar)
	def onFailureMessage = (ex: Throwable) => Failure(ex)
	
	def apply(handler: FileActorHandler, name: String, regionId: UUID): Behavior[Protocol] = Behaviors.receiveMessage { 
		case Init(rar) =>
			println("Initalized")
			rar ! Ack
			Behaviors.same
		case Message(rar, msg) =>
			val s = msg.toArray
			println(s"Message size: ${s.size}")

			handler.transferFile(name, msg.toArray, regionId).map { _ =>
				rar ! Ack
			}(ExecutionContext.global)

			Behaviors.same
		case Failure(ex) =>
			println(ex)
			Behaviors.stopped
		case Complete =>
			println("Complete")
			Behaviors.stopped
	}
}