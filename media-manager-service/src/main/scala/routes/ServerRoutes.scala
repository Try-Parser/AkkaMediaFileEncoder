package media.service.routes

import java.util.UUID
import scala.concurrent.duration._
import scala.util.{Failure, Success}
import akka.util.Timeout
import akka.actor.typed.ActorSystem
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import akka.http.scaladsl.model.StatusCodes // HttpResponse
import akka.cluster.sharding.typed.scaladsl.ClusterSharding
import media.service.handlers.FileActorHandler
import media.service.routes.RejectionHandlers
import media.fdk.json.MultiMedia
import media.state.media.MediaConverter

private[service] final class ServiceRoutes(system: ActorSystem[_]) extends SprayJsonSupport {

	// shards
	private val sharding = ClusterSharding(system)

	// Actor Timeout
	implicit private val timeout: Timeout = system
		.settings
		.config
		.getDuration("media-manager-service.routes.ask-timeout")
		.toMillis
		.millis

	lazy val maxSize = com.typesafe.config.ConfigFactory
		.load()
		.getLong("media-manager-service.max-content-size")

	//handler
	val fileActorHandler: FileActorHandler = FileActorHandler(sharding, system)

	val uploadFile: Route = path("upload") {
		post { 
			withSizeLimit(maxSize) {
				fileUpload("file") { case (meta, byteSource) => 
					onComplete(fileActorHandler.uploadFile(meta, byteSource)) { 
						case Success(multiMedia) => complete(multiMedia.toJson)
						case Failure(ex) => complete(StatusCodes.InternalServerError -> ex.toString) 
					}
		}}}
	}

	//todo
	val convertFile: Route = path("convert") {
		post {
			entity(as[MultiMedia]) { media =>
				onComplete(fileActorHandler.convertFile(media)) {
					case Success(mm) => complete(mm)
					case Failure(ex) => complete(StatusCodes.InternalServerError -> ex.toString)
				}
			}
	}}

	val mediaCodec: Route = path("codec") {
		get {
			complete(MediaConverter.getAvailableFormats().toJson)
		}
	}

	//test for play
	val convertStatus: Route =  path("status" / JavaUUID) { id =>
		get {
			import akka.http.scaladsl.model.{ ContentTypes, HttpEntity }
			complete(HttpEntity(
				ContentTypes.`text/html(UTF-8)`,
				"<audio controls> "+
					s"<source src='http://localhost:8061/play/$id' type='audio/mpeg'> "+
					"Your browser does not support the audio element. "+
				"</audio>"
			))
		}
	}

	//need revise for play
	val playFile: Route = path("play" / JavaUUID) { id =>
		get {
			complete("playFile")
			// onComplete(fileActorHandler.play(id)) {
			// 	case Success(Some(file)) => 
			// 		complete(HttpResponse(entity = FileHandler.getChunked(s"${file.fileName}.${file.ext}")))
			// 	case Success(None) => complete(s"Unable to find your file id: $id")
			// 	case Failure(e) => complete(e.toString)
			// }
		}
	}
	import JsonFormats._

	val getFile: Route = path("getFile" / JavaUUID) { id =>
		get {
			onSuccess(fileActorHandler.getFile(id)) { get =>
				complete(get)
			}
		}
	}
}
///example json format
import spray.json.DefaultJsonProtocol
import media.state.models.actors.FileActor
import spray.json.{DeserializationException, JsObject, JsString, JsValue, RootJsonFormat,JsNumber}
object JsonFormats extends DefaultJsonProtocol {
	implicit object Implicits extends RootJsonFormat[FileActor.FileJournal] {
		override def write(file: FileActor.FileJournal): JsValue = JsObject(
			"file_name" -> JsString(file.fileName),
			"file_path" -> JsString(file.fullPath),
			"contentType" -> JsString(file.contentType),
			"file_status" -> JsNumber(file.status),
			"file_id" -> JsString(file.fileId.toString)
		)

		override def read(json: JsValue): FileActor.FileJournal =
			json.asJsObject.getFields(
				"file_name",
				"file_path",
				"contentType",
				"file_status",
				"file_id") match {
				case Seq(JsString(fileName), JsString(fullPath), JsString(contentType), JsNumber(status), JsString(fileId)) =>
					FileActor.FileJournal(
						fileName,
						fullPath,
						contentType,
						status.toInt,
						UUID.fromString(fileId))
				case _ => throw DeserializationException("Invalid JSON Object")
			}
	}

	implicit val summaryFormat: RootJsonFormat[FileActor.Get] =
		jsonFormat2(FileActor.Get)
}

object ServiceRoutes extends RejectionHandlers {
	def apply(system: ActorSystem[_]): Route = {
		val route: ServiceRoutes = new ServiceRoutes(system)
		handleRejections(rejectionHandlers) {
			route.uploadFile ~ route.convertFile ~ route.convertStatus ~ route.playFile ~ route.mediaCodec ~ route.getFile
		}
	}
}