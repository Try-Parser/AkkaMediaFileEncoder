package media.fdk.json

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
import media.fdk.codec.{
	Audio,
	Video
}
import media.fdk.codec.Codec._
import media.fdk.json.MediaInfo

final case class MultiMedia(
	info: MediaInfo, 
	duration: Duration, 
	format: Format) {
		def toJson: JsObject = MultiMedia.Implicits.write(this).asJsObject
}

object MultiMedia extends DefaultJsonProtocol {
	import scala.collection.mutable.ListBuffer
	import utils.implicits.JsExtraction._

	implicit object Implicits extends utils.json.JsHandler with RootJsonFormat[MultiMedia] {
		def write(mm: MultiMedia): JsObject = JsObject(
			"info" -> mm.info.toJson,
			"duration" -> JsNumber(mm.duration.value),
			"video" -> mm.info.video.map { info => JsObject(
				"bit_rate" -> JsNumber(info.bitRate.value),
				"frame_rate" -> JsNumber(info.frameRate.value),
				"codec" -> JsString(info.codec.value),
				"size" -> JsObject(
					"width" -> JsNumber(info.size.getWidth()),
					"height" -> JsNumber(info.size.getHeight())),
				"tag" -> JsString(info.tag.value)
			)}.getOrElse(JsString("")),
			"audio" -> mm.info.audio.map { info => JsObject(
				"bit_rate" -> JsNumber(info.bitRate.value),
				"channels" -> JsNumber(info.channels.value),
				"codec" -> JsString(info.codec.value),
				"sampling_rate" -> JsNumber(info.samplingRate.value),
				"quality" -> JsNumber(info.quality.value),
				"volume" -> JsNumber(info.volume.value)
			)}.getOrElse(JsString("")),
			"format" -> JsString(mm.format.value)
		)

		def read(js: JsValue) = js
			.asJsObject
			.getFields("info", "duration", "format", "audio", "video") match {
				case Seq(JsObject(info), 
					JsNumber(duration), 
					JsString(format), 
					JsObject(audio), 
					JsObject(video)) => MultiMedia(
						readFileInfo(info, readAudio(audio), readVideo(video)),
						Duration(duration.toInt),
						Format(format))
				case Seq(JsObject(info), 
					JsNumber(duration), 
					JsString(format), 
					JsObject(audio)) => MultiMedia(
						readFileInfo(info, readAudio(audio), None),
						Duration(duration.toInt),
						Format(format))
			}

		private def readFileInfo(
			js: Map[String, JsValue], 
			audio: Option[Audio],
			video: Option[Video]): MediaInfo = {
				import java.util.UUID
				import utils.implicits.Primitive.GuardString
				import utils.file.{ ContentType, HttpContentType }

				val errorFields = new ListBuffer[String]()

				val file_name: String = js.extract[String]("file_name", "")({
					case JsString(value) => value.toString
					case _ => 
						errorFields += "file_name"
						""
				}, f => errorFields += f)

				val id: UUID = js.extractNonRequired[UUID]("id", UUID.randomUUID)({
					case JsString(value) => value.toString.parseUUID match {
						case Some(uuid) => uuid
						case None => 
							errorFields += "id"
							UUID.randomUUID 
					}
					case _ =>
						errorFields += "id"
						UUID.randomUUID 
				})

				val content_type: HttpContentType = 
					js.extract[HttpContentType]("content_type", ContentType(""))({
						case JsString(value) => try ContentType(value.toString)
							catch { case _: Throwable => 
								errorFields += "content_type"
								ContentType("")
							} 
						case _ => 
							errorFields += "content_type"
							ContentType("")
					}, f => errorFields += f)

				extractor[MediaInfo](
					errorFields.toList,
					MediaInfo(file_name, video, audio, content_type, 0, id))
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
}}
