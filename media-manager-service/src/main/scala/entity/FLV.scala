package entity

import media.service.entity.Codec
import media.service.entity.Codec._
import spray.json.{ DefaultJsonProtocol, DeserializationException, JsNumber, JsObject, JsString, JsValue, RootJsonFormat }
import ws.schild.jave.encode.{ AudioAttributes, VideoAttributes }
import ws.schild.jave.info.VideoSize

final case class FLV(
		bitRate: BitRate = BitRate(64000),
		samplingRate: SamplingRate = SamplingRate(22050),
		channels: Channels = Channels(1)) extends Codec {

  val codec = CodecName("flv")
  override val codecName: String = codec.value

  override def audioAttrs(): AudioAttributes = Codec.AudioAttr().copy(
    codec,
    bitRate,
    samplingRate,
    channels
  ).toJaveAudioAttr

  override def videoAttrs(): VideoAttributes = Codec.VideoAttr(
    new VideoSize(400, 300),
    codec,
    BitRate(160000),
    FrameRate(15)
  ).toJaveVideoAttr

  override def toJson: JsObject = FLV.Implicits.write(this).asJsObject

}

object FLV extends DefaultJsonProtocol {

  implicit object Implicits extends RootJsonFormat[FLV] {
    override def write(file: FLV): JsValue = JsObject(
      "audio" -> JsObject(
        "codec" -> JsString(file.codecName),
        "bit_rate" -> JsNumber(file.bitRate.value),
        "sampling_rate" -> JsNumber(file.samplingRate.value),
        "channels" -> JsNumber(file.channels.value)
      ),
      "extn" -> JsString(file.codecName)
    )

    override def read(json: JsValue): FLV =
      json.asJsObject.getFields("bit_rate", "sampling_rate", "channels") match {
        case Seq(JsNumber(bitRate), JsNumber(samplingRate), JsNumber(channels)) =>
          FLV(BitRate(bitRate.toInt), SamplingRate(samplingRate.toInt), Channels(channels.toInt))
        case _ => throw DeserializationException("Invalid JSON Object")
      }
  }

}
