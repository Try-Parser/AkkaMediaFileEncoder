package media.state.media

import java.nio.file.{ Files, Paths }

import scala.concurrent.Future

import ws.schild.jave.{ Encoder, MultimediaObject }
import ws.schild.jave.encode.{ 
	AudioAttributes, 
	VideoAttributes, 
	EncodingAttributes 
}

import media.fdk.codec.{ Video, Audio }
import media.fdk.json.{ MultiMedia, MediaEncoder, VideoCodec, AudioCodec, Formats }
import media.state.media.Progress


import utils.implicits.Primitive._

object MediaConverter {
	import scala.concurrent.ExecutionContext.Implicits.global

	private val encoder: Encoder = new Encoder()
	private val fileHandler = utils.file.FileHandler()

	def startConvert(config: MultiMedia, nName: String): Future[Option[String]] = Future { 
		convert(config, 
			new MultimediaObject(
				fileHandler.getFile(s"${config.info.fileName}")), nName)
	}

	def getAvailableFormats(video: Boolean = true, audio: Boolean = true): MediaEncoder = 
		MediaEncoder(
			VideoCodec(encoder.getVideoEncoders.toList, encoder.getVideoDecoders.toList),
			AudioCodec(encoder.getAudioEncoders.toList, encoder.getAudioDecoders.toList),
			Formats(encoder.getSupportedEncodingFormats.toList, encoder.getSupportedDecodingFormats.toList),
			video, audio)

	private def convert(config: MultiMedia, source: MultimediaObject, nName: String): Option[String] = {
		val attrs: EncodingAttributes = new EncodingAttributes()
		config.info.video.map(processVideo(_)).map(attrs.setVideoAttributes(_))
		config.info.audio.map(processAudio(_)).map(attrs.setAudioAttributes(_))
		attrs.setOutputFormat(config.format.value)

		Option(try {
			encoder.encode(
				source, 
				fileHandler.getFile(nName, false), 
				attrs,
				Progress())

			config.info.fileName
		} catch {
			case _ : Throwable => Files
				.deleteIfExists(
					Paths.get(s"${fileHandler.basePath}/${fileHandler.convertFilePath}/$nName"))
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