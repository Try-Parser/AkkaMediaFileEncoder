package media.service.entity

import ws.schild.jave.encode.{ AudioAttributes, VideoAttributes }

import spray.json.{
	JsObject,
	JsString,
	JsValue,
	JsNumber,
	DefaultJsonProtocol,
	RootJsonFormat,
	DeserializationException
}

import media.service.entity.Codec

final case class Mp3(
		audioBitRate: Int = 128000,
		audioChannels: Int = 2,
		audioSamplingRate: Int = 44100,
		videoBitRate: String = VideoAttributes.DIRECT_STREAM_COPY) extends Codec("mp3") {

	val audioCodec: String = "libmp3lame"

	def apply(): Mp3 = {
		audio.setCodec("libmp3lame")
		this
	}

	override def audioAttrs(): AudioAttributes = {
		audio.setBitRate(audioBitRate)
		audio.setChannels(audioChannels)
		audio.setSamplingRate(audioSamplingRate)
		audio
	}

	override def videoAttrs(): VideoAttributes = {
		video.setCodec(VideoAttributes.DIRECT_STREAM_COPY)
		video
	}

	override def toJson(): JsObject = Mp3.Implicits.write(this).asJsObject
} 

object Mp3 extends DefaultJsonProtocol {
	implicit object Implicits extends RootJsonFormat[Mp3] {
		def write(mp3: Mp3) = JsObject(
			"audio" -> JsObject(
				"codec" -> JsString(mp3.audioCodec),
				"bit_rate" -> JsNumber(mp3.audioBitRate),
				"channels" -> JsNumber(mp3.audioChannels),
				"sampling_rate" -> JsNumber(mp3.audioSamplingRate)
			),
			"video" -> JsObject(
				"bit_rate" -> JsString("copy")
			),
			"extn" -> JsString(mp3.codecName)
		)

		def read(json: JsValue) =
			json.asJsObject.getFields("bit_rate", "channels", "sampling_rate") match {
				case Seq(JsNumber(bit_rate), JsNumber(channels), JsNumber(sampling_rate)) => 
					Mp3(audioBitRate = bit_rate.toInt,
						audioChannels = channels.toInt,
						audioSamplingRate = sampling_rate.toInt)
				case _ => throw new DeserializationException("Invalid Json Object")
			}
	}
}