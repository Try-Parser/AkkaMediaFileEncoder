package media

import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route

class ServiceRoutes {
	val hello: Route = path("hello") {
		get {
			complete("Hi")
		}
	}
}

object ServiceRoutes {
	val route: Route = new ServiceRoutes().hello
}