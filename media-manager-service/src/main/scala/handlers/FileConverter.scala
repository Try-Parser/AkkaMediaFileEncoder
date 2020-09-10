package media.service.handlers

import java.nio.file.{ Files, Paths }

import scala.concurrent.Future

import ws.schild.jave.{ Encoder, MultimediaObject }
import ws.schild.jave.encode.{ 
	AudioAttributes, 
	VideoAttributes, 
	EncodingAttributes 
}

import media.service.entity.{ MediaConvert, Video, Audio }
import media.fdk.models.{ MultiMedia, VideoCodec, AudioCodec, Formats }
import media.service.handlers.ProgressHandler

import utils.implicits.Primitive._

private[service] object FileConverter {
	import scala.concurrent.ExecutionContext.Implicits.global

	private val encoder: Encoder = new Encoder()

	def startConvert(config: MediaConvert): Future[Option[String]] = Future { 
		convert(config, 
			new MultimediaObject(
				FileHandler.getFile(s"${config.file.fileName}.${config.file.ext}")))
	}

	def getAvailableFormats(video: Boolean = true, audio: Boolean = true): MultiMedia = 
		MultiMedia(
			VideoCodec(encoder.getVideoEncoders.toList, encoder.getVideoDecoders.toList),
			AudioCodec(encoder.getAudioEncoders.toList, encoder.getAudioDecoders.toList),
			Formats(encoder.getSupportedEncodingFormats.toList, encoder.getSupportedDecodingFormats.toList),
			video, audio)

	private def convert(config: MediaConvert, source: MultimediaObject): Option[String] = {
		val attrs: EncodingAttributes = new EncodingAttributes()
		config.video.map(processVideo(_)).map(attrs.setVideoAttributes(_))
		config.audio.map(processAudio(_)).map(attrs.setAudioAttributes(_))
		attrs.setOutputFormat(config.format.value)

		val fullPath: String = s"${java.util.UUID.randomUUID}.${config.file.ext}"

		Option(try {
			encoder.encode(
				source, 
				FileHandler.getFile(fullPath, false), 
				attrs,
				ProgressHandler())

			config.file.fileName
		} catch {
			case _ : Throwable => Files
				.deleteIfExists(
					Paths.get(s"${FileHandler.convertPath}/$fullPath"))
				null
		})
	}

	private def processVideo(v: Video): VideoAttributes = {
		val video: VideoAttributes = new VideoAttributes()
		v.bitRate.value.nonZeroInt.map(video.setBitRate(_))
		v.frameRate.value.nonZeroDouble.map(fr => video.setFrameRate(fr.toInt))
		v.codec.value.nonEmptyString.map(video.setCodec(_))
		v.tag.value.nonEmptyString.map(video.setTag(_))
		video.setSize(v.size)
		video.setX264Profile(v.profile);
		video
	}

	private def processAudio(a: Audio): AudioAttributes = {
		val audio: AudioAttributes = new AudioAttributes()
		a.codec.value.nonEmptyString.map(audio.setCodec(_))
		a.bitRate.value.nonZeroInt.map(audio.setBitRate(_))
		a.samplingRate.value.nonZeroInt.map(audio.setSamplingRate(_))
		a.channels.value.nonZeroInt.map(audio.setChannels(_))
		a.volume.value.nonZeroInt.map(audio.setVolume(_))
		audio
	}
}