// import java.io.File
// import java.nio.file.Files

// import akka.actor.typed.scaladsl.adapter._
// import akka.http.scaladsl.model.Multipart
// import akka.http.scaladsl.testkit.ScalatestRouteTest
// import com.typesafe.config.{Config, ConfigFactory}
// import media.service.routes.ServiceRoutes
// import org.scalatest.BeforeAndAfterAll
// import org.scalatest.concurrent.PatienceConfiguration.Timeout
// import org.scalatest.funsuite.AnyFunSuite
// import org.scalatest.matchers.should.Matchers

// import scala.concurrent.duration._

// class ServerRoutesSpec extends AnyFunSuite
//   with Matchers
//   with BeforeAndAfterAll
//   with ScalatestRouteTest {

//   private def read(file: File): String = {
//     val source = scala.io.Source.fromFile(file, "UTF-8")
//     try source.mkString
//     finally source.close()
//   }

//   private val config = ConfigFactory.load()
//   private val basePath: String = config.getString("file-directory.base-path")
//   private val uploadedFilePath: String = config.getString("file-directory.upload.path")
//   private val convertFilePath: String = config.getString("file-directory.convert.path")
//   private val routes = ServiceRoutes(system.toTyped)
//   private val data = TestFiles.sampleData
//   private val audio = File.createTempFile("akka-http-temp", ".mp3")

//   private val testUploadFormData = {
//     val filePath = Files.write(audio.toPath, data.getBytes)
//     val contentType = TestFiles.sampleAudio.contentType
//     Multipart.FormData.fromPath("file", contentType, filePath, 1)
//   }

//   // Happy paths

//   private def doesPathExists(path: String): Boolean = {
//     val filePath = new File(path)
//     val pathExists: Boolean = filePath.exists()
//     if (!pathExists) 
//       filePath.mkdir()

//     true
//   }

//   test("the /upload directive should upload a file into the server") {
//     implicit val timeout: Timeout = Timeout(20.seconds)
//     Post("/upload", testUploadFormData) ~> routes ~> check {
//       val filePath = basePath + "/" + uploadedFilePath
//       assert(doesPathExists(filePath), s"File Uploaded directory should exist: $uploadedFilePath")
//       val convertPath = basePath + "/" + convertFilePath
//       assert(doesPathExists(convertPath), "File Convert directory should exists $convertFilePath")
//       val uploadedFile: File = new File(filePath)
//       assert(uploadedFile.exists(), "File was not uploaded")
//       assert(read(audio) == data, "Uploaded file does not have equal data with the source file")
//     }
//   }

//   // Error paths

//   test("Unsupported HTTP method for /upload should be rejected") {
//     Get("/upload") ~> routes ~> check {
//       responseAs[String] shouldEqual "Not allowed. Supported methods List(POST)"
//     }

//     Put("/upload") ~> routes ~> check {
//       responseAs[String] shouldEqual "Not allowed. Supported methods List(POST)"
//     }

//     Delete("/upload") ~> routes ~> check {
//       responseAs[String] shouldEqual "Not allowed. Supported methods List(POST)"
//     }
//   }

//   test("Invalid endpoint") {
//     Post("/uploadd", testUploadFormData) ~> routes ~> check {
//       responseAs[String] shouldEqual "The requested resource could not be found."
//     }
//   }

//   test("Unsupported request content type") {
//     Post("/upload") ~> routes ~> check {
//       responseAs[String] shouldEqual "Not allowed. Supported request content types List(Set(multipart/form-data))"
//     }
//   }
// }
