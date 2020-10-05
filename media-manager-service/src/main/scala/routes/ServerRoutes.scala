package media.service.routes

import scala.concurrent.duration._
import scala.concurrent.ExecutionContext
import scala.util.{
	Failure,
	Success
}
import java.util.UUID

import akka.util.Timeout
import akka.actor.typed.ActorSystem
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import akka.http.scaladsl.model.StatusCodes
import akka.cluster.sharding.typed.scaladsl.ClusterSharding
import akka.stream.typed.scaladsl.ActorSink
import media.service.handlers.FileActorHandler
import media.fdk.json.PreferenceSettings
import media.state.media.MediaConverter
import media.state.models.actors.FileActor.{
	Config,
	FileJournal,
	FileNotFound,
	Get
}
import media.state.models.actors.FileActorListModel
import spray.json.{
	JsArray,
	JsNumber,
	JsObject,
	JsString,
	JsValue
}

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
	val fileActorHandler: FileActorHandler = FileActorHandler(sharding)(timeout, system)
	

	import media.service.sinks.FileSink.{
		messageAdapter => Message,
		onInitMessage => Init,
		onFailureMessage => Failure,
		Protocol,
		Ack,
		Complete,
		apply => FileSink
	}

	val uploadFile: Route = path("upload") {
		post { 
			withSizeLimit(maxSize) {
					fileUpload("file") { case (meta, byteSource) => 

						val name = s"${UUID.randomUUID}-${java.time.Instant.now.getEpochSecond}.${Config.handler.getExt(meta.fileName)}"
						val regionId = UUID.randomUUID

						val sink = ActorSink.actorRefWithBackpressure(
							ref = system.systemActorOf[Protocol](FileSink(fileActorHandler, name, regionId), s"file-sink-${regionId}"),
							Message,
							Init,
							ackMessage = Ack,
							onCompleteMessage = Complete,
							Failure
						)

						val power =	byteSource
							.alsoTo(sink)
							.run()(akka.stream.Materializer(system.classicSystem))

						val extractPower = power.flatMap { _ =>
							fileActorHandler
								.uploadFile(name, meta.contentType.toString, regionId)
								.map(PreferenceSettings(_))(ExecutionContext.global)
						}(ExecutionContext.global)

						onSuccess(extractPower)(pref => complete(pref.toJson))
		}}}
	}

	val convertFile: Route = path("convert") {
		post {
			entity(as[PreferenceSettings]) { media =>
				onComplete(fileActorHandler.startConvert(media)) {
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

	val queryFiles: Route = pathPrefix("getFiles") {
		concat(
			pathEnd {
				concat(
					get {
						onSuccess(fileActorHandler.getFiles(None)) {
							case FileActorListModel.Get(journal) => {
								complete(JsObject(
									"files" -> JsonFormats.List.write(journal)
								))
							}
						}
					})
			},
			path(Segment) { key =>
				concat(
					get {
						onSuccess(fileActorHandler.getFiles(Some(key))) {
							case FileActorListModel.Get(journal) => {
								complete(JsObject(
									"files" -> JsonFormats.List.write(journal)
								))
							}
							case _ => complete(
								JsObject(
									"id" -> JsString(key),
									"reason" -> JsString("file not found.")
								))
						}
					})
			})
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
		)}

	implicit object List {
		def write(file: List[(FileJournal, String)]): JsArray = JsArray(
			file.map(res =>
				JsObject(
					"file_id" -> JsString(res._1.fileId.toString),
					"file_name" -> JsString(res._1.fileName),
					"file_type" -> JsString(res._1.fullPath.split("/").toList.last),
					"contentType" -> JsString(res._1.contentType),
					"file_status" -> JsString(res._2)
				)
			).toVector
		)
	}
}

object ServiceRoutes extends RejectionHandlers {
	def apply(system: ActorSystem[_]): Route = {
		val route: ServiceRoutes = new ServiceRoutes(system)
		handleRejections(rejectionHandlers) {
			route.uploadFile ~ route.convertFile ~ route.convertStatus ~ route.playFile ~ route.mediaCodec ~ route.getFile ~ route.queryFiles
		}
	}
}