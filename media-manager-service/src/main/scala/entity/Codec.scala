package media.service.entity

object Codec {
	final case class CodecName(value: String) extends AnyVal
	final case class Tag(value: String) extends AnyVal
	final case class BitRate(value: Int) extends AnyVal
	final case class SamplingRate(value: Int) extends AnyVal
	final case class Channels(value: Int) extends AnyVal
	final case class Volume(value: Int) extends AnyVal
	final case class Quality(value: Int) extends AnyVal
	final case class FrameRate(value: Double) extends AnyVal
	final case class PixelFormat(value: String) extends AnyVal
	final case class Duration(value: Int) extends AnyVal
	final case class Format(value: String) extends AnyVal
}

