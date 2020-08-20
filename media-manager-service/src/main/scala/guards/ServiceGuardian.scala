package media.service.guards

import scala.util.{ Success, Failure }

import akka.actor.typed.{ Behavior, ActorSystem }

import akka.actor.typed.scaladsl.Behaviors

import akka.http.scaladsl.Http
import akka.http.scaladsl.server.Route

import media.service.models.ServiceEncoder
import media.service.routes.ServiceRoutes

object ServiceGuardian {
	def apply(port: Int): Behavior[Nothing] = Behaviors.setup[Nothing] { ctx => 
		ServiceEncoder.initSharding(ctx.system)
		ServiceHttpServer
			.startServer(ServiceRoutes(ctx.system), port + 10)(ctx.system)
		Behaviors.empty
	}
}

private[service] object ServiceHttpServer {
	def startServer(
		routes: Route, 
		port: Int
	)(implicit system: ActorSystem[_]): Unit = {
		import system.executionContext

		Http().newServerAt("localhost", port).bind(routes).onComplete {
			case Success(binding) =>
				val localAddr = binding.localAddress
				system.log.info("Server online at Http://{}:{}/", localAddr.getHostString, localAddr.getPort)
			case Failure(ex) =>
				system.log.error("Failed to bind Http endpoint, terminating system", ex)
				system.terminate()
		}
	}
}
