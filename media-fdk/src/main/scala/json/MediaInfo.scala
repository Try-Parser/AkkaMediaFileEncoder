package media.fdk.json

import utils.file.ContentType

import java.util.UUID
import akka.util.ByteString
import akka.stream.scaladsl.Source

import spray.json.{
	JsNumber,
	DefaultJsonProtocol,
	JsString,
	JsObject
}

import ws.schild.jave.MultimediaObject

final case class MediaInfo(
	fileName: String,
	fileData: MultimediaObject,
	contentType: ContentType.HttpContentType,
	status: Int,
	fileId: UUID = UUID.randomUUID()) {
		def toJson(): JsObject = MediaInfo.Implicits.write(this).asJsObject
}

object MediaInfo extends DefaultJsonProtocol {
	implicit object Implicits {
		def write(info: MediaInfo): JsObject = JsObject(
			"file_name" -> JsString(info.fileName),
			"content_type" -> JsString(info.contentType.toString),
			"status" -> JsNumber(info.status),
			"id" -> JsNumber(info.fileId.toString)
		)
	}
}