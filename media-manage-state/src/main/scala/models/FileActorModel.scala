package media.state.models

import java.util.UUID

import akka.actor.typed.{
  ActorRef,
  ActorSystem,
  Behavior,
  SupervisorStrategy
}
import akka.cluster.sharding.typed.scaladsl.EntityTypeKey
import akka.pattern.StatusReply
import akka.persistence.typed.PersistenceId
import akka.persistence.typed.scaladsl.{
  Effect,
  EventSourcedBehavior,
  ReplyEffect,
  RetentionCriteria
}
import media.state.events.EventProcessorSettings
import utils.actors.{Actor, ShardActor}
import utils.traits.{CborSerializable, Command, Event}

import scala.concurrent.duration._

class FileActorModel extends ShardActor[Command]("FileActor") {
  import media.state.models.FileActorModel.{
    AddFile,
    FileAdded,
    GetFile,
    State
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

object FileActorModel extends Actor[FileActorModel]{
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

  final case class FileAdded(fileId: UUID, file: File) extends Event
  final case class FileRemoved(fileId: UUID) extends Event

  val TypeKey: EntityTypeKey[Command] = EntityTypeKey[Command](actor.actorName)

  def init(settings: EventProcessorSettings)(implicit sys: ActorSystem[_]): Unit =
    actor.init(TypeKey, { e =>
      sys.log.info("Creating identity {} id: {} ", actor.actorName, e.entityId)
      val n = math.abs(e.entityId.hashCode % settings.parallelism)
      val eventTag = settings.tagPrefix + "-" + n
      FileActorModel(UUID.fromString(e.entityId), Set(eventTag))
    }){ entity =>
      entity.withRole("write-model")
    }

  def apply(fileId: UUID, eventTags: Set[String]): Behavior[Command] = 
    EventSourcedBehavior.withEnforcedReplies[Command, Event, State](
        PersistenceId(TypeKey.name, fileId.toString),
        State.empty,
        (state, command) => actor.ProcessFile(fileId, state, command),
        (state, event) => actor.handleEvent(state, event))
      .withTagger(_ => eventTags)
      .withRetention(RetentionCriteria.snapshotEvery(numberOfEvents = 100, keepNSnapshots = 3))
      .onPersistFailure(SupervisorStrategy.restartWithBackoff(200.millis, 5.seconds, 0.1))
}
