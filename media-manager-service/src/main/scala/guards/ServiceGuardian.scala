package media.service.guards

import scala.util.{ Success, Failure }
import java.util.concurrent.atomic.AtomicInteger

import akka.util.ByteString
import akka.actor.typed.{ Behavior, ActorSystem }
import akka.actor.typed.scaladsl.Behaviors
import akka.http.scaladsl.Http
import akka.http.scaladsl.server.Route
import akka.http.scaladsl.settings.ServerSettings

import media.service.routes.ServiceRoutes

object ServiceGuardian {
	def apply(port: Int): Behavior[Nothing] = Behaviors.setup[Nothing] { ctx => 
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

		val defaultSettings = ServerSettings(system)
		val ping = new AtomicInteger()

		Http().newServerAt("0.0.0.0", port)
			.adaptSettings(_.mapWebsocketSettings(_.withPeriodicKeepAliveData(() => ByteString(s"debug-${ping.incrementAndGet()}"))))
			.bind(routes).onComplete {
				case Success(binding) =>
					val localAddr = binding.localAddress
					system.log.info("Server online at Http://{}:{}/", localAddr.getHostString, localAddr.getPort)
				case Failure(ex) =>
					system.log.error("Failed to bind Http endpoint, terminating system", ex)
					system.terminate()
		}
	}
}
