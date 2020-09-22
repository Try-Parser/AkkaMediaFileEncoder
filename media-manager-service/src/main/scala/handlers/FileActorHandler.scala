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

import media.state.models.actors.FileActor.{ 
	File, 
	AddFile, 
	MediaDescription, 
	ConvertFile,
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
			shards.entityRefFor(TypeKey, regionId)
				.ask(AddFile(File(
					meta.fileName, 
					byteS.toArray, 
					meta.contentType.toString, 
					0
				), _)).map { case MediaDescription(duration, format, mediaInfo) => 
				MultiMedia(
					mediaInfo, 
					duration, 
					format)
			}(sys.executionContext)
		}(sys.executionContext)
	}

	def convertFile(mm: MultiMedia): Future[Unit] = 
		shards.entityRefFor(TypeKey, regionId)
			.ask(ConvertFile(mm, _))

}

private[service] object FileActorHandler {
	def apply(shards: ClusterSharding, sys: ActorSystem[_])(
		implicit t: Timeout): FileActorHandler = new FileActorHandler(shards, sys)
}