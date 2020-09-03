package media.service.entity

import java.util.UUID

import scala.collection.mutable.ListBuffer

import akka.http.scaladsl.model.ContentType

import ws.schild.jave.info.{ MultimediaInfo, VideoSize }

import spray.json.{
	JsNumber,
	JsObject,
	JsString,
	DefaultJsonProtocol,
	RootJsonFormat,
	DeserializationException,
	JsValue
}

import media.service.models.FileActor.FileUpload

import media.service.handlers.FileHandler.ContentTypeData

import media.service.entity.Codec.{
	CodecName,
	BitRate,
	FrameRate,
	Format,
	Tag,
	Duration,
	Channels,
	Quality,
	SamplingRate,
	Volume
}

final case class Media(mmi: MultimediaInfo, file: FileUpload) {
	def toJson: JsObject = MediaConvert.Media.Implicits.write(this).asJsObject
}

final case class Audio(
	bitRate: BitRate, 
	channels: Channels, 
	codec: CodecName, 
	samplingRate: SamplingRate,
	quality: Quality,
	volume: Volume)

final case class Video(
	bitRate: BitRate, 
	frameRate: FrameRate, 
	codec: CodecName, 
	size: VideoSize,
	tag: Tag)

final case class MediaConvert(
	file: FileUpload, 
	duration: Duration, 
	video: Option[Video], 
	audio: Option[Audio],
	format: Format) {
		def toJson: JsObject = MediaConvert.Implicits.write(this).asJsObject
}

object MediaConvert extends DefaultJsonProtocol {
	object Media {
		implicit object Implicits {
			def write(media: Media): JsObject = {
				val audio = Option(media.mmi.getAudio()) match {
					case Some(audio) => JsObject(
						"decoder" -> JsString(audio.getDecoder()),
						"sampling_rate" -> JsNumber(audio.getSamplingRate()),
						"channels" -> JsNumber(audio.getChannels()),
						"bit_rate" -> JsNumber(audio.getBitRate()))
					case None => JsString("")
				}

				val video = Option(media.mmi.getVideo()) match {
					case Some(video) => JsObject(
						"decoder" -> JsString(video.getDecoder()),
						"size" -> JsObject(
							"width" -> JsNumber(video.getSize().getWidth()),
							"height" -> JsNumber(video.getSize().getHeight())),
						"bit_rate" -> JsNumber(video.getBitRate()),
						"frame_rate" -> JsNumber(video.getFrameRate()))
					case None => JsString("")
				}

				JsObject(
					"file_uploaded" -> media.file.toJson,
					"format" -> JsString(media.mmi.getFormat()),
					"duration" -> JsNumber(media.mmi.getDuration()),
					"video" -> video,
					"audio" -> audio)
		}}
	}

