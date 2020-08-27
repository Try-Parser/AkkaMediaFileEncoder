package media.service.routes

// import java.time.Instant
// import java.util.UUID

import scala.concurrent.duration._
import scala.util.{ Success, Failure }

import akka.util.Timeout
import akka.actor.typed.ActorSystem

import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import akka.http.scaladsl.model.{ HttpResponse, StatusCodes }

import akka.cluster.sharding.typed.scaladsl.ClusterSharding

import media.service.handler.FileActorHandler


private[service] final class ServiceRoutes(system: ActorSystem[_]) extends SprayJsonSupport  {

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
			fileUpload("file") { case (meta, byteSource) => 
				onComplete(
					fileActorHandler
					.writeFile(meta, byteSource)) {
						case Success(file) => 
							import ws.schild.jave.MultimediaObject

							val ff = fileActorHandler.getFile(s"${file.fileName}.${file.ext}")
							val mmObject: MultimediaObject = new MultimediaObject(ff)
							val infos = mmObject.getInfo()

							println(infos)
							println(mmObject)

							complete(file.toJson)
						case Failure(ex) => complete(StatusCodes.InternalServerError -> ex.toString) 
			}}	
	}}

	//todo 
	val convertFile: Route = path("convert") {
		get {
			complete("convertFile")
		}
	}	

	//test for play
	val convertStatus: Route = path("status" / JavaUUID) { id =>
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
					complete(HttpResponse(entity = fileActorHandler.getChunked(s"${file.fileName}.${file.ext}")))
				case Success(None) => complete(s"Unable to find your file id: $id")
				case Failure(e) => complete(e.toString)
			}
		}
	}
}

object ServiceRoutes {
	def apply(system: ActorSystem[_]): Route = {
		val route: ServiceRoutes = new ServiceRoutes(system)
		route.uploadFile ~ route.convertFile ~ route.convertStatus ~ route.playFile	
		//~ route.testFunc
	}
}