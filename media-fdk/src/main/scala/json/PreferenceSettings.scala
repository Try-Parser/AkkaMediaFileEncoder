package media.fdk.json


import java.util.UUID

import spray.json.{
	JsString,
	JsObject,
	JsNumber,
	DefaultJsonProtocol,
	RootJsonFormat,
	JsValue
}

import ws.schild.jave.encode.enums.X264_PROFILE
import ws.schild.jave.info.VideoSize

import media.fdk.codec.{ Audio, Video }
import media.fdk.codec.Codec._
import media.fdk.json.MultiMedia

final case class PreferenceSettings(
	id: UUID,
	audio: Option[Audio], 
	video: Option[Video],
	extension: String,
	format: Format,
	fileName: String) {

	def updateFileName(name: String) = PreferenceSettings(id, audio, video, extension, format, name)

	def toJson: JsObject = PreferenceSettings.Implicits.write(this).asJsObject
}


object PreferenceSettings extends DefaultJsonProtocol {
	import scala.collection.mutable.ListBuffer
	import utils.implicits.JsExtraction._

	def getExtension(name: String): String = {
		val sp = name.split("\\.")
		sp(sp.size-1)
	}

	def apply(multiMedia: MultiMedia): PreferenceSettings = 
		PreferenceSettings(
			multiMedia.info.fileId,
			multiMedia.info.audio,
			multiMedia.info.video,
			getExtension(multiMedia.info.fileName),
			multiMedia.format,
			multiMedia.info.fileName)

