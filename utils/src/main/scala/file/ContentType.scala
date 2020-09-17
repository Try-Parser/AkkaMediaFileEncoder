package utils.file

import akka.http.scaladsl.model.{ ContentType => CT }

trait ContentType 

case object ContentType extends ContentType {
	
	type HttpContentType = CT 

	def apply(ct: CT): CT = ct
	def apply(ctString: String): CT = CT.parse(ctString).fold(
		err => throw new Exception(s"ContenType : $ctString is not valid ContentType."),
		ct => ct
	)
}