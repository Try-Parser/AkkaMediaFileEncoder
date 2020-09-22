package media.state.models.actors

import java.util.UUID
import scala.concurrent.duration._
import com.typesafe.config.ConfigFactory

import akka.actor.typed.{
  ActorRef, 
  ActorSystem, 
  Behavior, 
  SupervisorStrategy
}
import akka.cluster.sharding.typed.scaladsl.{ EntityTypeKey, EntityContext }
import akka.persistence.typed.PersistenceId
import akka.persistence.typed.scaladsl.{
  EventSourcedBehavior, 
  RetentionCriteria
}

import media.state.events.EventProcessorSettings
import media.fdk.codec.{ Video, Audio }
import media.fdk.codec.Codec.{ Duration, Format }
import media.fdk.json.MultiMedia

import utils.actors.Actor
import utils.traits.{CborSerializable, Command, Event}

import utils.file.ContentType
import media.fdk.json.MediaInfo
import media.fdk.file.FileIOHandler

import media.state.models.shards.FileShard

object FileActor extends Actor[FileShard]{

  val Config = FileIOHandler(ConfigFactory.load())

  /*** CMD  ***/
  final case class AddFile(file: File, replyTo: ActorRef[MediaDescription]) extends Command
  final case class RemoveFile(fileId: UUID) extends Command
  final case class ConvertFile(info: MultiMedia, reply: ActorRef[FileProgress]) extends Command
  final case class GetFile(replyTo: ActorRef[Get]) extends Command

  /*** STATE ***/
  final case class State(
    file: FileJournal,
    status: Option[String]) extends CborSerializable {
    def insert(file: FileJournal): State = copy(file = file)
    def isComplete: Boolean = status.isDefined
    def getFile: Get = Get(file, isComplete)
    def getFileProgress: FileProgress = FileProgress(file.fileName, file.fileId) 
    def getFileJournal(upload: Boolean): MediaDescription = {

      val mMmo = Config.getMultiMedia(file.fileName, upload)
      val info = mMmo.getInfo()
      val media = (Video(info.getVideo()), Audio(info.getAudio()))

      MediaDescription(Duration(info.getDuration), Format(info.getFormat), MediaInfo(
        file.fileName,
        media._1,
        media._2,
        ContentType(file.contentType),
        file.status,
        file.fileId
      ))
    }
  }

  object State {
    val empty = State(file = FileJournal.empty, status = None)
  }
  final case class FileAdded(fileId: UUID, file: FileJournal) extends Event
  final case class FileRemoved(fileId: UUID) extends Event
  final case class ConvertedFile(journal: FileJournal) extends Event

  /*** PERSIST ***/
  final case class File(
    fileName: String,
    fileData: Array[Byte],
    contentType: String,
    status: Int,
    fileId: UUID = UUID.randomUUID()) extends CborSerializable 

  final case class MediaDescription(
    duration: Duration,
    format: Format,
    mediaInfo: MediaInfo
  ) extends CborSerializable

  final case class FileJournal(
    fileName: String, 
    fullPath: String, 
    contentType: String, 
    status: Int, 
    fileId: UUID) extends CborSerializable

  final case class FileProgress(fileName: String, fileId: UUID, progress: Int = 0) extends CborSerializable

  object FileJournal {
    def empty: FileJournal = FileJournal("", "", "", 0, null)
  }
   
  final case class Get(journal: FileJournal, status: Boolean) extends CborSerializable

  /*** INI ***/
  val TypeKey: EntityTypeKey[Command] = EntityTypeKey[Command](actor.actorName)

  def createBehavior(e: EntityContext[Command])(implicit sys: ActorSystem[_], sett: EventProcessorSettings): Behavior[Command] = { 
    sys.log.info("Creating identity {} id: {} ", actor.actorName, e.entityId)
    val n = math.abs(e.entityId.hashCode % sett.parallelism)
    val eventTag = sett.tagPrefix + "-" + n
    apply(UUID.randomUUID, Set(eventTag))
  }

  def init(settings: EventProcessorSettings)(implicit sys: ActorSystem[_]): Unit = {
    implicit val sett: EventProcessorSettings = settings
    actor.init(TypeKey, createBehavior){ entity =>
      entity.withRole("write-model")
    }
  }

  def apply(fileId: UUID, eventTags: Set[String])(implicit sys: ActorSystem[_]): Behavior[Command] = 
    EventSourcedBehavior.withEnforcedReplies[Command, Event, State](
        PersistenceId(TypeKey.name, fileId.toString),
        State.empty,
        (state, command) => 
          actor
            .processFile(fileId, state, command)
            .unsafeRun(),
        (state, event) => actor.handleEvent(state, event))
      .withTagger(_ => eventTags)
      .withRetention(RetentionCriteria.snapshotEvery(numberOfEvents = 100, keepNSnapshots = 3))
      .onPersistFailure(SupervisorStrategy.restartWithBackoff(200.millis, 5.seconds, 0.1))
}
