package media.state.models

import java.util.UUID

import akka.actor.typed.{
  ActorRef, 
  ActorSystem, 
  Behavior, 
  SupervisorStrategy
}
import akka.cluster.sharding.typed.scaladsl.{ EntityTypeKey, EntityContext }
import akka.persistence.typed.PersistenceId
import akka.persistence.typed.scaladsl.{
  Effect, 
  EventSourcedBehavior, 
  ReplyEffect, 
  RetentionCriteria
}
import akka.stream.scaladsl.Source
import akka.util.ByteString
import media.state.events.EventProcessorSettings
import utils.actors.{Actor, ShardActor}
import utils.traits.{CborSerializable, Command, Event}

import ws.schild.jave.MultimediaObject

import scala.concurrent.duration._

import utils.traits.CborSerializable
import com.typesafe.config.ConfigFactory

class FileActorModel extends ShardActor[Command]("FileActor") {
  import media.fdk.file.FileIOHandler

  import media.state.models.FileActorModel.{
    AddFile,
    FileAdded,
    GetFile,
    State,
    Test
  }

  private def ProcessFile(
    fileId: UUID, 
    state: State, 
    command: Command
  ): ReplyEffect[Event, State] = command match {
    case Test => 
      println("000000000000000000000000000000000000000000000000000000000000000000000000000000")
      println("000000000000000000000000000000000000000000000000000000000000000000000000000000")
      println(s"00000000000000000000000000      Mr Debug Test    0000000000000000000000000000")
      println("000000000000000000000000000000000000000000000000000000000000000000000000000000")
      println("000000000000000000000000000000000000000000000000000000000000000000000000000000")
      Effect.noReply
    case AddFile(file, replyTo) =>
      
      val newName: String = FileIOHandler(ConfigFactory.load())
        .handler
        .generateName(file.fileName)

      println("000000000000000000000000000000000000000000000000000000000000000000000000000000")
      println("000000000000000000000000000000000000000000000000000000000000000000000000000000")
      println("000000000000000000000000     Mr Debug AddFile     0000000000000000000000000000")
      println("000000000000000000000000000000000000000000000000000000000000000000000000000000")
      println("000000000000000000000000000000000000000000000000000000000000000000000000000000")
      

      // FileIOHandler.writeFile(
      //   newName,
      //   file.fileData)

      Effect
        .persist(FileAdded(fileId, file.updateFileData(newName, null)))
        .thenReply(replyTo)(state => state.getFile)

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
  import utils.file.ContentType
  import media.fdk.json.MediaInfo

  case object Test extends Command

  final case class File(
    fileName: String,
    fileData: String, //Source[ByteString, _],
    contentType: String,
    status: Int,
    fileId: UUID = UUID.randomUUID(),
    mmo: MultimediaObject = null) extends Command {

    def convertToMediaInfo(): MediaInfo = MediaInfo(
      this.fileName, 
      null, 
      ContentType(this.contentType), 
      this.status, 
      this.fileId
    )

    def updateFileData(newName: String, mmo: MultimediaObject = null): File = 
      File(newName, this.fileData, this.contentType, this.status, this.fileId, mmo)
  }

  final case class State(
    file: File,
    status: Option[String]) extends CborSerializable {
    def insert(file: File): State = copy(file = file)
    def isComplete: Boolean = status.isDefined
    def getFile: Get = Get(file, isComplete)
  }

  object State {
    val empty = State(file = File("", null, null, 0), status = None)
  }

  final case class AddFile(file: File, replyTo: ActorRef[Get]) extends Command
  final case class RemoveFile(fileId: UUID) extends Command
  final case class GetFile(replyTo: ActorRef[Get]) extends Command
  final case class Get(file: File, status: Boolean) extends CborSerializable

  final case class FileAdded(fileId: UUID, file: File) extends Event
  final case class FileRemoved(fileId: UUID) extends Event

  val TypeKey: EntityTypeKey[Command] = EntityTypeKey[Command](actor.actorName)

  def createBehavior(e: EntityContext[Command])(implicit sys: ActorSystem[_], sett: EventProcessorSettings): Behavior[Command] = { 
    sys.log.info("Creating identity {} id: {} ", actor.actorName, e.entityId)
    val n = math.abs(e.entityId.hashCode % sett.parallelism)
    val eventTag = sett.tagPrefix + "-" + n
    FileActorModel(UUID.fromString(e.entityId), Set(eventTag))
  }

  def init(settings: EventProcessorSettings)(implicit sys: ActorSystem[_]): Unit = {
    implicit val sett: EventProcessorSettings = settings
    actor.init(TypeKey, createBehavior){ entity =>
      entity.withRole("write-model")
    }
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
