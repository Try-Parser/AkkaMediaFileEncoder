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

	val uploadFile: Route = handleRejections(rejectionHandlers) { { path("upload") {
		post { 
			withSizeLimit(50000000) { 
				fileUpload("file") { case (meta, byteSource) => 
					onComplete(fileActorHandler.uploadFile(meta, byteSource)) { 
						case Success(multiMedia) => //complete(multiMedia)
							complete(multiMedia.toJson) 
						case Failure(ex) => complete(StatusCodes.InternalServerError -> ex.toString) 
					}
		}}}
	}}}

	//todo
	val convertFile: Route = handleRejections(rejectionHandlers) { { path("convert") {
		// post {
		// 	entity(as[MediaConvert]) { media =>
		// 		complete("Convert File")
		// 		// println(FileConverter.startConvert(media))
		// 		// complete(media.toJson)
		// 	}
		// }
		get { 
			complete("convert File")
			// onComplete(fileActorHandler.test()) {
			// 	case Success(test) => complete("yes")
			// 	case Failure(ex) => complete("error")
			// }
		}
	}}}

	//test for play
	val convertStatus: Route = handleRejections(rejectionHandlers) { { path("status" / JavaUUID) { id =>
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
	}}}

	//need revise for play
	val playFile: Route = handleRejections(rejectionHandlers) { { path("play" / JavaUUID) { id =>
		get {
			complete("playFile")
			// onComplete(fileActorHandler.play(id)) {
			// 	case Success(Some(file)) => 
			// 		complete(HttpResponse(entity = FileHandler.getChunked(s"${file.fileName}.${file.ext}")))
			// 	case Success(None) => complete(s"Unable to find your file id: $id")
			// 	case Failure(e) => complete(e.toString)
			// }
		}
	}}
}}

object ServiceRoutes {
	def apply(system: ActorSystem[_]): Route = {
		val route: ServiceRoutes = new ServiceRoutes(system)
		route.uploadFile ~ route.convertFile ~ route.convertStatus ~ route.playFile
	}
}