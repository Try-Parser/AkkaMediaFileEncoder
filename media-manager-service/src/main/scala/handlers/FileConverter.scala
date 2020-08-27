package media.service.handler

import java.io.File

import ws.schild.jave.{ Encoder, MultimediaObject }
import ws.schild.jave.encode.{ 
	AudioAttributes, 
	VideoAttributes, 
	EncodingAttributes 
}
import ws.schild.jave.info.VideoSize
import ws.schild.jave.encode.enums.X264_PROFILE
	
abstract class Codec(name: String) {
	val codecName: String = name

	def audio(): AudioAttributes
	def video(): VideoAttributes
}

private[service] object FileConverter {
	final case class Mp3() extends Codec("mp3") {
		override def audio(): AudioAttributes = {
			val audio: AudioAttributes = new AudioAttributes()
			audio.setCodec("libmp3lame")
			audio.setBitRate(128000)
			audio.setChannels(2)
			audio.setSamplingRate(44100)
			audio
		}

		override def video(): VideoAttributes = {
			val video: VideoAttributes = new VideoAttributes()
			video.setCodec(VideoAttributes.DIRECT_STREAM_COPY)
			video
		}
	}

	final case class Mp4() extends Codec("mp4") {
		override def audio(): AudioAttributes = {
			val audio: AudioAttributes = new AudioAttributes()
			audio.setCodec("aac");
			// here 64kbit/s is 64000
			audio.setBitRate(64000);
			audio.setChannels(2);
			audio.setSamplingRate(44100);
			audio
		}

		override def video(): VideoAttributes = {
			val video: VideoAttributes = new VideoAttributes()
			video.setCodec("h264");
			video.setX264Profile(X264_PROFILE.BASELINE);
			// Here 160 kbps video is 160000
			video.setBitRate(160000);
			// More the frames more quality and size, but keep it low based on devices like mobile
			video.setFrameRate(15);
			video.setSize(new VideoSize(400, 300));
			video
		}
	}

	def convert(
		codec: Codec, 
		source: MultimediaObject,
		target: File,
		va: Boolean = false
	): String = {

		val attrs: EncodingAttributes = new EncodingAttributes()
		attrs.setOutputFormat(codec.codecName)
		attrs.setAudioAttributes(codec.audio)
		if(va) 
			attrs.setVideoAttributes(codec.video)

		val encoder: Encoder = new Encoder()
		encoder.encode(source, target, attrs)
		codec.codecName
	} 
}