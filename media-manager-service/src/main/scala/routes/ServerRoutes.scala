package media.service.routes

import java.io.File
import java.time.Instant
import java.util.UUID
// import java.nio.file.Paths

import scala.concurrent.duration._
import scala.util.{ Success, Failure }

import akka.actor.typed.ActorSystem

import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
// import akka.http.scaladsl.model.StatusCodes

import akka.cluster.sharding.typed.scaladsl.ClusterSharding

import akka.util.Timeout

import media.service.handler.FileActorHandler

import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import akka.http.scaladsl.server.directives.FileInfo

// import akka.stream.scaladsl.FileIO
// import akka.stream.Materializer

import com.typesafe.config.ConfigFactory

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

	// implicit private val mat: Materializer = Materializer(system.classicSystem)

	//handler
	val fileActorHandler: media.service.handler.FileActorHandler = FileActorHandler(sharding)

	val configPath: String = ConfigFactory.load().getString("upload.path")
		// "/home/frank/Desktop/file"

	def tmpDst(file: FileInfo)(implicit tmpName: String): File = 
		File.createTempFile(tmpName, ".tmp", new File(configPath))

	//routes
	val uploadFile: Route = path("upload") {
		post {
			implicit val tmpName: String = s"${UUID.randomUUID}-${Instant.now.toEpochMilli}"
			storeUploadedFile("file", tmpDst) { 
				case (meta, stream) =>
					onComplete(fileActorHandler.uploadFile("", tmpName, configPath)) {
						case Success(file) => complete(file.toJson)
						case Failure(e) => complete(e.toString)
					}
				case _ => complete("Invalid upload.")
			}
		}
	}

	val convertFile: Route = path("convert") {
		get {
			complete("convertFile")
		}
	}	

	val convertStatus: Route = path("status") {
		get {
			complete("convertStatus")
		}
	}	

	val playFile: Route = path("play" / JavaUUID) { id =>
		get {
			onComplete(fileActorHandler.play(id)) {
				case Success(Some(file)) => complete(file.toJson)
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