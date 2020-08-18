package media

import akka.actor.typed.ActorSystem
import akka.actor.typed.scaladsl.Behaviors
import akka.http.scaladsl.Http
import akka.http.scaladsl.server.Route

import scala.util.{ Success, Failure }

import media.routes.ServiceRoutes

object ServerService {
	def main(args: Array[String]): Unit = 
		ActorSystem[Nothing](Behaviors.setup[Nothing] { ctx => 
			startServer(ServiceRoutes.Routes)(ctx.system)
			Behaviors.empty
		}, "manager-service-system")

	private def startServer(routes: Route)(implicit system: ActorSystem[_]): Unit = {
		import system.executionContext

		Http().newServerAt("localhost", 8080).bind(routes).onComplete {
			case Success(binding) =>
				val localAddr = binding.localAddress
				system.log.info("Server online at Http://{}:{}/", localAddr.getHostString, localAddr.getPort)
			case Failure(ex) =>
				system.log.error("Failed to bind Http endpoint, terminating system", ex)
				system.terminate()
		}
	}
}	