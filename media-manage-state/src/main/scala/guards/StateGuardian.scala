package media.state.guards

import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorSystem, Behavior}
import akka.cluster.sharding.typed.scaladsl.ShardedDaemonProcess
import akka.cluster.sharding.typed.{ClusterShardingSettings, ShardedDaemonProcessSettings}
import akka.cluster.typed.Cluster
import akka.http.scaladsl.Http
import akka.http.scaladsl.server.Route
import akka.persistence.cassandra.query.scaladsl.CassandraReadJournal
import akka.persistence.query.Offset
import akka.projection.cassandra.scaladsl.CassandraProjection
import akka.projection.eventsourced.EventEnvelope
import akka.projection.eventsourced.scaladsl.EventSourcedProvider
import akka.projection.scaladsl.AtLeastOnceProjection
import akka.projection.{ProjectionBehavior, ProjectionId}
import media.state.events.EventProcessorSettings
import media.state.handlers.StateProjectionHandler
import media.state.models.FileActorModel
import media.state.routes.FileActorRoutes

import scala.util.{Failure, Success}

object StateGuardian {
  def apply(port: Int): Behavior[Nothing] = {
    Behaviors.setup[Nothing] { context =>
      val system = context.system
      val settings = EventProcessorSettings(system)
      FileActorModel.init(system, settings)
//      StateModel.init(system, settings)

      if (Cluster(system).selfMember.hasRole("read-model")) {
        val shardingSettings = ClusterShardingSettings(system)
        val shardedDaemonProcessSettings =
          ShardedDaemonProcessSettings(system).withShardingSettings(shardingSettings.withRole("read-model"))

        ShardedDaemonProcess(system).init(
          name = "StateProjection",
          settings.parallelism,
          n => ProjectionBehavior(ServiceHttpServer.createProjectionFor(system, settings, n)),
          shardedDaemonProcessSettings,
          Some(ProjectionBehavior.Stop))
      }

//      val routes = new StateRoutes()(context.system)
//      ServiceHttpServer.startServer(routes.shopping , port)(context.system)
      ServiceHttpServer.startServer(FileActorRoutes(context.system), port)(context.system)
      Behaviors.empty
    }
  }
}

private object ServiceHttpServer {
  def startServer(routes: Route, port: Int)(implicit system: ActorSystem[_]): Unit = {
    import system.executionContext
    Http().newServerAt("127.0.0.1", port).bind(routes).onComplete {
      case Success(binding) =>
        val localAddr = binding.localAddress
        system.log.info(
          "Server online at Http://{}:{}",
          localAddr.getHostString,
          localAddr.getPort)
      case Failure(exception) =>
        system.log.error("Failed")
        system.terminate()
    }
  }
  def createProjectionFor(
    system: ActorSystem[_],
    settings: EventProcessorSettings,
    index: Int): AtLeastOnceProjection[Offset, EventEnvelope[FileActorModel.Event]] = {
    val tag = s"${settings.tagPrefix}-$index"
    val sourceProvider = EventSourcedProvider.eventsByTag[FileActorModel.Event](
      system = system,
      readJournalPluginId = CassandraReadJournal.Identifier,
      tag = tag)
    CassandraProjection.atLeastOnce(
      projectionId = ProjectionId("StateProjection", tag),
      sourceProvider,
      handler = () => new StateProjectionHandler(tag, system))
  }
}
