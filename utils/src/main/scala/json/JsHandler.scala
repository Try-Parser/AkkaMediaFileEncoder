package utils.json

// import scala.collection.mutable.ListBuffer

import spray.json.DeserializationException
import spray.json.JsValue

final case class Extractor[T](
	default: T,
	required: Boolean = true)

final case class Form(
	errors: Map[String, String],
	values: Map[String, _],
	hasErrors: Boolean = false) {

	def get[T](key: String, default: T)(action: Any => T): T = values.get(key) match {
		case Some(value) => action(value)
		case None => default
	} 
}

class JsHandler {
	protected def extractor[T](fields: List[String], a: T, msg: String = "Invalid fields."): T = {
		if(fields.isEmpty) a
		else error(msg, fieldNames = fields)
	}

	protected def error(
			msg: String, 
			fieldNames: List[String] = Nil,
			cause: Throwable = null) = 
				throw new DeserializationException(msg, cause, fieldNames)

	protected def JsExtract[T](
		json: Map[String, JsValue], 
		fieldName: String, 
		default: T,
		action: (JsValue) => T,  
		errorAction: (String) => Unit): T = json.get(fieldName) match {
			case Some(value) => action(value)
			case None =>
				errorAction(fieldName)
			 	default 
		}

	protected def JsExtractOption[T](
		json: Map[String, JsValue],
		fieldName: String,
		default: T,
		action: (JsValue) => T): T = json.get(fieldName) match {
			case Some(value) => action(value)
			case None => default 
		}
}


// val form: Form = 
// 	js.extracForm(
// 		Map("bit_rate" -> Extractor[Int](0),
// 		"frame_rate" -> Extractor[Int](0), 
// 		"codec" -> Extractor[String](""),
// 		"tag" -> Extractor[String]("", false)))

// extractor[Option[Video]](
// 	form.errors.toList ++ errorFields.toMap.toList, 
// 	Some(Video(
// 		form.get("bit_rate", BitRate(0)){ d => BitRate(d.toInt) }, 
// 		form.get("frame_rate", FrameRate(0.0)){ d => FrameRate(d.toDouble) },
// 		form.get("decoder", CodecName("")){ d => CodecName(d.toString) }, 
// 		size,
// 		form.get("tag", Tag("")){ d => Tag(d.toString) })))