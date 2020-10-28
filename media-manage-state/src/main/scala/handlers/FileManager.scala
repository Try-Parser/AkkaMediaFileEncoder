package handlers

import java.nio.file.Path

import akka.actor.typed.ActorSystem
import akka.event.Logging
import akka.stream.scaladsl.{FileIO, Source, StreamRefs}
import akka.stream.{Attributes, IOResult, SourceRef}
import akka.util.ByteString
import com.typesafe.config.ConfigFactory
import media.state.models.actors.FileActor.FileJournal
import utils.file.FileHandler

import scala.concurrent.Future

object FileManager {

  private val logAttributes: Attributes = Attributes.logLevels(
    onElement = Logging.WarningLevel,
    onFinish = Logging.InfoLevel,
    onFailure = Logging.DebugLevel
  )

  private def sourceForFile(file: FileJournal): Source[ByteString, Future[IOResult]] = {
    val source: Either[Throwable, Path] = FileHandler(ConfigFactory.load()).getPath(file.fileName)

    source match {
      case Right(value) =>
        FileIO.fromPath(value, 100000)
          .map(ByteString(_))
          .log("fileSource")
          .withAttributes(logAttributes)
      case Left(error) => throw error
    }
  }

  def play(file: FileJournal)(implicit actorSystem: ActorSystem[_]): SourceRef[ByteString] = {
    val fileSource: Source[ByteString, Future[IOResult]] = sourceForFile(file)
    fileSource.runWith(StreamRefs.sourceRef())
  }

}
