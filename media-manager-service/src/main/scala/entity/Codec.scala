package media.service.entity

import ws.schild.jave.encode.{
	VideoAttributes,
	AudioAttributes
}

abstract class Codec(name: String) {
	import spray.json.JsObject

	val audio: AudioAttributes = new AudioAttributes()
	val video: VideoAttributes = new VideoAttributes()

	val codecName: String = name

	def audioAttrs(): AudioAttributes
	def videoAttrs(): VideoAttributes
	def toJson(): JsObject
}