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
	profile: X264_PROFILE = X264_PROFILE.BASELINE
)

object Video {
	def apply(info: VideoInfo): Video =
		Video(
			BitRate(info.getBitRate()),
			FrameRate(info.getFrameRate()),
			CodecName(info.getDecoder()),
			info.getSize(),
			Tag(""))
}