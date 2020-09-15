package media.fdk.models

import java.util.UUID

import akka.util.ByteString
import akka.pattern.StatusReply
import akka.actor.typed.ActorRef
import akka.stream.scaladsl.Source

import ws.schild.jave.info.MultimediaInfo

import utils.traits._

object FileActorStateModel {
	final case class AddFile(file: File, replyTo: ActorRef[StatusReply[Get]]) extends Command
	final case class RemoveFile(fileId: UUID) extends Command
	final case class GetFile(replyTo: ActorRef[Get]) extends Command
	final case class Get(file: File, status: Boolean) extends CborSerializable

	final case class FileAdded(fileId: UUID, file: File) extends Event
	final case class FileRemoved(fileId: UUID) extends Event

	final case class File(
	  fileName: String,
	  fileData: Source[ByteString, _],
	  extension: String,
	  mediaInfo: MultimediaInfo,
	  status: Int,
	  fileId: UUID = UUID.randomUUID()) extends CborSerializable
}
