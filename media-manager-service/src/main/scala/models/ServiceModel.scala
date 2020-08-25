package media.service.models

import utils.traits.{
	Command,
	Event
}

import akka.actor.typed.scaladsl.{ ActorContext, Behaviors }
import akka.actor.typed.{
	PostStop,
	Behavior,
	ActorRef,
	ActorSystem
}
import akka.cluster.sharding.typed.scaladsl.{
	ClusterSharding,
	Entity,
	EntityTypeKey
}

import spray.json.{
	DefaultJsonProtocol,
	DeserializationException,
	RootJsonFormat,
	JsObject,
	JsValue,
	JsArray,
	JsString
}

private[service] object ServiceEncoder {

	final case class AddRecord(data: Data, sender: ActorRef[Information]) extends Command
	final case class RemoveRecord(data: Data, sender: ActorRef[Event]) extends Command
	final case class GetAllData(sender: ActorRef[AllInfo]) extends Command

	final case class Data(value: Long)

	final case class Information(eid: String, data: Data) extends Event 
	final case class AllInfo(data: Vector[Information]) extends Event {
		def toJson: JsObject = AllInfo.Implicits.write(this).asJsObject
	}

	object AllInfo extends DefaultJsonProtocol{
		implicit object Implicits extends RootJsonFormat[AllInfo] {
			def write(allInfo: AllInfo) = {
				JsObject(
					"data" -> JsArray(
						allInfo.data.map {
							e => JsObject(
								"eid" -> JsString(e.eid), 
								"info" -> JsObject("data" -> JsString(e.data.value.toString)) 
							)}
					)
				)
			}

			def read(json: JsValue) = {
				json.asJsObject.getFields("data") match {
					case Seq(JsArray(data)) => 
						AllInfo(data.map { e => 
							val eObject = e.asJsObject.fields
							Information(
								eObject.get("eid").getOrElse("").toString, 
								Data(
									eObject
										.get("data")
										.map(
											_.asJsObject.fields.get("value")
											.map(_.toString)
											.getOrElse(0).toString)
										.getOrElse(0).asInstanceOf[Number].longValue
								)
							)
						})
					case _ => throw new DeserializationException("Invalid Json Object")
				}
			}
		}
	}

	def apply(eid: String): Behavior[Command] = Behaviors.setup { ctx => 
		ctx.log.info("Starting ServiceEncoder id : {}", eid)
		run(ctx, eid, Vector.empty)
	}

	val TKey: EntityTypeKey[Command] = 
		EntityTypeKey[Command]("ServiceModel")

	def initSharding(system: ActorSystem[_]): Unit = ClusterSharding(system)
		.init(Entity(TKey) { eCtx => ServiceEncoder(eCtx.entityId) })

	private def run(
		ctx: ActorContext[Command], 
		eid: String, 
		values: Vector[Information]
	): Behavior[Command] = Behaviors.receiveMessage[Command] {
		case AddRecord(data, sender) => 
			val update = values :+ Information(eid, data)
			ctx.log.debug("totval values : {}", update.size)
			sender ! Information(eid, data)
			run(ctx, eid, update)
		case GetAllData(sender) =>
			sender ! AllInfo(values)
			Behaviors.same
		case RemoveRecord(_, _) => 
			Behaviors.same
	}.receiveSignal {
		case (_, PostStop) =>
			ctx.log.info("Service Encoder cluster {} is stopping all data will be lost", eid)
			Behaviors.same
	}
}