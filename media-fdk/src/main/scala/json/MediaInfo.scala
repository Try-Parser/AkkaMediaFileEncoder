package media.fdk.json

import utils.file.HttpContentType

import java.util.UUID
import akka.util.ByteString
import akka.stream.scaladsl.Source

import media.fdk.codec.{ Audio, Video }

import spray.json.{
	JsNumber,
	DefaultJsonProtocol,
	JsString,
	JsObject
}

import ws.schild.jave.MultimediaObject

final case class MediaInfo(
	fileName: String,
	video: Option[Video], 
	audio: Option[Audio],
	contentType: HttpContentType,
	status: Int,
	fileId: UUID = UUID.randomUUID()) {
		def toJson(): JsObject = MediaInfo.Implicits.write(this).asJsObject
}

object MediaInfo extends DefaultJsonProtocol {
	implicit object Implicits {
		def write(info: MediaInfo): JsObject = JsObject(
			"file_name" -> JsString(info.fileName),
			"content_type" -> JsString(info.contentType.get.toString),
			"status" -> JsNumber(info.status),
			"id" -> JsString(info.fileId.toString)
		)
	}
}