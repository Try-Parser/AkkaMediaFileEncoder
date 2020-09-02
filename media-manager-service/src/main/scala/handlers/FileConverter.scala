package media.service.handlers

import ws.schild.jave.{ Encoder, MultimediaObject }
import ws.schild.jave.encode.EncodingAttributes 

import media.service.entity.Codec

private[service] object FileConverter {
	def convert(
		codec: Codec, 
		source: MultimediaObject,
		target: java.io.File,
		va: Boolean = false): String = {
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