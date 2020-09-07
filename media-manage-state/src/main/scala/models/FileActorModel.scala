package media.state.models

import java.util.UUID

import akka.actor.typed.{
  ActorRef,
  ActorSystem,
  Behavior,
  SupervisorStrategy
}
import akka.cluster.sharding.typed.scaladsl.{
  ClusterSharding,
  Entity,
  EntityTypeKey
}
import akka.pattern.StatusReply
import akka.persistence.typed.PersistenceId
import akka.persistence.typed.scaladsl.{
  Effect,
  EventSourcedBehavior,
  ReplyEffect,
  RetentionCriteria
}
import media.state.events.EventProcessorSettings
import utils.traits.{
  CborSerializable,
  Command
}

import scala.concurrent.duration._

object FileActorModel {
  final case class File(
    fileName: String,
    fileData: String,
    description: String,
    mediaInfo: String,
    status: Int,
    fileId: UUID = UUID.randomUUID()) extends CborSerializable
  final case class State(
    file: File,
    status: Option[String]) extends CborSerializable {
    def insert(file: File): State = copy(file = file)
    def isComplete: Boolean = status.isDefined
    def getFile: Get = Get(file, isComplete)
  }
  object State {
    val empty = State(file = File("", "", "", "", 0), status = None)
  }
  final case class AddFile(file: File, replyTo: ActorRef[StatusReply[Get]]) extends Command
  final case class RemoveFile(fileId: UUID) extends Command
  final case class GetFile(replyTo: ActorRef[Get]) extends Command

  final case class Get(file: File, status: Boolean) extends CborSerializable

  sealed trait Event extends CborSerializable {
    def fileId: UUID
  }

  final case class FileAdded(fileId: UUID, file: File) extends Event
  final case class FileRemoved(fileId: UUID) extends Event

  val TypeKey: EntityTypeKey[Command] =
    EntityTypeKey[Command]("FileActor")

  def init(system: ActorSystem[_], eventProcessorSettings: EventProcessorSettings): Unit = {
    ClusterSharding(system).init(Entity(TypeKey) { eCntxt =>
      val n = math.abs(eCntxt.entityId.hashCode % eventProcessorSettings.parallelism)
      val eventTag = eventProcessorSettings.tagPrefix + "-" + n
      FileActorModel(UUID.fromString(eCntxt.entityId), Set(eventTag))
    }.withRole("write-model"))
  }

  def apply(fileId: UUID, eventTags: Set[String]): Behavior[Command] = {
    EventSourcedBehavior
      .withEnforcedReplies[Command, Event, State](
        PersistenceId(TypeKey.name, fileId.toString),
        State.empty,
        (state, command) => ProcessFile(fileId, state, command),
        (state, event) => handleEvent(state, event))
      .withTagger(_ => eventTags)
      .withRetention(RetentionCriteria.snapshotEvery(numberOfEvents = 100, keepNSnapshots = 3))
      .onPersistFailure(SupervisorStrategy.restartWithBackoff(200.millis, 5.seconds, 0.1))
  }

  private def ProcessFile(fileId: UUID, state: State, command: Command): ReplyEffect[Event, State] =
    command match {
      case AddFile(file, replyTo) =>
        Effect
          .persist(FileAdded(fileId, file))
          .thenReply(replyTo)(fileAdded => StatusReply.Success(fileAdded.getFile))
      case GetFile(replyTo) =>
          Effect.reply(replyTo)(state.getFile)
    }

  private def handleEvent(state: State, event: Event): State = {
    event match {
      case FileAdded(_, file) => state.insert(file)
    }
  }
}
