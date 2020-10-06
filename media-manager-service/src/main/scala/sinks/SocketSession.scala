package media.service.sinks

import java.util.UUID

// import akka.stream.scaladsl.Flow
import akka.actor.typed.Behavior
import akka.actor.typed.ActorRef
import akka.actor.typed.scaladsl.Behaviors
// import akka.http.scaladsl.model.ws.Message
// import akka.stream.typed.scaladsl.ActorSource
// import akka.stream.CompletionStrategy
// import akka.stream.scaladsl.Source
// import akka.actor.Status.Success
import akka.actor.typed.Terminated
// import akka.actor.typed.scaladsl.ActorContext
import spray.json._
import spray.json.DefaultJsonProtocol._

sealed trait Protocol
sealed trait Event 
sealed trait Response

object Publisher {
	sealed trait Protocol 

	final case class InOut(msg: String) extends Protocol
	final case class Register(id: UUID, ref: ActorRef[Protocol]) extends Protocol
	final case class SendTo(id: UUID, msg: String) extends Protocol

	def apply(): Behavior[Protocol] = run(Set.empty)

	private def run(consumers: Set[(UUID, ActorRef[Protocol])]): Behavior[Protocol] =
		Behaviors.receive[Protocol] { (ctx, proto) => 
			proto match {
				case Register(id, ref) => 
					ctx.watch(ref)
					ref ! Register(id, ref)
					run(consumers + (id -> ref))
				case msg: InOut =>
					consumers.foreach {
						case (id, reply) => reply ! msg
					}
					Behaviors.same
				case sendTo: SendTo =>
					consumers.foreach {
						case (id, reply) => 
							println(s"$id == ${sendTo.id}")
							if(sendTo.id.equals(id))
								reply ! sendTo
					}
					Behaviors.same
			} 
		}.receiveSignal {
			case (_, Terminated(consumer)) =>
				val consumerF = consumers.filterNot(_._2 == consumer)
				run(consumerF)
		}
}

object Consumer {
	sealed trait Event 

	final case class Outgoing(msg: String) extends Event
	final case class Incomming(msg: String) extends Event
	final case class Connected(id: UUID, out: ActorRef[Event]) extends Event

	final case class Command(id: UUID, msg: String)

	object Command {
	  implicit val uuidJsonFormat: JsonFormat[UUID] = new JsonFormat[UUID] {
	    override def write(x: UUID): JsValue = JsString(x.toString)
	    override def read(value: JsValue): UUID = value match {
	      case JsString(x) => UUID.fromString(x)
	      case x =>
	        throw new IllegalArgumentException("Expected UUID as JsString, but got " + x.getClass)
	    }
		}
	  implicit val comandFormat: RootJsonFormat[Command] = jsonFormat2(Command.apply)
	}

	case object Ack extends Event
	case object Disconnected extends Event

	def apply(pub: ActorRef[Publisher.Protocol]): Behavior[Event] = 
		Behaviors.setup { ctx => 
			Behaviors.receiveMessagePartial {
				case Connected(id, out) => 
					pub ! Publisher.Register(id, ctx.messageAdapter {
						case Publisher.InOut(msg) => Outgoing(msg)
						case Publisher.Register(id, _) => Outgoing(s"""{ id: "$id" }""")
						case Publisher.SendTo(id, msg) => Outgoing(s"""{ id: "$id", msg: "$msg" }""")
					})
					channel(pub, out)
			}
		}

	private def channel(
		pub: ActorRef[Publisher.Protocol], 
		out: ActorRef[Event]
	): Behavior[Event] = Behaviors.receiveMessagePartial {
		case Incomming(msg) =>
			try {
				val cmd = msg.parseJson.convertTo[Command]
				pub ! Publisher.SendTo(cmd.id, cmd.msg)
			} catch {
				case _: Throwable => out ! Outgoing(s""" { "error": "invalid command." } """)
			}
			Behaviors.same
		case msg: Outgoing =>
			out ! msg
			Behaviors.same
		case Disconnected =>
			// out ! Disconnected
			Behaviors.stopped
	}
}