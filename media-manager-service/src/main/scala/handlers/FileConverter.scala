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
			attrs.setAudioAttributes(codec.audioAttrs())
			if(va) 
				attrs.setVideoAttributes(codec.videoAttrs())

			val encoder: Encoder = new Encoder()

			encoder.encode(source, target, attrs)

			println("Encoding formats 000000000000000000000000000000000000000000")
			encoder.getSupportedEncodingFormats().map(println)
			println("Decoding formats 000000000000000000000000000000000000000000")
			encoder.getSupportedDecodingFormats().map(println)
			println("Audio Decoders 00000000000000000000000000000000000000000000")
			encoder.getAudioDecoders().map(println)
			println("Audio Encoders 00000000000000000000000000000000000000000000")
			encoder.getAudioEncoders().map(println)
			println("Video Encoders 00000000000000000000000000000000000000000000")
			encoder.getVideoEncoders().map(println)
			println("Video Decoders 00000000000000000000000000000000000000000000")
			encoder.getVideoDecoders().map(println)

			codec.codecName
	} 
}