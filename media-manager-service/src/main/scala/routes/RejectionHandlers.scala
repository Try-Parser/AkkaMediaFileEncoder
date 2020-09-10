package routes

import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.{MalformedRequestContentRejection, MethodRejection, RejectionHandler, UnsupportedRequestContentTypeRejection}

trait RejectionHandlers {

  val rejectionHandlers: RejectionHandler = RejectionHandler
    .newBuilder()
    .handleAll[MethodRejection] { methodRejections =>
      val names = methodRejections.map(_.supported.name)
      complete(StatusCodes.MethodNotAllowed, s"Not allowed. Supported methods $names")
    }
    .handleAll[UnsupportedRequestContentTypeRejection] { methodRejections =>
      val names = methodRejections.map(_.supported)
      complete(StatusCodes.MethodNotAllowed, s"Not allowed. Supported request content types $names")
    }
    .handleAll[MalformedRequestContentRejection] { methodRejections =>
      val message = methodRejections.map(_.message).headOption.getOrElse("Malformed request content")
      complete(StatusCodes.BadRequest, s"$message")
    }
    .result()

}
