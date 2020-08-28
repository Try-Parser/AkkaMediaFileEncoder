package media.service.handler 

import akka.actor.typed.ActorSystem

import akka.cluster.sharding.typed.scaladsl.ClusterSharding
import akka.util.Timeout

private[service] class FileActorHandler(shards: ClusterSharding, sys: ActorSystem[_])
	(implicit timeout: Timeout) {

	import java.util.UUID
	import java.nio.file.Paths
	import java.io.File

	import scala.concurrent.{ ExecutionContext, Future }
	
	import akka.util.ByteString
	import akka.stream.Materializer
	import akka.stream.IOResult
	import akka.stream.scaladsl.{ FileIO, Source }
	import akka.http.scaladsl.model.ContentType
	import akka.http.scaladsl.model.HttpEntity.{ Chunked, ChunkStreamPart }
	import akka.http.scaladsl.model.{ ContentTypes, ResponseEntity }
	import akka.http.scaladsl.server.directives.FileInfo
	
	import FileActorHandler._

	import media.service.models.FileActor
	import media.service.models.FileActor.{
		Upload,
		Play,
		FileUpload,
		ContentTypeData
	}

	implicit private val mat: Materializer = Materializer(sys.classicSystem)
	implicit private val ec: ExecutionContext = mat.executionContext

	val key = FileActor.TKey

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
		source: Source[ByteString, _]): Future[FileUpload] = {
		
		val newName = java.util.UUID.randomUUID.toString
		val xtn = getXtn(meta.fileName)

		writeFile(s"$newName.$xtn", source).flatMap {
			_ => uploadFile(newName, xtn, meta.contentType)
		}
	}

	def getFile(name: String): File = new File(s"${basePath}/$name")

	def getChunked(name: String): ResponseEntity  = 
		Chunked(ContentTypes.`application/octet-stream`, getSource(name)) 

	private def writeFile(
		fileName: String, 
		source: Source[ByteString, _]): Future[IOResult] =
			source.runWith(FileIO.toPath(Paths.get(s"${basePath}/$fileName")))

	private def getXtn(fileName: String): String = {
		val fullName = fileName.split(".")
		if(fullName.size <= 1) "tmp" else fullName(fullName.size-1)
	}

	private def getFileWithPath(fileName: String): Source[ByteString, Future[IOResult]] =
		FileIO.fromPath(Paths.get(s"${basePath}/$fileName"))

	private def getSource(name: String): Source[ChunkStreamPart, Future[IOResult]] =
		getFileWithPath(name).map(ChunkStreamPart.apply)
}

private[service] object FileActorHandler {

	import com.typesafe.config.ConfigFactory

	val basePath: String = ConfigFactory.load().getString("upload.path")

	def apply(shards: ClusterSharding, sys: ActorSystem[_])(
		implicit t: Timeout): FileActorHandler = new FileActorHandler(shards, sys)
}