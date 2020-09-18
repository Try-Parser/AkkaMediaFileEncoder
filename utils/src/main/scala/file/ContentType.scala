package utils.file

import akka.http.scaladsl.model.{ ContentType => CT }


case object ContentType {
	
	type HttpContentType = CT 

	def apply(ct: CT): HttpContentType = ct
	def apply(ctString: String): HttpContentType = CT.parse(ctString).fold(
		err => throw new Exception(s"ContenType : $ctString is not valid ContentType."),
		ct => ct
	)
}