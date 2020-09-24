package media.service.routes

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
import media.state.models.actors.FileActor.{ Get, FileNotFound, FileJournal }

import spray.json.{JsValue, JsNumber, JsObject, JsString }


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
						case Failure(ex) => 
							complete(StatusCodes.InternalServerError -> ex.toString) 
					}
		}}}
	}

	//todo
	val convertFile: Route = path("convert") {
		post {
			entity(as[MultiMedia]) { media =>
				// fileActorHandler.convertFile(media)
				// complete(media.toJson)
				onComplete(fileActorHandler.convertFile(media)) {
					case Success(mm) => complete(mm)
					case Failure(ex) => 
						println(ex)
						complete(StatusCodes.InternalServerError -> ex.toString)
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

	val getFile: Route = path("file" / JavaUUID) { id =>
		get {
			onSuccess(fileActorHandler.getFile(id)) {
				case Get(journal, status) => complete(
					JsObject(
						"journal" -> JsonFormats.Implicits.write(journal).asJsObject,
						"status" -> JsString(status)
					))
				case FileNotFound => complete(
					JsObject(
						"id" -> JsString(id.toString),
						"reason" -> JsString("file not found.")
				))
			}
		}
	}
}

object JsonFormats extends  {
	implicit object Implicits {
		def write(file: FileJournal): JsValue = JsObject(
			"file_name" -> JsString(file.fileName),
			"file_path" -> JsString(file.fullPath),
			"contentType" -> JsString(file.contentType),
			"file_status" -> JsNumber(file.status),
			"file_id" -> JsString(file.fileId.toString)
		)
}}

object ServiceRoutes extends RejectionHandlers {
	def apply(system: ActorSystem[_]): Route = {
		val route: ServiceRoutes = new ServiceRoutes(system)
		handleRejections(rejectionHandlers) {
			route.uploadFile ~ route.convertFile ~ route.convertStatus ~ route.playFile ~ route.mediaCodec ~ route.getFile
		}
	}
}