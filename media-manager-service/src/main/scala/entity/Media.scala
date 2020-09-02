package media.service.entity

import akka.http.scaladsl.model.ContentType

import ws.schild.jave.info.{ MultimediaInfo, VideoSize }

import spray.json.{
	JsNumber,
	JsObject,
	JsString,
	DefaultJsonProtocol,
	RootJsonFormat,
	DeserializationException,
	JsValue,
	JsArray
}

import media.service.models.FileActor.FileUpload

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

	implicit object Implicits extends RootJsonFormat[MediaConvert] {
		def write(m: MediaConvert): JsObject = JsObject(
			"file_uploaded" -> m.toJson,
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

		def read(js: JsValue) = {
			js.asJsObject.getFields(
				"file_uploaded", 
				"duration", 
				"format", 
				"video", 
				"audio") match {
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
					case _ => throw new DeserializationException("Invalid Media config")
				}
		}

		private def readVideo(js: Map[String, JsValue]): Option[Video] = js match {
			case Seq(JsNumber(bit_rate), 
				JsNumber(frame_rate), 
				JsString(codec), 
				JsObject(size),
				JsString(tag)) => someVideo(bit_rate.toInt, frame_rate.toInt, codec, size, tag)
			case Seq(JsNumber(bit_rate), 
				JsNumber(frame_rate),
				JsString(codec), 
				JsObject(size)) => someVideo(bit_rate.toInt, frame_rate.toInt, codec, size, "")
			case _ => throw new DeserializationException("Invalid video config")
		}

		private def someVideo(
			bitRate: Int, 
			frameRate: Int,
			codec: String,
			size: Map[String, JsValue],
			tag: String): Option[Video] = Some(Video(
				BitRate(bitRate),
				FrameRate(frameRate),
				CodecName(codec),
				size match {
					case Seq(JsNumber(h), JsNumber(w)) => new VideoSize(w.toInt, h.toInt)
					case _ => throw new DeserializationException("Invalid size config")
				},
				Tag(tag)))

		private def readAudio(js: Map[String, JsValue]): Option[Audio] = js match {
			case Seq(JsNumber(bit_rate), 
				JsNumber(channels), 
				JsString(codec), 
				JsNumber(sampling_rate), 
				JsNumber(quality), 
				JsNumber(volume)) => 
					someAudio(bit_rate.toInt,
						channels.toInt,
						codec,
						sampling_rate.toInt,
						quality.toInt,
						volume.toInt)
			case Seq(JsNumber(bit_rate), JsNumber(channels), 
				JsString(codec), JsString(sampling_rate)) =>
					someAudio(bit_rate.toInt, channels.toInt, codec, sampling_rate.toInt, 0, 0)
			case _ => throw new DeserializationException("Invalid audio config")}

		private def someAudio(
			bitRate: Int, 
			channels: Int, 
			codec: String, 
			samplingRate: Int, 
			quality: Int, 
			volume: Int): Option[Audio] = Some(Audio(
				BitRate(bitRate),
				Channels(channels),
				CodecName(codec),
				SamplingRate(samplingRate),
				Quality(quality),
				Volume(volume)))

		private def readFileUpload(js: Map[String, JsValue]): FileUpload = js match {
			case Seq(JsString(file_name), 
				JsString(extension), 
				JsString(content_type)) => ContentType.parse(content_type) match {
					case Right(contentType) => FileUpload(
						file_name, 
						extension, 
						media.service.handlers.FileHandler.ContentTypeData(content_type))
					case Left(errors) => throw new DeserializationException(
						JsArray(errors.map { error =>
							JsObject(
								"summary" -> JsString(error.summary), 
								"detail" -> JsString(error.detail),
								"header" -> JsString(error.errorHeaderName))
					}.toVector).toString)}
			case _ => throw new DeserializationException("Invalid file_upload config")
		}
	}
}