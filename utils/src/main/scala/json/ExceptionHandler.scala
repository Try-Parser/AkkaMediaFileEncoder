package utils.json

import spray.json.{
	DeserializationException,
	JsValue
}

class ExceptionHandler {
	protected def extractor[T](fields: List[String], a: T, msg: String = "Invalid fields."): T = 
		if(fields.isEmpty) a
		else error(msg, fieldNames = fields)

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