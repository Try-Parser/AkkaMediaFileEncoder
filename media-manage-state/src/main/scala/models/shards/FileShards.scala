package media.state.models.shards

import java.util.UUID

import akka.actor.typed.ActorSystem
import akka.actor.typed.ActorRef
import akka.persistence.typed.scaladsl.{Effect, ReplyEffect}
import media.state.media.MediaConverter
import media.state.models.actors.FileActor.{AddFile, CompressFile, Config, ConvertFile, ConvertedFile, FileAdded, FileJournal, GetFile, PlayFile, State, UpdateStatus, UpdatedStatus}
import media.state.models.actors.FileActorListModel
import utils.actors.ShardActor
import utils.traits.{Command, Event, Response}
 
private[models] class FileShard extends ShardActor[Command, Event, State]("FileActor") { 

  def processFile(
    fileId: UUID,
    self: ActorRef[Command],
    singleton: ActorRef[Command])(implicit sys: ActorSystem[_]): CommandHandler[ReplyEffect] = {
    (state, cmd) => cmd match {
      case CompressFile(data: Array[Byte], fileName: String, replyTo) =>
        Config.writeFile(fileName, data)
        Effect.reply[Response, Event, State](replyTo)(state.getAck)
      case AddFile(file, replyTo) =>
        val journal = FileJournal(
          file.fileName,
          Config.handler.uploadFilePath,
          file.contentType,
          file.status,
          fileId)
        Effect.persist(
          FileAdded(fileId, journal))
          .thenReply(replyTo)((state: State) => {
            singleton.tell(FileActorListModel.AddFile(journal, "none"))
            state.getFileJournal(true)
          })
      case GetFile(replyTo) =>
        Effect.reply[Response, Event, State](replyTo)(state.getFile)
      case PlayFile(replyTo) =>
        Effect.reply[Response, Event, State](replyTo)(state.playFile(replyTo, sys))
      case UpdateStatus(file, status) =>
        Effect.persist[Event, State](UpdatedStatus(file, status)).thenNoReply
      case ConvertFile(mm, replyTo) =>
        val newName = Config.handler.generateName(mm.fileName, mm.extension)

        val convertedJournal = FileJournal(
            newName,
            Config.handler.convertFilePath,
            "",
            4,
            fileId)

        MediaConverter.startConvert(mm, newName).map { 
          case Some(name) => self ! UpdateStatus(convertedJournal, "complete")
          case None => self ! UpdateStatus(convertedJournal, "failed")
        }(sys.executionContext)

        Effect.persist(ConvertedFile(convertedJournal)).thenReply(replyTo)((state: State) => state.getFileProgress)
  }}

  def handleEvent(implicit singleton: ActorRef[Command]): EventHandler = { (state, event) => event match {
      case FileAdded(_, file) => 
        val runtime = java.lang.Runtime.getRuntime()
        println(s"""
          | Free Memory : ${runtime.freeMemory()} 
          | Total Memory : ${runtime.totalMemory()} 
          | Processor : ${runtime.availableProcessors()} 
        """)
        state.insert(file)
      case ConvertedFile(file) => 
        state
          .insert(file)
          .updateStatus("inprogress")
      case UpdatedStatus(file, status) => {
        singleton.tell(FileActorListModel.AddFile(file, status))
        state.updateStatus(status)
      }
    }}
}