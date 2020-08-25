package media.service.models

import java.util.UUID

import akka.actor.typed.scaladsl.{ ActorContext, Behaviors }
import akka.actor.typed.{
	ActorRef,
	ActorSystem,
	PostStop,
	Behavior
}
import akka.cluster.sharding.typed.scaladsl.{
	EntityTypeKey
}

import spray.json.{
	DeserializationException,
	DefaultJsonProtocol,
	RootJsonFormat,
	JsObject,
	JsValue,
	JsString
}

import utils.traits.{ Command, Event }
import utils.traits._
import utils.actors._

private[service] class FileActor extends ShardActor[Command]("FileServiceActor") {
	import FileActor.{
		Upload,
		Convert,
		ConvertStatus,
		Play,
		FileUpload
	}

	protected def state(
		ctx: ActorContext[Command],
		aid: String,
		memory: Vector[FileUpload]
	): Behavior[Command] = Behaviors.receiveMessage[Command] {
		case Upload(fileName, ext, source, sender) =>
			val newFile = FileUpload(fileName, ext, source)
			val memoryUpdate = memory :+ newFile
			ctx.log.info("new file added id: {}", newFile.id)
			sender ! newFile
			state(ctx, aid, memoryUpdate)
		case Convert(_, _, sender) =>
			Behaviors.same
		case ConvertStatus(_, _, sender) => 
			Behaviors.same
		case Play(id, sender) => 
			memory.map(println)
			sender ! memory.filter(_.id.equals(id)).headOption 
			Behaviors.same
	}.receiveSignal {
		case (_, PostStop) =>
			ctx.log.info("FileActorService id: {} is gracefully stopping.", aid)
			Behaviors.same
	}
}

private[service] object FileActor extends Actor[FileActor] {
	//key
	val TKey: EntityTypeKey[Command] = EntityTypeKey[Command](actor.actorName)

	def apply(aid: String): Behavior[Command] = Behaviors.setup { ctx => 
		ctx.log.info("Starting FileActorService id: {}", aid)
		actor.state(ctx, aid, Vector.empty)
	}

	def init()(implicit sys: ActorSystem[_]): Unit = 
		actor.init(TKey) { eCtx => 
			sys.log.info("Creating identity {} id: {} ", actor.actorName, eCtx.entityId)
			FileActor(eCtx.entityId)
		}

	//cmd
	final case class Upload(
		fileName: String, 
		ext: String, 
		source: String,
		sender: ActorRef[FileUpload]) extends Command
	final case class Convert(
		id: UUID, 
		details: String,
		sender: ActorRef[_]) extends Command
	final case class ConvertStatus(
		id: UUID, 
		details: String,
		sender: ActorRef[_]) extends Command
	final case class Play(id: UUID, sender: ActorRef[Option[FileUpload]]) extends Command

	//evt
	final case class FileUpload(
		source: String, 
		fileName: String, 
		extension: String) extends Event {
		val id: UUID = UUID.randomUUID

		def toJson: JsObject = FileUpload.Implicits.write(this).asJsObject
	} 

	//comp spray
	object FileUpload extends DefaultJsonProtocol {
		implicit object Implicits extends RootJsonFormat[FileUpload] {
			def write(file: FileUpload) = {
				JsObject(
					"file_name" -> JsString(file.fileName),
					"extension" -> JsString(file.extension),
					"id" -> JsString(file.id.toString)
				)
			}

			def read(json: JsValue) = {
				json.asJsObject.getFields("source", "file_name", "xtn") match {
					case Seq(JsString(file_name), JsString(xtn)) =>
						FileUpload("", file_name, xtn)
					case _ => throw new DeserializationException("Invalid Json DeserializationException")
				}
			}
		}
	}
}
