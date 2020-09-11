package media.state.guards

import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorSystem, Behavior}
import akka.cluster.sharding.typed.scaladsl.ShardedDaemonProcess
import akka.cluster.sharding.typed.{ClusterShardingSettings, ShardedDaemonProcessSettings}
import akka.cluster.typed.Cluster
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
import utils.traits.Event

object StateGuardian {
  def apply(): Behavior[Nothing] = Behaviors.setup[Nothing] { context =>
    val system = context.system
    val settings = EventProcessorSettings(system)
    FileActorModel.init(settings)(system)

    if (Cluster(system).selfMember.hasRole("read-model")) {
      val shardingSettings = ClusterShardingSettings(system)
      val shardedDaemonProcessSettings = ShardedDaemonProcessSettings(system)
        .withShardingSettings(shardingSettings.withRole("read-model"))

      ShardedDaemonProcess(system).init(
        name = "StateProjection",
        settings.parallelism,
        n => ProjectionBehavior(
          ServiceHttpServer.createProjectionFor(system, settings, n)),
        shardedDaemonProcessSettings,
        Some(ProjectionBehavior.Stop))
    }
    Behaviors.empty
  }
}

private object ServiceHttpServer {
  def createProjectionFor(
    system: ActorSystem[_],
    settings: EventProcessorSettings,
    index: Int): AtLeastOnceProjection[Offset, EventEnvelope[Event]] = {

    val tag = s"${settings.tagPrefix}-$index"

    val sourceProvider = EventSourcedProvider.eventsByTag[Event](
        system = system,
        readJournalPluginId = CassandraReadJournal.Identifier,
        tag = tag)

    CassandraProjection
      .atLeastOnce(
        projectionId = ProjectionId("file-actor", tag),
        sourceProvider,
        handler = () => new StateProjectionHandler(tag, system))
  }
}
