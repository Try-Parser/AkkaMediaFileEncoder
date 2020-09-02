package media.service.entity

import spray.json.{ DefaultJsonProtocol, DeserializationException, JsNumber, JsObject, JsString, JsValue, RootJsonFormat }
import ws.schild.jave.encode.enums.X264_PROFILE
import ws.schild.jave.encode.{ AudioAttributes, VideoAttributes }
import ws.schild.jave.info.VideoSize

final case class Mp4(
	audioCodec: String = "aac",
	audioBitRate: Int = 64000,
	audioChannels: Int = 2,
	audioSamplingRate: Int = 44100,
	videoCodec: String = "h264",
	videoProfile: X264_PROFILE = X264_PROFILE.BASELINE,
	videoBitRate: Int = 160000,
	videoFrameRate: Int = 15,
	videoSize: (Int, Int) = (400, 300)) extends Codec {

	override val codecName: String = "mp4"

	override def audioAttrs(): AudioAttributes = {
		audio.setCodec("aac")
		// here 64kbit/s is 64000
		audio.setBitRate(audioBitRate)
		audio.setChannels(audioChannels)
		audio.setSamplingRate(audioSamplingRate)
		audio
	}

	override def videoAttrs(): VideoAttributes = {
		video.setCodec(videoCodec)
		video.setX264Profile(videoProfile)
		// Here 160 kbps video is 160000
		video.setBitRate(videoBitRate)
		// More the frames more quality and size, but keep it low based on devices like mobile
		video.setFrameRate(videoFrameRate)
		video.setSize(new VideoSize(videoSize._1, videoSize._1))
		video
	}

	override def toJson: JsObject = Mp4.Implicits.write(this).asJsObject
}

object Mp4 extends DefaultJsonProtocol {
	implicit object Implicits extends RootJsonFormat[Mp4] {
		def write(mp4: Mp4) = JsObject(
			"audio" -> JsObject(
				"bit_rate" -> JsNumber(mp4.audioBitRate),
				"codec" -> JsString(mp4.audioCodec),
				"channels" -> JsNumber(mp4.audioChannels),
				"sampling_rate" -> JsNumber(mp4.audioSamplingRate)
			),
			"video" -> JsObject(
				"bit_rate" -> JsNumber(mp4.videoBitRate),
				"codec" -> JsString(mp4.videoCodec),
				"frame_rate" -> JsNumber(mp4.videoFrameRate),
				"size" -> JsString(s"${mp4.videoSize._1}x${mp4.videoSize._2}")
			),
			"extn" -> JsString(mp4.codecName)
		)

		def read(json: JsValue) =
			json.asJsObject.getFields("audio", "video") match {
				case Seq(JsObject(audio), JsObject(video)) =>
					val (aBitRate,
						aCodec,
						aChannels,
						aSamplingRate): (Int, String, Int, Int) = audio match {
							case Seq(JsNumber(bit_rate), JsString(codec), JsNumber(channels), JsNumber(sampling_rate)) =>
									(bit_rate.toInt, codec, channels.toInt, sampling_rate.toInt)
							case _ => throw new DeserializationException("Invalid audio config")}

					val (vBitRate,
						vCodec,
						vChannels,
						vFrameRate,
						vProfile,
						(h, w)): (Int, String, Int, Int, X264_PROFILE, (Int, Int)) = video match {
							case Seq(JsNumber(bit_rate),
								JsString(codec),
								JsNumber(channels),
								JsNumber(frame_rate),
								JsObject(size),
								JsString(profile)) => (
									bit_rate.toInt,
									codec,
									channels.toInt,
									frame_rate.toInt,
									X264_PROFILE.valueOf(profile), size match {
									case Seq(JsNumber(h), JsNumber(w))=> (h.toInt, w.toInt)
									case _ => throw new DeserializationException("Invalid size config")
								})
							case _ => throw new DeserializationException("Invalid video config")}

					Mp4(aCodec, aBitRate, aChannels, aSamplingRate, vCodec, vProfile, vBitRate, vFrameRate, (h,w))
				case _ => throw new DeserializationException("Invalid Mp4 config")
			}
	}
}