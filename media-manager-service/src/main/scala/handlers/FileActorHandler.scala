package media.service.handlers

import java.util.UUID

import scala.concurrent.Future

import akka.util.ByteString
import akka.stream.scaladsl.Source
import akka.http.scaladsl.server.directives.FileInfo
import akka.cluster.sharding.typed.scaladsl.Entity
import akka.cluster.sharding.typed.scaladsl.ClusterSharding
import akka.util.Timeout
import akka.actor.typed.ActorSystem

import media.state.models.FileActorModel.{ File, AddFile, Test }
import media.state.models.FileActorModel
import media.state.events.EventProcessorSettings
import media.fdk.file.FileIOHandler
import media.fdk.json.MultiMedia
import media.fdk.codec.{ Video, Audio }
import media.fdk.codec.Codec.{ Duration, Format }
import akka.cluster.sharding.typed.ShardingEnvelope

private[service] class FileActorHandler(shards: ClusterSharding, sys: ActorSystem[_])
	(implicit timeout: Timeout) {

	implicit private val sett = EventProcessorSettings(sys)
	implicit private val system = sys

	// val proxy = 
	shards
		.init(Entity(FileActorModel.TypeKey)(FileActorModel.createBehavior))

	def test(): Future[Unit] = {
		// proxy ! ShardingEnvelope(UUID.randomUUID.toString(), FileActorModel.Test)  
		Future(shards
			.entityRefFor(FileActorModel.TypeKey, UUID.randomUUID.toString)
			.tell(Test))(sys.executionContext)
	}

	def uploadFile(meta: FileInfo, byteSource: Source[ByteString, _]): Future[String] =  {
		byteSource.runFold(ByteString.empty)(_ ++ _).flatMap { byteS => 
			sendToActor(meta, byteS).map { file => 
				// val mediaInfo = file.file.convertToMediaInfo()
				// val mmObject = FileIOHandler.getMultiMedia(mediaInfo.fileName)
				// val mmoInfo = mmObject.getInfo()

				println("000000000000000000000000000000000000000000000000000000000000000000000000000000")
	      println("000000000000000000000000000000000000000000000000000000000000000000000000000000")
	      println("000000000000000000000      Mr Debug uploadFile      00000000000000000000000000")
	      println("000000000000000000000000000000000000000000000000000000000000000000000000000000")
	      println("000000000000000000000000000000000000000000000000000000000000000000000000000000")

				// MultiMedia(
				// 	mediaInfo, 
				// 	Duration(mmoInfo.getDuration().toInt), 
				// 	Option(Video(mmoInfo.getVideo())),
				// 	Option(Audio(mmoInfo.getAudio())),
				// 	Format(mmoInfo.getFormat()))

				"yes"
			}(sys.executionContext)
		}(sys.executionContext)
	}

	def sendToActor(meta: FileInfo, byteSource: ByteString): Future[FileActorModel.Get] = {
			println("000000000000000000000000000000000000000000000000000000000000000000000000000000")
      println("000000000000000000000000000000000000000000000000000000000000000000000000000000")
      println("000000000000000000000      Mr Debug sendToActor      0000000000000000000000000")
      println("000000000000000000000000000000000000000000000000000000000000000000000000000000")
      println("000000000000000000000000000000000000000000000000000000000000000000000000000000")
			shards
				.entityRefFor(FileActorModel.TypeKey, "8c5aefc7-8921-4cac-b938-6b2149157c0e")
				.ask(AddFile(File(
					meta.fileName, 
					byteSource.toString, 
					meta.contentType.toString, 
					0
				), _))
	}
}

private[service] object FileActorHandler {
	def apply(shards: ClusterSharding, sys: ActorSystem[_])(
		implicit t: Timeout): FileActorHandler = new FileActorHandler(shards, sys)
}