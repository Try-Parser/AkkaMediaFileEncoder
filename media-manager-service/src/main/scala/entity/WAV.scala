package entity

import media.service.entity.Codec
import media.service.entity.Codec.CodecName
import spray.json.{DefaultJsonProtocol, DeserializationException, JsObject, JsString, JsValue, RootJsonFormat}
import ws.schild.jave.encode.{AudioAttributes, VideoAttributes}

final case class WAV() extends Codec {

  val codec = CodecName("pcm_s16le")

  override val codecName: String = codec.value

  override def audioAttrs(): AudioAttributes = Codec.AudioAttr().copy(
    codec
  ).toJaveAudioAttr

  override def videoAttrs(): VideoAttributes = new VideoAttributes()

  override def toJson: JsObject = WAV.Implicits.write(this).asJsObject
}

object WAV extends DefaultJsonProtocol {

  implicit object Implicits extends RootJsonFormat[WAV] {
    override def write(file: WAV): JsValue = JsObject(
      "audio" -> JsObject(
        "codec" -> JsString(file.codecName)
      ),
      "extn" -> JsString("wav")
    )

    override def read(json: JsValue): WAV = {
      json.asJsObject.getFields() match {
        case Seq() => WAV()
        case _ => throw DeserializationException("Invalid JSON Object")
      }
    }
  }

}