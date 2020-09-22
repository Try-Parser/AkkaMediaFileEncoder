package media.fdk.json

import spray.json.{
	JsObject,
	DefaultJsonProtocol,
	RootJsonFormat,
	DeserializationException,
	JsValue,
	JsArray,
	JsString
}

import ws.schild.jave.encode.enums.X264_PROFILE

sealed trait Codec {
	def getEncoders: List[String]
	def getDecoders: List[String]
	def toJson(): JsValue
}

final case class VideoCodec(encoders: List[String], decoders: List[String]) extends Codec {
	override def getDecoders(): List[String] = decoders
	override def getEncoders(): List[String] = encoders
	override def toJson(): JsObject = MediaEncoder.VideoCodec.write(this).asJsObject
}

final case class AudioCodec(encoders: List[String], decoders: List[String]) extends Codec {
	override def getDecoders(): List[String] = decoders
	override def getEncoders(): List[String] = encoders
	override def toJson(): JsObject = MediaEncoder.AudioCodec.write(this).asJsObject
}

final case class Formats(encoders: List[String], decoders: List[String]) extends Codec {
	override def getDecoders(): List[String] = decoders
	override def getEncoders(): List[String] = encoders
	override def toJson(): JsObject = MediaEncoder.Formats.write(this).asJsObject
}

final case class MediaEncoder(
	video: VideoCodec, 
	audio: AudioCodec, 
	format: Formats, 
	isVideo: Boolean = false, 
	isAudio: Boolean = false) {
		def toJson(): JsObject = MediaEncoder.Implicits.write(this).asJsObject
}

object MediaEncoder extends DefaultJsonProtocol {
	implicit object Formats extends RootJsonFormat[Formats] {
		def write(f: Formats) = ed[Formats](f)
		def read(js: JsValue) = ed[Formats](js) ((e, d)  => new Formats(e, d))
	}

	implicit object VideoCodec extends RootJsonFormat[VideoCodec] {
		def write(vc: VideoCodec) = ed[VideoCodec](vc)
		def read(js: JsValue) = ed[VideoCodec](js) ((e, d)  => new VideoCodec(e, d))
	}

	implicit object AudioCodec extends RootJsonFormat[AudioCodec] {
		def write(ac: AudioCodec) = ed[AudioCodec](ac)
		def read(js: JsValue) = ed[AudioCodec](js) ((e, d) => new AudioCodec(e, d))
	}

	implicit object Implicits {
		def write(mm: MediaEncoder) = {
			val vid = if(mm.isVideo) mm.video.toJson else JsString("")
			val aud = if(mm.isAudio) mm.audio.toJson else JsString("")

			JsObject(
				"video" -> vid,
				"audio" -> aud,
				"format" -> mm.format.toJson,
				"profile" -> JsArray(X264_PROFILE.values.map(v => JsString(v.getModeName)).toVector))
	}}

	private def ed[T <: Codec](ec: T): JsObject = JsObject(
		"encoders" -> JsArray(ec.getEncoders.map(JsString(_))),
		"decoders" -> JsArray(ec.getDecoders.map(JsString(_))))

	private def ed[T <: Codec](js: JsValue)(
		action: (List[String], List[String]) => T) = js.asJsObject.getFields("encoders", "decoders") match {
			case Seq(JsArray(encoders), JsArray(decoders)) => 
				action(
					encoders.map(_.toString).toList, 
					encoders.map(_.toString).toList)
			case _ => throw new DeserializationException("Invalid config")
	}
}