	implicit object Implicits extends utils.json.JsHandler with RootJsonFormat[PreferenceSettings] {
		def write(settings: PreferenceSettings): JsObject = 
			(settings.audio, settings.video) match {
				case (Some(a), Some(v)) => JsObject(
					"id" -> JsString(settings.id.toString),
					"video" -> JsObject(
						"bit_rate" -> JsNumber(v.bitRate.value),
						"frame_rate" -> JsNumber(v.frameRate.value),
						"decoder" -> JsString(v.codec.value),
						"tag" -> JsString(v.tag.value),
						"profile" -> JsString(v.profile.toString),
						"size" -> JsObject(
							"width" -> JsNumber(v.size.getWidth()),
							"height" -> JsNumber(v.size.getHeight()))),
					"audio" -> JsObject(
						"bit_rate" -> JsNumber(a.bitRate.value),
						"channels" -> JsNumber(a.channels.value),
						"decoder" -> JsString(a.codec.value),
						"volume" -> JsNumber(a.volume.value),
						"quality" -> JsNumber(a.quality.value),
						"sampling_rate" -> JsNumber(a.samplingRate.value)),
					"format" -> JsString(settings.format.value),
					"extension" -> JsString(settings.extension))
				case (Some(a), None) => JsObject(
					"id" -> JsString(settings.id.toString),
					"audio" -> JsObject(
						"bit_rate" -> JsNumber(a.bitRate.value),
						"channels" -> JsNumber(a.channels.value),
						"decoder" -> JsString(a.codec.value),
						"volume" -> JsNumber(a.volume.value),
						"quality" -> JsNumber(a.quality.value),
						"sampling_rate" -> JsNumber(a.samplingRate.value)),
					"format" -> JsString(settings.format.value),
					"extension" -> JsString(settings.extension))
				case (None, Some(v)) => JsObject(
					"id" -> JsString(settings.id.toString),
					"video" -> JsObject(
						"bit_rate" -> JsNumber(v.bitRate.value),
						"frame_rate" -> JsNumber(v.frameRate.value),
						"decoder" -> JsString(v.codec.value),
						"tag" -> JsString(v.tag.value),
						"profile" -> JsString(v.profile.toString),
						"size" -> JsObject(
							"width" -> JsNumber(v.size.getWidth()),
							"height" -> JsNumber(v.size.getHeight()))),
					"format" -> JsString(settings.format.value),
					"extension" -> JsString(settings.extension))
				case (None, None) => JsObject(
					"id" -> JsString(settings.id.toString),
					"reason" -> JsString("Non-media file."))
			}

		def read(js: JsValue) = js
			.asJsObject
			.getFields("id", "extension", "format", "audio", "video") match {
				case Seq(JsString(id), 
					JsString(extension), 
					JsString(format), 
					JsObject(audio), 
					JsObject(video)) => PreferenceSettings(
						UUID.fromString(id),
						readAudio(audio),
						readVideo(video),
						extension,
						Format(format), "")
				case Seq(JsString(id), 
					JsString(extension),
					JsString(format), 
					JsObject(audio)) => PreferenceSettings(
						UUID.fromString(id),
						readAudio(audio),
						None,
						extension,
						Format(format), "")
			}

		private def readAudio(js: Map[String, JsValue]): Option[Audio] = {
			val errorFields = new ListBuffer[String]()
			val (bitRate, codec, errFields) = common(js) 

			val channels: Channels = js.extract[Channels]("channels", Channels(0))({
				case JsNumber(value) => Channels(value.toInt)
				case _ => 
					errorFields += "channels"
					Channels(0)
			}, f => errorFields += f)

			val samplingRate: SamplingRate = js.extract[SamplingRate]("sampling_rate", SamplingRate(0))({
				case JsNumber(value) => SamplingRate(value.toInt)
				case _ => 
					errorFields += "sampling_rate"
					SamplingRate(0)
			}, f => errorFields += f)

			
			val quality: Quality = js.extractNonRequired[Quality]("quality", Quality(0))({
				case JsNumber(value) => Quality(value.toInt)
				case _ => 
					errorFields += "quality"
					Quality(0)
			})

			val volume: Volume = js.extractNonRequired[Volume]("volume", Volume(0))({
				case JsNumber(value) => Volume(value.toInt)
				case _ =>
					errorFields += "volume"
					Volume(0)
			})

			extractor[Option[Audio]](
				errorFields.toList ++ errFields, 
				Option(Audio(bitRate, channels, codec, samplingRate, quality, volume)))
		}

		private def readVideo(js: Map[String, JsValue]): Option[Video] = {
			val errorFields = new ListBuffer[String]()

			val (bitRate, codec, errFields) = common(js)

			val frameRate: FrameRate = js.extract[FrameRate]("frame_rate", FrameRate(0.0))({
				case JsNumber(value) => FrameRate(value.toDouble)
				case _ => 
					errorFields += "frame_rate"
					FrameRate(0.0)
			}, f => errorFields += f)

			val tag: Tag = js.extractNonRequired[Tag]("tag", Tag(""))({
				case JsString(value) => Tag(value.toString)
				case _ => Tag("")
			})

			val profile: X264_PROFILE = js.extractNonRequired[X264_PROFILE]("profile", X264_PROFILE.BASELINE)({
				case JsString(value) => X264_PROFILE.valueOf(value.toUpperCase.toString)
				case _ => X264_PROFILE.BASELINE 
			})

			val size: VideoSize = js.extract[VideoSize]("size", new VideoSize(0, 0))({
				case sizeObj: JsValue => sizeObj.asJsObject.fields match {
					case obj: Map[String, JsValue] =>
						new VideoSize(obj.extract[Int]("height", 0)({
							case JsNumber(h) => h.toInt
							case _ => 
								errorFields += "size.height"
								0
						}, f => errorFields += f),
						obj.extract[Int]("width", 0)({
							case JsNumber(w) => w.toInt
							case _ =>
								errorFields += "size.width"
								0
						}, f => errorFields += f))
				}
			}, f => errorFields += f)

			extractor[Option[Video]](
				errorFields.toList ++ errFields, 
				Some(Video(bitRate, frameRate, codec, size, tag, profile)))
		}

		private def common(js: Map[String, JsValue]): (BitRate, CodecName, List[String]) = {
			val errorFields = new ListBuffer[String]()

			val bitRate: BitRate = js.extract[BitRate]("bit_rate", BitRate(0))({
				case JsNumber(value) => BitRate(value.toInt)
				case _ => 
					errorFields += "bit_rate"
					BitRate(0)
			}, f => errorFields += f)

			val codec: CodecName = js.extract[CodecName]("decoder", CodecName(""))({
				case JsString(value) => CodecName(value.toString)
				case _ => 
					errorFields += "decoder"
					CodecName("")
			}, f => errorFields += f)

			(bitRate, codec, errorFields.toList)
		}

	}
}