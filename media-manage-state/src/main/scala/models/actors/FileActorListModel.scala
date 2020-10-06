package media.state.models.actors

import java.util.UUID

import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{
  ActorRef,
  ActorSystem,
  Behavior,
  SupervisorStrategy
}
import akka.cluster.sharding.typed.scaladsl.EntityTypeKey
import akka.cluster.typed.{ ClusterSingleton, SingletonActor }
import akka.persistence.typed.PersistenceId
import akka.persistence.typed.scaladsl.ReplyEffect
import akka.persistence.typed.scaladsl.{ Effect, EventSourcedBehavior, RetentionCriteria }

import media.state.models.actors.FileActor.FileJournal

import utils.traits.{ CborSerializable, Command, Event }

import scala.concurrent.duration.DurationInt

object FileActorListModel {
  final case class State(files: List[(FileJournal, String)]) extends CborSerializable {
    def insert(file: FileJournal, status: String): State = copy(files = files :+ (file, status))
    def getFile(key: Option[String] = None): Get =
      key match {
        case Some(key) => Get(files
          .filter { case (journal, status) =>
            journal.fileId.toString.contains(key) ||
            journal.fileName.contains(key) ||
            journal.contentType.contains(key) ||
            journal.fullPath.contains(key) ||
            status.contains(key)
          })
        case _ => Get(files)
      }
  }
  object State {
    val empty = State(files = List.empty)
  }

  final case class AddFile(file: FileJournal, status: String) extends Command
  final case class Get(files: List[(FileJournal, String)]) extends Command
  final case class GetFiles(key: Option[String], reply: ActorRef[Get]) extends Command
  final case class FileAdded(regionId: UUID, file: FileJournal, status: String) extends Event

  val ListTypeKey: EntityTypeKey[Command] =
    EntityTypeKey[Command]("FileListActor")

  def init(system: ActorSystem[_]): Unit = {
    ClusterSingleton(system)
      .init(SingletonActor(apply(), "FileListActor"))
  }

  def apply(): Behavior[Command] = Behaviors.setup { cntxt =>
    val fileId = "0b13122d-3882-47b6-a0a1-b562234752c4"
    EventSourcedBehavior.withEnforcedReplies[Command, Event, State](
      PersistenceId(ListTypeKey.name, fileId),
      State.empty,
      (state, command) => handleCommand(UUID.fromString(fileId), state, command),
      (state, event) => handleEvent(state, event))
      .withRetention(RetentionCriteria.snapshotEvery(numberOfEvents = 100, keepNSnapshots = 3))
      .onPersistFailure(SupervisorStrategy.restartWithBackoff(200.millis, 5.seconds, 0.1))
  }

  def handleCommand(fileId: UUID, state: State, command: Command): ReplyEffect[Event, State] = {
    command match {
      case AddFile(file, status) =>
        Effect
          .persist(FileAdded(fileId, file, status))
          .thenNoReply()
      case GetFiles(key, replyTo) =>
        Effect.reply(replyTo)(state.getFile(key))
      case _ => Effect.noReply
    }
  }

  def handleEvent(state: State, event: Event): State = {
    event match {
      case FileAdded(_, file, status) => state.insert(file, status)
    }
  }
}
