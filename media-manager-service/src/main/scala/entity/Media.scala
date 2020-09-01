package media.service.entity

import ws.schild.jave.info.MultimediaInfo

import spray.json.{
	JsNumber,
	JsObject,
	JsString,
	// DefaultJsonProtocol,
	// RootJsonFormat
}

import media.service.models.FileActor.FileUpload

case class Media(mmi: MultimediaInfo, file: FileUpload) {
	def toJson: JsObject = Media.Implicits.write(this).asJsObject
}

object Media {
	implicit object Implicits {
		def write(media: Media): JsObject = {
			val audio = Option(media.mmi.getAudio()) match {
				case Some(audio) => JsObject(
					"decoder" -> JsString(audio.getDecoder()),
					"sampling_rate" -> JsNumber(audio.getSamplingRate()),
					"channels" -> JsNumber(audio.getChannels()),
					"bit_rate" -> JsNumber(audio.getBitRate()))
				case None => JsString("")
			}

			val video = Option(media.mmi.getVideo()) match {
				case Some(video) => JsObject(
					"decoder" -> JsString(video.getDecoder()),
					"size" -> JsObject(
						"width" -> JsNumber(video.getSize().getWidth()),
						"height" -> JsNumber(video.getSize().getHeight())),
					"bit_rate" -> JsNumber(video.getBitRate()),
					"frame_rate" -> JsNumber(video.getFrameRate()))
				case None => JsString("")
			}

			JsObject(
				"file_uploaded" -> media.file.toJson,
				"format" -> JsString(media.mmi.getFormat()),
				"duration" -> JsNumber(media.mmi.getDuration()),
				"video" -> video,
				"audio" -> audio)
		}
	}
}