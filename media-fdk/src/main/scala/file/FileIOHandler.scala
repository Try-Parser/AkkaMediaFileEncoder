package media.fdk.file

import java.nio.file.Paths
import java.io.File

import scala.concurrent.Future

import akka.util.ByteString
import akka.stream.IOResult
import akka.stream.scaladsl.{ FileIO, Source }
import akka.stream.Materializer

import ws.schild.jave.MultimediaObject
import com.typesafe.config.Config

import utils.file.FileHandler

case class FileIOHandler(handler: FileHandler) {

	def getMultiMedia(name: String): MultimediaObject = 
		getMultiMedia(handler.getFile(name))

	def getMultiMedia(file: File): MultimediaObject = new MultimediaObject(file)

	def getFileWithPath(fileName: String): Source[ByteString, Future[IOResult]] =
		FileIO.fromPath(Paths.get(s"${handler.basePath}/${handler.convertFilePath}/$fileName"))

	def writeFile(
		fileName: String, 
		source: Source[ByteString, _])(implicit mat: Materializer): Future[IOResult] =
			source.runWith(FileIO.toPath(Paths.get(s"${handler.basePath}/${handler.uploadFilePath}/$fileName")))
}

object FileIOHandler {
	def apply(config: Config): FileIOHandler = FileIOHandler(FileHandler(config))
}