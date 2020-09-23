package media.service.handlers

import utils.concurrent.SysLog
import scala.concurrent.Future

import akka.util.{ ByteString, Timeout }
import akka.stream.scaladsl.Source
import akka.http.scaladsl.server.directives.FileInfo
import akka.cluster.sharding.typed.scaladsl.{ Entity, ClusterSharding }
import akka.actor.typed.ActorSystem

import media.state.models.actors.FileActor.{ 
	File, 
	AddFile, 
	MediaDescription, 
	ConvertFile,
	TypeKey,
	createBehavior => CreateBehavior,
	FileProgress
}
import media.state.events.EventProcessorSettings
import media.fdk.json.MultiMedia

import spray.json.{ JsObject, JsString, JsNumber }

private[service] class FileActorHandler(shards: ClusterSharding, sys: ActorSystem[_])
	(implicit timeout: Timeout) extends SysLog(sys.log) {

	implicit private val sett = EventProcessorSettings(sys)
	implicit private val system = sys

	val regionId = com.typesafe.config.ConfigFactory
		.load()
		.getString("media-manager-service.region.id")
	
	shards.init(Entity(TypeKey)(CreateBehavior))

	def uploadFile(meta: FileInfo, byteSource: Source[ByteString, _]): Future[MultiMedia] =  {
		byteSource.runFold(ByteString.empty)(_ ++ _).flatMap { byteS => 
			shards.entityRefFor(TypeKey, java.util.UUID.randomUUID.toString)
				.ask(AddFile(File(
					meta.fileName, 
					byteS.toArray, 
					meta.contentType.toString, 
					0
				), _)).map(extractMedia)(sys.executionContext)
		}(sys.executionContext)
	}

	def convertFile(mm: MultiMedia): Future[JsObject] = 
		shards.entityRefFor(TypeKey, mm.info.fileId.toString)
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