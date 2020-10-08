package media.state.media

import java.nio.file.{Files, Paths}

import scala.concurrent.Future
import akka.actor.typed.ActorSystem
import ws.schild.jave.{Encoder, MultimediaObject}
import ws.schild.jave.encode.{AudioAttributes, EncodingAttributes, VideoAttributes}
import media.fdk.codec.{Audio, Video}
import media.fdk.json.{AudioCodec, Formats, MediaEncoder, PreferenceSettings, VideoCodec}
import media.state.media.Progress
import utils.implicits.Primitive._
import ws.schild.jave.encode.enums.X264_PROFILE

object MediaConverter {
	import scala.concurrent.ExecutionContext.Implicits.global

	private val encoder: Encoder = new Encoder()
	private val fileHandler = utils.file.FileHandler()

	def startConvert(
		config: PreferenceSettings,
		nName: String)(implicit system: ActorSystem[_]
	): Future[Option[String]] = Future { 
		println(config.fileName)
		convert(config, 
			new MultimediaObject(
				fileHandler.getFile(s"${config.fileName}")), nName)
	}

	def getAvailableFormats(video: Boolean = true, audio: Boolean = true): MediaEncoder = 
		MediaEncoder(
			VideoCodec(encoder.getVideoEncoders.toList, encoder.getVideoDecoders.toList),
			AudioCodec(encoder.getAudioEncoders.toList, encoder.getAudioDecoders.toList),
			Formats(encoder.getSupportedEncodingFormats.toList, encoder.getSupportedDecodingFormats.toList),
			video, audio)

	private def convert(
		config: PreferenceSettings, 
		source: MultimediaObject, 
		nName: String
	)(implicit system: ActorSystem[_]): Option[String] = {
		val attrs: EncodingAttributes = new EncodingAttributes()
		config.video.map(processVideo(_)).map(attrs.setVideoAttributes(_))
		config.audio.map(processAudio(_)).map(attrs.setAudioAttributes(_))
		attrs.setOutputFormat(config.format.value)

		Option(try {
			encoder.encode(
				source, 
				fileHandler.getFile(nName, false), 
				attrs,
				Progress(progressId = config.progress))

			nName
		} catch {
			case a : Throwable => 
				println(s"$a 00000000000000000000000000000000000000000000000000000000")
				Files
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
		v.profile.map(res => video.setX264Profile(X264_PROFILE.valueOf(res)))
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