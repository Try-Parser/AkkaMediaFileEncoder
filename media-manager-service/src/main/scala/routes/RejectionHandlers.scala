package routes

import akka.http.scaladsl.server.{
  MethodRejection,
  RejectionHandler,
  UnsupportedRequestContentTypeRejection
}
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Directives._

trait RejectionHandlers {

  val rejectionHandlers = RejectionHandler
    .newBuilder()
    .handleAll[MethodRejection] { methodRejections =>
      val names = methodRejections.map(_.supported.name)
      complete(StatusCodes.MethodNotAllowed,
               s"Not allowed. Supported methods $names")
    }
    .handleAll[UnsupportedRequestContentTypeRejection] { methodRejections =>
      val names = methodRejections.map(_.supported)
      complete(StatusCodes.MethodNotAllowed,
               s"Not allowed. Supported request content types $names")
    }
    .handleNotFound {
      complete(StatusCodes.NotFound, "Page not found.")
    }
    .result()

}
