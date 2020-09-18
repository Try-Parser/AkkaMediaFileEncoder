import java.io.File

import akka.http.scaladsl.model.{ContentType, MediaTypes}

object TestFiles {

  case class AudioFile(file: File, contentType: ContentType)

  def sampleAudio: AudioFile = {
    val path = "/home/eli/Downloads/sample-audio.mp3"
    AudioFile(new File(path), ContentType(MediaTypes.`audio/mpeg`))
  }

  def sampleData: String = s"<int>${"42" * 1000000}</int>" // ~2MB of data

  def sizeInMb(length: Int): Int = length / (1024 * 1024)

  def sizeInKb(length: Int): Int = length / 1024

  def sizeInBytes(length: Int): Int = length

}
