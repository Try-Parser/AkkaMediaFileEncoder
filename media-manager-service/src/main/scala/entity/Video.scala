package media.service.entity

import ws.schild.jave.info.VideoSize
import ws.schild.jave.encode.enums.X264_PROFILE

import media.service.entity.Codec.{
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