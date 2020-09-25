package media.state.models.shards

import java.util.UUID
import utils.traits.Response

import akka.actor.typed.ActorSystem
import akka.actor.typed.ActorRef

import akka.persistence.typed.scaladsl.{
  Effect, 
  ReplyEffect, 
}
import akka.stream.scaladsl.Source
import akka.util.ByteString

import media.state.media.MediaConverter
import media.state.models.actors.FileActor.{
  AddFile,
  FileAdded,
  GetFile,
  State,
  Config,
  FileJournal,
  ConvertFile,
  ConvertedFile,
  UpdatedStatus,
  UpdateStatus,
  PersistJournal
}

import utils.actors.ShardActor
import utils.traits.{ Command, Event }
 
private[models] class FileShard extends ShardActor[Command, Event, State]("FileActor") { 

  def processFile(fileId: UUID, self: ActorRef[Command])(implicit sys: ActorSystem[_]): CommandHandler[ReplyEffect] = { 
    (state, cmd) => cmd match {
      case AddFile(file, replyTo) =>
        val newName = Config.handler.generateName(file.fileName)

        Config.writeFile(newName, Source.single(ByteString(file.fileData)))(
          akka.stream.Materializer(sys.classicSystem)).map { _ => 
            self ! PersistJournal(fileId, FileJournal(
              newName,
              Config.handler.uploadFilePath,
              file.contentType,
              file.status,
              fileId), replyTo)
          }(sys.executionContext)
        Effect.none.thenNoReply
      case PersistJournal(id, journal, replyTo) => 
        Effect.persist(FileAdded(fileId, journal)).thenReply(replyTo)((state: State) => state.getFileJournal(true))
      case GetFile(replyTo) =>
        Effect.reply[Response, Event, State](replyTo)(state.getFile)
      case UpdateStatus(status) => 
        Effect.persist[Event, State](UpdatedStatus(status)).thenNoReply
      case ConvertFile(mm, replyTo) =>
        val newName = Config.handler.generateName(mm.info.fileName)

        val convertedJournal = FileJournal(
            newName,
            Config.handler.convertFilePath,
            mm.info.contentType.get.toString,
            4,
            fileId)

        MediaConverter.startConvert(mm, newName).map { 
          case Some(name) => self ! UpdateStatus("complete")
          case None => self ! UpdateStatus("failed")
        }(sys.executionContext)

        Effect.persist(ConvertedFile(convertedJournal)).thenReply(replyTo)((state: State) => state.getFileProgress)
  }}

  def handleEvent: EventHandler = { (state, event) => event match {
      case FileAdded(_, file) => 
        state.insert(file)
      case ConvertedFile(file) => 
        state
          .insert(file)
          .updateStatus("inprogress")
      case UpdatedStatus(status) => 
        state.updateStatus(status)
    }}
}