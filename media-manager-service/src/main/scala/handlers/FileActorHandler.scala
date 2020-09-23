package media.service.handlers

import java.util.UUID

import utils.concurrent.SysLog
import akka.util.{ByteString, Timeout}
import akka.stream.scaladsl.Source
import akka.http.scaladsl.server.directives.FileInfo
import akka.cluster.sharding.typed.scaladsl.{ClusterSharding, Entity}
import akka.actor.typed.ActorSystem
import media.state.models.actors.FileActor.{AddFile, ConvertFile, File, FileProgress, Get, GetFile, MediaDescription, TypeKey, createBehavior => CreateBehavior}
import media.state.events.EventProcessorSettings
import media.fdk.json.MultiMedia
import spray.json.{JsNumber, JsObject, JsString}

import scala.concurrent.Future

private[service] class FileActorHandler(shards: ClusterSharding, sys: ActorSystem[_])
	(implicit timeout: Timeout) extends SysLog(sys.log) {

	implicit private val sett = EventProcessorSettings(sys)
	implicit private val system = sys

	val regionId = com.typesafe.config.ConfigFactory
		.load()
		.getString("media-manager-service.region.id")
	
	shards.init(Entity(TypeKey)(CreateBehavior))

	def uploadFile(meta: FileInfo, byteSource: Source[ByteString, _]): Future[MultiMedia] =  {
		val id = UUID.randomUUID()
		byteSource.runFold(ByteString.empty)(_ ++ _).flatMap { byteS => 
			shards.entityRefFor(TypeKey, id.toString)
				.ask(AddFile(File(
					meta.fileName, 
					byteS.toArray, 
					meta.contentType.toString, 
					0,
					id
				), _)).map(extractMedia)(sys.executionContext)
		}(sys.executionContext)
	}

	def getFile(fileId: UUID): Future[Get] = {
		shards.entityRefFor(TypeKey, fileId.toString)
			.ask(GetFile(_))
	}

	def convertFile(mm: MultiMedia): Future[JsObject] = 
		shards.entityRefFor(TypeKey, regionId)
			.ask(ConvertFile(mm, _)).map { case FileProgress(fileName, id, progress) => 
				JsObject(
					"file_name" -> JsString(fileName),
					"id" -> JsString(id.toString),
					"progress" -> JsNumber(progress)
				)
			}(sys.executionContext)

	private def extractMedia(media: MediaDescription): MultiMedia = 
		MultiMedia(media.mediaInfo, media.duration, media.format)
}

private[service] object FileActorHandler {
	def apply(shards: ClusterSharding, sys: ActorSystem[_])(
		implicit t: Timeout): FileActorHandler = new FileActorHandler(shards, sys)
}