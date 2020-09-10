package media.service.entity

import media.service.entity.Codec.{
	CodecName,
	BitRate,
	Channels,
	Quality,
	SamplingRate,
	Volume
}

final case class Audio(
	bitRate: BitRate, 
	channels: Channels, 
	codec: CodecName, 
	samplingRate: SamplingRate,
	quality: Quality,
	volume: Volume
)