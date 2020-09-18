package utils.file

import java.util.UUID
import java.time.Instant
import java.io.File
import com.typesafe.config.{ Config, ConfigFactory }

case class FileHandler(config: Config) {
	val uploadFilePath: String = config.getString("file-directory.upload.path")
	val maxContentSize: Long  = config.getLong("file-directory.upload.max-content-size")
	val convertFilePath: String = config.getString("file-directory.convert.path")

	def getFile(fileName: String, upload: Boolean = true): File = 
		new File(s"${if(upload) uploadFilePath else convertFilePath}/$fileName")

	def getExt(fileName: String): String = {
		val fullName: Array[String] = fileName.split("\\.")
		if(fullName.size <= 1) "tmp" else fullName(fullName.size-1)
	}

	def generateName(oldName: String): String = 
		s"${UUID.randomUUID.toString}-${Instant.now.getEpochSecond.toString}.${getExt(oldName)}"
}

object FileHandler {
	def apply(config: ConfigFactory): FileHandler = FileHandler(config)
	def apply(): FileHandler = FileHandler(ConfigFactory.load())
}