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
import media.service.entity.FileMedia
import media.service.entity.MediaConvert
import media.service.handlers.FileConverter

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

	val uploadFile: Route = path("upload") {
		post { 
			withSizeLimit(FileHandler.maxContentSize) { 
				fileUpload("file") { case (meta, byteSource) => 
					onComplete(
						fileActorHandler
						.writeFile(meta, byteSource)) {
							case Success(file) => 
								val (vid, aud) = (file.mmi.getVideo(), file.mmi.getAudio())
								complete(FileMedia(
									FileConverter.getAvailableFormats(vid != null, aud != null), 
									file).toJson)
							case Failure(ex) => complete(StatusCodes.InternalServerError -> ex.toString) 
			}}}
	}}

	//todo
	val convertFile: Route = path("convert") {
		post {
			entity(as[MediaConvert]) { media =>
				println(FileConverter.startConvert(media))
				complete(media.toJson)
			}
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
			onComplete(fileActorHandler.play(id)) {
				case Success(Some(file)) => 
					complete(HttpResponse(entity = FileHandler.getChunked(s"${file.fileName}.${file.ext}")))
				case Success(None) => complete(s"Unable to find your file id: $id")
				case Failure(e) => complete(e.toString)
			}
		}
	}
}

object ServiceRoutes {
	def apply(system: ActorSystem[_]): Route = {
		val route: ServiceRoutes = new ServiceRoutes(system)
		val cRoute = route.uploadFile ~ route.convertFile ~ route.convertStatus ~ route.playFile	
		cRoute
	}
}