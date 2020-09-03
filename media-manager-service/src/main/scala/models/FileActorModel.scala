package media.service.models

import java.util.UUID

import akka.actor.typed.{
	ActorRef,
	ActorSystem,
	PostStop,
	Behavior
}

import akka.actor.typed.scaladsl.{ ActorContext, Behaviors }

import utils.traits.{ Command, Event }
import utils.traits._
import utils.actors._

import media.service.handlers.FileHandler.ContentTypeData

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
		case Upload(fileName, ext, contentType, sender) =>
			val newFile = FileUpload(fileName, ext, contentType)
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
	import akka.cluster.sharding.typed.scaladsl.EntityTypeKey
	import akka.http.scaladsl.model.ContentType

	import spray.json.{
		DeserializationException,
		DefaultJsonProtocol,
		RootJsonFormat,
		JsObject,
		JsValue,
		JsString,
		JsArray
	}

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
		contentType: ContentTypeData,
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

	final case class FileUpload(
		fileName: String, 
		ext: String,
		contentType: ContentTypeData,
		id: UUID = UUID.randomUUID) extends Event {

		def toJson: JsObject = FileUpload.Implicits.write(this).asJsObject
	} 

	//comp spray
	object FileUpload extends DefaultJsonProtocol {
		implicit object Implicits extends RootJsonFormat[FileUpload] {
			def write(file: FileUpload) = {
				JsObject(
					"file_name" -> JsString(file.fileName),
					"content_type" -> JsString(file.contentType.content.toString),
					"extension" -> JsString(file.ext),
					"id" -> JsString(file.id.toString)
				)
			}

			def read(json: JsValue) = {
				json.asJsObject.getFields("file_name", "content_type", "extension") match {
					case Seq(JsString(file_name), 
						JsString(content_type), 
						JsString(extension)) => ContentType.parse(content_type) match {
							case Right(contentType) => 	FileUpload(
								file_name, 
								extension,
								ContentTypeData(content_type))
							case Left(errors) => throw new DeserializationException(
								JsArray(errors.map { error =>
									JsObject(
										"summary" -> JsString(error.summary), 
										"detail" -> JsString(error.detail),
										"header" -> JsString(error.errorHeaderName))
							}.toVector).toString)}
					case _ => throw new DeserializationException("Invalid Json DeserializationException")
				}
			}
		}
	}
}
