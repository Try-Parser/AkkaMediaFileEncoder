package media.service.routes

import java.util.UUID

import java.nio.file.Paths
import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}

import akka.NotUsed
import akka.util.Timeout
import akka.actor.typed.{ActorSystem, SpawnProtocol, Props, ActorRef}
import akka.actor.typed.scaladsl.AskPattern._
import akka.cluster.sharding.typed.scaladsl.ClusterSharding
import akka.util.{ByteString, Timeout}
import akka.actor.typed.ActorSystem
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import akka.http.scaladsl.model.StatusCodes // HttpResponse
import akka.http.scaladsl.model.ws.{ TextMessage, Message }
import akka.http.scaladsl.model.{ContentType, HttpEntity, HttpResponse, MediaType, MediaTypes, StatusCodes}
import akka.cluster.sharding.typed.scaladsl.ClusterSharding
import akka.stream.IOResult
import akka.stream.scaladsl.{FileIO, Source}
import akka.stream.typed.scaladsl.ActorSink
import akka.stream.scaladsl.{ Flow, Sink, Source }
import akka.stream.{OverflowStrategy, Materializer}
import akka.stream.typed.scaladsl.ActorSource

import media.service.handlers.FileActorHandler
import media.fdk.json.PreferenceSettings
import media.state.media.MediaConverter
import media.state.models.actors.FileActor.{ Get, FileNotFound, FileJournal, Config }
import media.service.sinks.FileSink.{
	messageAdapter => OnMessage,
	onInitMessage => OnInit,
	onFailureMessage => OnFailure,
	Protocol,
	Ack,
	Complete,
	apply => FileSink
}
import media.service.sinks.{ Publisher, Consumer }

// import spray.json.{ JsValue, JsNumber, JsObject, JsString }
import spray.json._
import media.state.models.actors.FileActor.{Config, FileJournal, FileNotFound, Get, Play}
import spray.json.{JsNumber, JsObject, JsString, JsValue}

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

	val pub = system.systemActorOf[Publisher.Protocol](Publisher(), "publisher")
	val sc = system.systemActorOf[SpawnProtocol.Command](SpawnProtocol(), "spawn-control")

	implicit val scheduler = system.scheduler

	def futureConsumer: Future[ActorRef[Consumer.Event]] =
		sc.ask(SpawnProtocol.Spawn(Consumer(pub), s"consumer-${UUID.randomUUID}-${java.time.Instant.now.getEpochSecond}", Props.empty, _))

	def pubSub: Flow[Message, Message, Future[NotUsed]] =
		Flow.futureFlow(futureConsumer.map { consumerRef =>
			val inComming: Sink[Message, NotUsed] =
				Flow[Message].map {
					case TextMessage.Strict(msg) =>
						println("Strict message pass only")
						Consumer.Incomming(msg)
				}.to(
					ActorSink.actorRef[Consumer.Event](
						consumerRef,
						Consumer.Disconnected,
						_ => Consumer.Disconnected
					)
				)

			val outGoing: Source[Message, NotUsed] =
				ActorSource.actorRef[Consumer.Event](
					PartialFunction.empty,
					PartialFunction.empty,
					10,
					OverflowStrategy.fail
				).mapMaterializedValue { out =>
					println("Handshake established.")
					consumerRef ! Consumer.Connected(UUID.randomUUID, out)
					NotUsed
				}.map {
					case Consumer.Outgoing(msg) => TextMessage(msg)
				}

				Flow.fromSinkAndSourceCoupled(inComming, outGoing)
		}(ExecutionContext.global))

	val socket: Route = path("media.v1") {
		handleWebSocketMessages(pubSub)
	}

	val uploadFile: Route = path("upload") {
		post { 
			withSizeLimit(maxSize) {
					fileUpload("file") { case (meta, byteSource) => 

						val name = s"${UUID.randomUUID}-${java.time.Instant.now.getEpochSecond}.${Config.handler.getExt(meta.fileName)}"
						val regionId = UUID.randomUUID

						val sink = ActorSink.actorRefWithBackpressure(
							ref = system.systemActorOf[Protocol](FileSink(fileActorHandler, name, regionId), s"file-sink-${regionId}"),
							OnMessage,
							OnInit,
							ackMessage = Ack,
							onCompleteMessage = Complete,
							OnFailure
						)

						val power =	byteSource
							.alsoTo(sink)
							.run()(Materializer(system.classicSystem))

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
			onSuccess(fileActorHandler.playFile(id)) {
				case FileNotFound => complete(
					JsObject(
						"id" -> JsString(id.toString),
						"reason" -> JsString("file not found.")
					))
				case Play(fileUriString, contentTypeString) =>
					val dataSource: Source[ByteString, Future[IOResult]] = FileIO.fromPath(Paths.get(fileUriString))
					val mediaType = MediaType.parse(contentTypeString).toOption.getOrElse(MediaTypes.`audio/mpeg`)
					val contentType = ContentType(MediaType.customBinary(mediaType.mainType, mediaType.subType, mediaType.comp))
					complete(HttpResponse(StatusCodes.PartialContent, entity = HttpEntity(contentType, dataSource)))
			}
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
			route.socket ~ route.uploadFile ~ route.convertFile ~ route.convertStatus ~ route.playFile ~ route.mediaCodec ~ route.getFile
		}
	}
}