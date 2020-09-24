package media.state.models.shards

import java.util.UUID
import utils.traits.Response

import akka.actor.typed.ActorSystem

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
  UpdatedStatus
}

import utils.actors.ShardActor
import utils.traits.{ Command, Event }
import utils.concurrent.FTE
 
private[models] class FileShard extends ShardActor[Command]("FileActor") {

  def processFile(
    fileId: UUID, 
    state: State, 
    command: Command
  )(implicit sys: ActorSystem[_]): FTE[ReplyEffect[Event, State]] = command match {
    case AddFile(file, replyTo) =>
      val newName = Config
        .handler
        .generateName(file.fileName)

      FTE.response(Config
        .writeFile(
          newName, 
          Source.single(ByteString(file.fileData))
        )(akka.stream.Materializer(sys.classicSystem)).map { _ => 
          Effect
            .persist(FileAdded(
              fileId, 
              FileJournal(
                newName,
                Config.handler.uploadFilePath,
                file.contentType,
                file.status,
                fileId)))
            .thenReply(replyTo)((state: State) => state.getFileJournal(true))
        }(sys.executionContext))
    case GetFile(replyTo) =>
      FTE.response(Effect.reply[Response, Event, State](replyTo)(state.getFile))
    case ConvertFile(mm, replyTo) =>
      val newName = Config
        .handler
        .generateName(mm.info.fileName)

      val convertedJournal = FileJournal(
          newName,
          Config.handler.convertFilePath,
          mm.info.contentType.get.toString,
          4,
          fileId)

      MediaConverter.startConvert(mm, newName).map { 
        case Some(name) => Effect
          .persist(UpdatedStatus("complete"))
          .thenNoReply
        case None => Effect
          .persist(UpdatedStatus("failed"))
          .thenNoReply
      }(sys.executionContext)

      FTE.response(Effect
        .persist(ConvertedFile(convertedJournal))
        .thenReply(replyTo)((state: State) => state.getFileProgress))
  }

  def handleEvent(state: State, event: Event): State = 
    event match {
      case FileAdded(_, file) => 
        state.insert(file)
      case ConvertedFile(file) => 
        state
          .insert(file)
          .updateStatus("inprogress")
      case UpdatedStatus(status) => 
        state.updateStatus(status)
    }
}