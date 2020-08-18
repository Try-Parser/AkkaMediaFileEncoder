package media.routes

import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route

class ServiceRoutes {
	val uploadFile: Route = path("upload") {
		get {
			complete("uploadFile")
		}
	}	

	val convertFile: Route = path("convert") {
		get {
			complete("convertFile")
		}
	}	

	val convertStatus: Route = path("status") {
		get {
			complete("convertStatus")
		}
	}	

	val playFile: Route = path("play") {
		get {
			complete("playFile")
		}
	}
}

object ServiceRoutes {
	val route: ServiceRoutes = new ServiceRoutes()
	val Routes: Route = route.uploadFile ~ route.convertFile ~ route.convertStatus ~route.playFile
}