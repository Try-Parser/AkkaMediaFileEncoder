package utils.file

import akka.http.scaladsl.model.{ ContentType => CT }

import com.fasterxml.jackson.core.JsonGenerator
import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.databind.DeserializationContext
import com.fasterxml.jackson.databind.SerializerProvider
import com.fasterxml.jackson.databind.annotation.JsonDeserialize
import com.fasterxml.jackson.databind.annotation.JsonSerialize
import com.fasterxml.jackson.databind.deser.std.StdDeserializer
import com.fasterxml.jackson.databind.ser.std.StdSerializer

@JsonSerialize(using = classOf[ContentTypeSerialization])
@JsonDeserialize(using = classOf[ContentTypeDeserialization])
case class HttpContentType(get: CT)

case object ContentType {
	def getMp3: HttpContentType = apply("audio/mpeg")
	def apply(ct: HttpContentType): HttpContentType = ct
	def apply(ctString: String): HttpContentType =
		CT.parse(ctString).fold(
			err => throw new Exception(s"ContenType : $ctString is not valid ContentType."),
			ct => HttpContentType(ct))
}

class ContentTypeSerialization extends StdSerializer[HttpContentType](classOf[HttpContentType]) {
	override def serialize(value: HttpContentType, gen: JsonGenerator, provider: SerializerProvider): Unit = 
		gen.writeString(value.get.toString)
}

class ContentTypeDeserialization extends StdDeserializer[HttpContentType](classOf[HttpContentType]) {
	override def deserialize(
		p: JsonParser, 
		ctxt: DeserializationContext): HttpContentType = ContentType(p.getText)
}