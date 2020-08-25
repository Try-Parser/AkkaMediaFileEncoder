package media.service.handler 

import java.util.UUID

import scala.concurrent.Future

import akka.cluster.sharding.typed.scaladsl.ClusterSharding
import akka.util.Timeout

import media.service.models.FileActor
import media.service.models.FileActor.{
	Upload,
	Play,
	FileUpload
}

private[service] class FileActorHandler(shards: ClusterSharding)
	(implicit timeout: Timeout) {

	val key = FileActor.TKey

	def uploadFile(
		fileName: String, 
		ext: String, 
		source: String): Future[FileUpload] = shards
			.entityRefFor(key, FileActor.actor.actorName)
			.ask(Upload(fileName, ext, source, _))


	def play(uuid: UUID): Future[Option[FileUpload]] = shards
		.entityRefFor(key, FileActor.actor.actorName)
		.ask(Play(uuid, _))
}

private[service] object FileActorHandler {
	def apply(shards: ClusterSharding)(implicit t: Timeout): FileActorHandler = 
		new FileActorHandler(shards)
}