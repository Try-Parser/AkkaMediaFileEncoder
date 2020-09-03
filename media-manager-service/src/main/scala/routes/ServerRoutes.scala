package media.service.routes

import scala.concurrent.duration._
import scala.util.{Failure, Success}
import akka.util.Timeout
import akka.actor.typed.ActorSystem
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import akka.http.scaladsl.model.{HttpResponse, StatusCodes}
import akka.cluster.sharding.typed.scaladsl.ClusterSharding
import media.service.handlers.{FileActorHandler, FileHandler}
import routes.RejectionHandlers

private[service] final class ServiceRoutes(system: ActorSystem[_]) extends SprayJsonSupport with RejectionHandlers {

	// shards
	private val sharding = ClusterSharding(system)

	// Actor Timeout
	implicit private val timeout: Timeout = system
		.settings
		.config
		.getDuration("media-manager-service.routes.ask-timeout")
		.toMillis
		.millis

	//handler
	val fileActorHandler: FileActorHandler = FileActorHandler(sharding, system)

	val uploadFile: Route = handleRejections(rejectionHandlers) { path("upload") {
		post { 
			withSizeLimit(FileHandler.maxContentSize) { 
				fileUpload("file") { case (meta, byteSource) => 
					onComplete(
						fileActorHandler
						.writeFile(meta, byteSource)) {
							case Success(file) => complete(file.toJson)
							case Failure(ex) => complete(StatusCodes.InternalServerError -> ex.toString) 
			}}}
	}}}

	//todo
	val convertFile: Route = path("convert") {
		post {
			entity(as[media.service.entity.MediaConvert]) { media =>
				complete(media.toJson)
			}
			/* Commented convertion working please uncomment for test*/

			// import ws.schild.jave.MultimediaObject
			// import media.service.entity.Media

			// val ff = fileActorHandler.getFile(s"${file.fileName}.${file.ext}")
			// val mmObject: MultimediaObject = new MultimediaObject(ff)
			// val infos = mmObject.getInfo()

			// FileConverter.convert(
			// 	Mp3(),
			// 	mmObject,

			/* This path file to your directory please change before test */

			// new java.io.File(s"${FileActorHandler.basePath}/frank.mp3"))

			// println(infos)
			// println(mmObject)
			// complete("convertFile")
		}
	}

	//test for play
	val convertStatus: Route = handleRejections(rejectionHandlers) { path("status" / JavaUUID) { id =>
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
	}}

	//need revise for play
	val playFile: Route = handleRejections(rejectionHandlers) { path("play" / JavaUUID) { id =>
		get {
			onComplete(fileActorHandler.play(id)) {
				case Success(Some(file)) => 
					complete(HttpResponse(entity = FileHandler.getChunked(s"${file.fileName}.${file.ext}")))
				case Success(None) => complete(s"Unable to find your file id: $id")
				case Failure(e) => complete(e.toString)
			}
		}
	}
}}

object ServiceRoutes {
	def apply(system: ActorSystem[_]): Route = {
		val route: ServiceRoutes = new ServiceRoutes(system)
		route.uploadFile ~ route.convertFile ~ route.convertStatus ~ route.playFile	
		//~ route.testFunc
	}
}