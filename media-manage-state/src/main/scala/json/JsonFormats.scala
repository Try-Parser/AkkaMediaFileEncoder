package media.state.json

import java.util.UUID

import media.state.models.FileActorModel
import media.state.models.FileActorModel.File
import media.state.routes.FileActorRoutes
import spray.json.{DefaultJsonProtocol, DeserializationException, JsNumber, JsObject, JsString, JsValue, RootJsonFormat}

object JsonFormats extends DefaultJsonProtocol {

  implicit object Implicits extends RootJsonFormat[FileActorModel.File] {
    override def write(file: File): JsValue = JsObject(
      "file_name" -> JsString(file.fileName),
      "file_data" -> JsString(file.fileData),
      "desc" -> JsString(file.description),
      "media_info" -> JsString(file.mediaInfo),
      "status" -> JsNumber(file.status),
      "file_id" -> JsString(file.fileId.toString)
    )

    override def read(json: JsValue): File =
      json.asJsObject.getFields(
        "file_name",
        "file_data",
        "desc",
        "media_info",
        "status",
        "file_id") match {
        case Seq(JsString(fileName), JsString(fileData), JsString(description), JsString(mediaInfo), JsNumber(status), JsString(fileId)) =>
          FileActorModel.File(
            fileName,
            fileData,
            description,
            mediaInfo,
            status.toInt,
            UUID.fromString(fileId))
        case Seq(JsString(fileName), JsString(fileData), JsString(description), JsString(mediaInfo), JsNumber(status)) =>
          FileActorModel.File(
            fileName,
            fileData,
            description,
            mediaInfo,
            status.toInt)
        case _ => throw DeserializationException("Invalid JSON Object")
      }
  }

  implicit val summaryFormat: RootJsonFormat[FileActorModel.Get] =
    jsonFormat2(FileActorModel.Get)
  implicit val addFileFormat2: RootJsonFormat[FileActorRoutes.AddFile] =
    jsonFormat1(FileActorRoutes.AddFile)

}