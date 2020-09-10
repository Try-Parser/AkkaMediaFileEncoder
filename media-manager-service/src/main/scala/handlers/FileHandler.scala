package media.service.handlers 

import java.nio.file.Paths
import java.io.File

import java.nio.file.Paths

import scala.concurrent.Future

import akka.util.ByteString
import akka.http.scaladsl.model.HttpEntity.{ Chunked, ChunkStreamPart }
import akka.http.scaladsl.model.{ ContentTypes, ResponseEntity }
import akka.stream.IOResult
import akka.stream.scaladsl.{ FileIO, Source }
import akka.stream.Materializer
import akka.http.scaladsl.model.{ ContentType, ErrorInfo }

import ws.schild.jave.MultimediaObject

private[service] object FileHandler {
	private val factory: com.typesafe.config.Config = 
		com.typesafe.config.ConfigFactory.load()

	val uploadPath: String = factory.getString("file-directory.upload.path")
	val convertPath: String = factory.getString("file-directory.convert.path")
	val maxContentSize: Long = factory.getLong("file-directory.upload.max-content-size")

	def getFile(name: String, upload: Boolean = true): File = 
		new File(s"${if(upload) uploadPath else convertPath}/$name")

	def getChunked(name: String): ResponseEntity  = 
		Chunked(ContentTypes.`application/octet-stream`, getSource(name)) 

	def getMultiMedia(name: String): MultimediaObject = getMultiMedia(getFile(name))

	def getMultiMedia(file: File): MultimediaObject = new MultimediaObject(file)

	def getXtn(fileName: String): String = {
		val fullName: Array[String] = fileName.split("\\.")
		if(fullName.size <= 1) "tmp" else fullName(fullName.size-1)
	}

	def getFileWithPath(fileName: String): Source[ByteString, Future[IOResult]] =
		FileIO.fromPath(Paths.get(s"${convertPath}/$fileName"))

	def getSource(name: String): Source[ChunkStreamPart, Future[IOResult]] =
		getFileWithPath(name).map(ChunkStreamPart.apply)

	def writeFile(
		fileName: String, 
		source: Source[ByteString, _])(implicit mat: Materializer): Future[IOResult] =
			source.runWith(FileIO.toPath(Paths.get(s"${FileHandler.uploadPath}/$fileName")))

	final case class ContentTypeData(content: String) {
		def getContentType(): Either[List[ErrorInfo], ContentType] = ContentType.parse(content)
	}
}