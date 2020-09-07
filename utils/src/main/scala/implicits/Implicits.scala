package utils.implicits

object Primitive {
	implicit class GuardInt(value: Int) {
		def nonZeroInt(): Option[Int] =
			 if (value <= 0) None else Some(value)
	}

	implicit class GuardString(value: String) {
		def nonEmptyString(): Option[String] =
			if (value.trim.isEmpty) None else Some(value.trim)
	}
}

object JsExtraction {
	import spray.json.{
		JsValue,
		JsObject,
		JsString,
		JsBoolean,
		JsArray,
		JsNumber
	}
	import utils.json.{ Extractor, Form }

	implicit class GuardJsValue(mJsV: Map[String, JsValue]) {
		def extract[T](
			field: String, 
			default: T)(
				action: JsValue => T, 
				errorAction: String => Unit): T =  mJsV.get(field) match {
			case Some(js) => action(js)
			case None => 
				errorAction(field)
				default 
		}

		def extracForm(fields: Map[String, Extractor[_]]): Form = {
			val errors = scala.collection.mutable.Map[String, String]()
			val mMap: Map[String, _] = fields.map { case (k, Extractor(default, required)) => (k, mJsV.get(k) match {
				case Some(js) => 
					js match {
						case JsString(v) => k -> v.toString
						case JsNumber(v) => k -> v.toInt 
						case JsBoolean(v) => k -> v
						case JsArray(v) => k -> v
						case JsObject(v) => k -> v
						case _ => 
							errors += (k -> "Invalid type found in this field.")
							k -> default
					}
				case None => 
					if(required)
						errors += (k -> "This field is required.")
					k -> default
			})}

			Form(errors, mMap, errors.size > 0)
		}

		def extractNonRequired[T](
			field: String, 
			default: T)(
				action: (JsValue) => T): T = mJsV.get(field) match {
			case Some(js) => action(js)
			case None => default 
		}
	}
}