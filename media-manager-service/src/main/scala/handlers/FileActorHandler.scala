package media.service.handlers

import utils.concurrent.SysLog
import scala.concurrent.Future

import akka.util.ByteString
import akka.stream.scaladsl.Source
import akka.http.scaladsl.server.directives.FileInfo
import akka.cluster.sharding.typed.scaladsl.Entity
import akka.cluster.sharding.typed.scaladsl.ClusterSharding
import akka.util.Timeout
import akka.actor.typed.ActorSystem

import media.state.models.FileActorModel.{ 
	File, 
	AddFile, 
	MediaDescription, 
	TypeKey,
	createBehavior => CreateBehavior
}
import media.state.events.EventProcessorSettings
import media.fdk.json.MultiMedia

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
			sendToActor(meta, byteS).map { case MediaDescription(duration, format, file) => 
				val mediaInfo = file.convertToMediaInfo()
				MultiMedia(
					mediaInfo, 
					duration, 
					file.mediaInfo._1,
					file.mediaInfo._2,
					format)
			}(sys.executionContext)
		}(sys.executionContext)
	}

	def sendToActor(meta: FileInfo, byteSource: ByteString): Future[MediaDescription] = {
				shards.entityRefFor(TypeKey, regionId)
				.ask(AddFile(File(
					meta.fileName, 
					byteSource.toArray, 
					meta.contentType.toString, 
					0
				), _))
	}
}

private[service] object FileActorHandler {
	def apply(shards: ClusterSharding, sys: ActorSystem[_])(
		implicit t: Timeout): FileActorHandler = new FileActorHandler(shards, sys)
}