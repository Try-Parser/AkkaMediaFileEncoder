package media.service.entity

import ws.schild.jave.encode.{AudioAttributes, VideoAttributes}
import ws.schild.jave.info.VideoSize

object Codec {

	private val NonZeroInt: Int => Option[Int] = i => if (i <= 0) None else Some(i)
	private val NonEmptyString: String => Option[String] = s => if (s.isEmpty) None else Some(s)

	final case class CodecName(value: String) extends AnyVal
	final case class BitRate(value: Int) extends AnyVal
	final case class SamplingRate(value: Int) extends AnyVal
	final case class Channels(value: Int) extends AnyVal
	final case class Volume(value: Int) extends AnyVal
	final case class Quality(value: Int) extends AnyVal
	final case class FrameRate(value: Int) extends AnyVal
	final case class PixelFormat(value: String) extends AnyVal

	final case class AudioAttr(codec: CodecName = CodecName(""),
			                       bitRate: BitRate = BitRate(0),
	                           samplingRate: SamplingRate = SamplingRate(0),
	                           channels: Channels = Channels(0),
	                           volume: Volume = Volume(0),
	                           quality: Quality = Quality(0)) {

		def toJaveAudioAttr: AudioAttributes = {
			val audio: AudioAttributes = new AudioAttributes()
			NonEmptyString(codec.value).map{a => audio.setCodec(a)}
			NonZeroInt(bitRate.value).map(a => audio.setBitRate(a))
			NonZeroInt(samplingRate.value).map(a => audio.setSamplingRate(a))
			NonZeroInt(channels.value).map(a => audio.setChannels(a))
			NonZeroInt(volume.value).map(a => audio.setVolume(a))
			NonZeroInt(quality.value).map(a => audio.setQuality(a))
			audio
		}
	}


	final case class VideoAttr(size: VideoSize,
	                           codec: CodecName = CodecName(""),
	                           bitRate: BitRate = BitRate(0),
	                           frameRate: FrameRate = FrameRate(0)) {

		def toJaveVideoAttr: VideoAttributes = {
			val video: VideoAttributes = new VideoAttributes()
			NonEmptyString(codec.value).map(v => video.setCodec(v))
			NonZeroInt(bitRate.value).map(v => video.setBitRate(v))
			NonZeroInt(frameRate.value).map(v => video.setFrameRate(v))
			video.setSize(size)
			video
		}
	}

}

abstract class Codec {
	import spray.json.JsObject

	val audio: AudioAttributes = new AudioAttributes()
	val video: VideoAttributes = new VideoAttributes()

	val codecName: String
	def audioAttrs(): AudioAttributes
	def videoAttrs(): VideoAttributes
	def toJson: JsObject
}