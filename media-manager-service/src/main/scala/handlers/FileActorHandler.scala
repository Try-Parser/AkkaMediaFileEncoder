package media.service.handlers

import akka.actor.typed.{ ActorRef, ActorSystem }

import akka.cluster.sharding.typed.scaladsl.ClusterSharding
import akka.util.Timeout

private[service] class FileActorHandler(shards: ClusterSharding, sys: ActorSystem[_])
	(implicit timeout: Timeout) {

	import java.util.UUID

	import scala.concurrent.{ ExecutionContext, Future }
	
	import akka.util.ByteString
	import akka.stream.Materializer
	import akka.stream.scaladsl.Source
	import akka.http.scaladsl.model.ContentType
	import akka.http.scaladsl.server.directives.FileInfo
	
	import media.service.models.FileActor
	import media.service.models.FileActor.{
		Upload,
		Play,
		FileUpload
	}
	import media.service.handlers.FileHandler.ContentTypeData
	import media.service.entity.Media

	import media.state.models.FileActorModel.{
		File,
		AddFile
	}
	import akka.cluster.sharding.typed.ShardingEnvelope
	import utils.traits.Command
	import akka.cluster.sharding.typed.scaladsl.Entity
	import media.state.models.FileActorModel
	import media.state.events.EventProcessorSettings

	implicit private val mat: Materializer = Materializer(sys.classicSystem)
	implicit private val ec: ExecutionContext = mat.executionContext
	implicit private val sett = EventProcessorSettings(sys)
	implicit private val system = sys

	val key = FileActor.TKey

	shards
		.init(Entity(FileActorModel.TypeKey)(FileActorModel.createBehavior)
		.withRole("write-model-proxy"))

	def test(): Unit = { 
		shards.entityRefFor(FileActorModel.TypeKey, UUID.randomUUID.toString)
		.ask(AddFile(File("", null, "", null, 0), _))
	}

	def uploadFile(
		fileName: String, 
		ext: String, 
		contentType: ContentType): Future[FileUpload] = shards
			.entityRefFor(key, FileActor.actor.actorName)
			.ask(Upload(fileName, ext, ContentTypeData(contentType.toString), _))

	def play(uuid: UUID): Future[Option[FileUpload]] = shards
		.entityRefFor(key, FileActor.actor.actorName)
		.ask(Play(uuid, _))

	def writeFile(
		meta: FileInfo, 
		source: Source[ByteString, _]): Future[Media] = {
		
		val newName = java.util.UUID.randomUUID.toString
		val xtn = FileHandler.getXtn(meta.fileName)

		FileHandler.writeFile(s"$newName.$xtn", source).flatMap {
			_ => uploadFile(newName, xtn, meta.contentType).map {
				Media(FileHandler.getMultiMedia(s"$newName.$xtn").getInfo(), _)
			}
		}
	}

}

private[service] object FileActorHandler {
	def apply(shards: ClusterSharding, sys: ActorSystem[_])(
		implicit t: Timeout): FileActorHandler = new FileActorHandler(shards, sys)
}