	implicit object Implicits extends utils.json.ExceptionHandler with RootJsonFormat[MediaConvert] {

		def write(m: MediaConvert): JsObject = JsObject(
			"file_uploaded" -> m.file.toJson,
			"duration" -> JsNumber(m.duration.value),
			"format" -> JsString(m.format.value),
			"video" -> m.video.map { v =>
				JsObject(
					"bit_rate" -> JsNumber(v.bitRate.value),
					"frame_rate" -> JsNumber(v.frameRate.value),
					"codec" -> JsString(v.codec.value),
					"size" -> JsObject(
						"width" -> JsNumber(v.size.getWidth()),
						"height" -> JsNumber(v.size.getHeight())),
					"tag" -> JsString(v.tag.value)
				)}.getOrElse(JsString("")),
			"audio" -> m.audio.map { a => 
				JsObject(
					"bit_rate" -> JsNumber(a.bitRate.value),
					"channels" -> JsNumber(a.channels.value),
					"codec" -> JsString(a.codec.value),
					"sampling_rate" -> JsNumber(a.samplingRate.value),
					"quality" -> JsNumber(a.quality.value),
					"volume" -> JsNumber(a.volume.value)
				)}.getOrElse(JsString("")))

		def read(js: JsValue) = js.asJsObject.getFields(
				"file_uploaded", 
				"duration", 
				"format", 
				"audio",
				"video") match {
					case Seq(JsObject(file_uploaded), 
						JsNumber(duration), 
						JsString(format), 
						JsObject(audio),
						JsObject(video)) => MediaConvert(readFileUpload(file_uploaded), 
							Duration(duration.toInt), 
							readVideo(video), 
							readAudio(audio), 
							Format(format))
					case Seq(JsObject(file_uploaded), 
						JsNumber(duration),
						JsString(format), 
						JsObject(audio)) => MediaConvert(readFileUpload(file_uploaded),
							Duration(duration.toInt),
							None,
							readAudio(audio),
							Format(format))
					case Seq(JsObject(file_uploaded), 
						JsNumber(duration),
						JsString(format), 
						JsObject(video)) => MediaConvert(readFileUpload(file_uploaded),
							Duration(duration.toInt),
							readVideo(video),
							None,
							Format(format))
					case _ => throw new DeserializationException("Invalid Media config")}

		private def readVideo(js: Map[String, JsValue]): Option[Video] = {
			val errorFields = new ListBuffer[String]()

			val frameRate: FrameRate = JsExtract[FrameRate](
				js, "frame_rate", FrameRate(0.0),
				{ case JsNumber(value) => FrameRate(value.toDouble)
				  case _ => errorFields += "frame_rate"; FrameRate(0.0) },
				(f) => errorFields += f)

			val bitRate: BitRate = JsExtract[BitRate](
				js, "bit_rate", BitRate(0), 
				{ case JsNumber(value) => BitRate(value.toInt)
				  case _ => errorFields += "bit_rate"; BitRate(0) }, 
				(f) => errorFields += f)

			val codec: CodecName = JsExtract[CodecName](
				js, "decoder", CodecName(""),
				{ case JsString(value) => CodecName(value.toString)
				  case _ => errorFields += "decoder"; CodecName("") },
				(f) => errorFields += f)

			val tag: Tag = JsExtractOption[Tag](
				js, "tag", Tag(""),
				{ case JsString(value) => Tag(value.toString)
				  case _ => Tag("") })

			val size: VideoSize = JsExtract[VideoSize](
				js, "size", new VideoSize(0, 0), 
				{ case obj: JsValue => obj.asJsObject.fields match {
					case sobj: Map[String, JsValue] => 
						val h: Int = JsExtract[Int](
							sobj, "height", 0, 
							{ case JsNumber(h) => h.toInt 
							  case _ => errorFields += "size.height"; 0 }, 
							(f) => errorFields += f)

						val w: Int = JsExtract[Int](
							sobj, "width", 0,
							{ case JsNumber(w) => w.toInt 
							  case _ => errorFields += "size.width"; 0 }, 
							(f) => errorFields += f)
						
						new VideoSize(w, h)}}, 
				(f) => errorFields += f)

			
			extractor[Option[Video]](
				errorFields.toList, 
				Some(Video(bitRate, frameRate, codec, size, tag)))
		}

		private def readAudio(js: Map[String, JsValue]): Option[Audio] = {
			val errorFields = new ListBuffer[String]()

			val bitRate: BitRate = JsExtract[BitRate](
				js, "bit_rate", BitRate(0),
				{ case JsNumber(value) => BitRate(value.toInt)
				  case _ => errorFields += ""; BitRate(0) },
				(f) => errorFields += f)

			val channels: Channels = JsExtract[Channels](
				js, "channels", Channels(0),
				{ case JsNumber(value) => Channels(value.toInt)
				  case _ => errorFields += ""; Channels(0) },
				(f) => errorFields += f)

			val codec: CodecName = JsExtract[CodecName](
				js, "decoder", CodecName(""),
				{ case JsString(value) => CodecName(value.toString)
				  case _ => errorFields += ""; CodecName("") },
				(f) => errorFields += f)

			val samplingRate: SamplingRate = JsExtract[SamplingRate](
				js, "sampling_rate", SamplingRate(0),
				{ case JsNumber(value) => SamplingRate(value.toInt) 
				  case _ => errorFields += ""; SamplingRate(0) },
				(f) => errorFields += f)

			val quality: Quality = JsExtractOption[Quality](
				js, "quality", Quality(0), 
				{ case JsNumber(value) => Quality(value.toInt)
				  case _ => errorFields += ""; Quality(0) })

			val volume: Volume = JsExtractOption[Volume](
				js, "volume", Volume(0),
				{ case JsNumber(value) => Volume(value.toInt)
				  case _ => errorFields += ""; Volume(0) })

			extractor[Option[Audio]](errorFields.toList, Some(Audio(
				bitRate, channels, codec, samplingRate, quality, volume)))
		}

		private def readFileUpload(js: Map[String, JsValue]): FileUpload = {
			val errorFields = new ListBuffer[String]()

			val file_name: String = JsExtract[String](
				js, "file_name", "",
				{ case JsString(value) => value.toString 
				  case _ => errorFields += "file_name"; "" },
				(f) => errorFields += f)

			val extension: String = JsExtract[String](
				js, "extension", "",
				{ case JsString(value) => value.toString 
				  case _ => errorFields += "extension"; "" },
				(f) => errorFields += f)

			val id: UUID = JsExtract[UUID](
				js, "id", UUID.randomUUID, 
				{ case JsString(value) => parseId(value.toString) match {
					case Some(uid) => uid
					case None => errorFields += "id"; UUID.randomUUID }
				  case _ => errorFields += "id"; UUID.randomUUID },
				(f) => errorFields += f)

			val content_type: ContentTypeData = JsExtract[ContentTypeData](
				js, "content_type", ContentTypeData("mp3"), 
				{ case JsString(value) => ContentType.parse(value) match {
					case Right(ct) => ContentTypeData(value)
					case Left(errors) => errorFields += "content_type"; ContentTypeData("") }
				  case _ => errorFields += "content_type"; ContentTypeData("") },
				(f) => errorFields += f)

			extractor[FileUpload](
				errorFields.toList,
				FileUpload(file_name, extension, content_type, id))
		}

		private def parseId(id: String): Option[UUID] = try {
			Some(UUID.fromString(id))
		} catch {
			case _ : Throwable => None
		}
	}
}