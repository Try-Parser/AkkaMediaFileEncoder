package media.fdk.codec

import ws.schild.jave.info.{ VideoSize, VideoInfo }
import ws.schild.jave.encode.enums.X264_PROFILE

import media.fdk.codec.Codec.{
	CodecName,
	BitRate,
	FrameRate,
	Tag
}

final case class Video(
	bitRate: BitRate, 
	frameRate: FrameRate, 
	codec: CodecName, 
	size: VideoSize,
	tag: Tag,
	profile: Option[String]
) extends utils.traits.CborSerializable

object Video {
	def apply(info: VideoInfo): Option[Video] = Option(info).map { v => 
		Video(
			BitRate(v.getBitRate()),
			FrameRate(v.getFrameRate()),
			CodecName(v.getDecoder()),
			v.getSize(),
			Tag(""),
			None)
	}
}