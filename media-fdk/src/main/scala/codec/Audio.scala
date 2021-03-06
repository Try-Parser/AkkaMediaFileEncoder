package media.fdk.codec

import ws.schild.jave.info.AudioInfo

import media.fdk.codec.Codec.{
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
) extends utils.traits.CborSerializable


object Audio {
	def apply(info: AudioInfo): Option[Audio] = Option(info).map {  v => 
		Audio(
			BitRate(info.getBitRate()), 
			Channels(info.getChannels()),
			CodecName(info.getDecoder()),
			SamplingRate(info.getSamplingRate()),
			Quality(0), Volume(0))
	}
}