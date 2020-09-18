import java.io.File
import java.nio.file.Files
import java.util.UUID

import akka.actor.testkit.typed.scaladsl.ActorTestKit
import akka.actor.typed.scaladsl.adapter._
import akka.http.scaladsl.model.Multipart
import akka.http.scaladsl.testkit.ScalatestRouteTest
import com.typesafe.config.{Config, ConfigFactory}
import media.service.routes.ServiceRoutes
import org.scalatest.BeforeAndAfterAll
import org.scalatest.concurrent.PatienceConfiguration.Timeout
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import routes.RejectionHandlers

import scala.concurrent.duration._
class ServerRoutesSpec extends AnyFunSuite
  with Matchers
  with BeforeAndAfterAll
  with ScalatestRouteTest
  with RejectionHandlers {

  private def read(file: File): String = {
    val source = scala.io.Source.fromFile(file, "UTF-8")
    try source.mkString
    finally source.close()
  }

  private val configs: Config = ConfigFactory.load()
  private val mediaServiceTestKit: ActorTestKit = ActorTestKit("media-service", configs)
  private val uploadedFilePath: String = ConfigFactory.load().getString("file-directory.upload.path")
  private val routes = ServiceRoutes(system.toTyped)
  private val data = TestFiles.sampleData
  private val audio = File.createTempFile("akka-http-temp", ".mp3")

  private val testUploadFormData = {
    val filePath = Files.write(audio.toPath, data.getBytes)
    val contentType = TestFiles.sampleAudio.contentType
    Multipart.FormData.fromPath("file", contentType, filePath, 1)
  }

  // Happy paths

  test("the /upload directive should upload a file into the server and return an JSON with file info") {
    implicit val timeout: Timeout = Timeout(20.seconds)
    Post("/upload", testUploadFormData) ~> routes ~> check {
      val uploadedFile: File = new File(uploadedFilePath)
      uploadedFile.exists() should be(true)
      read(audio) shouldEqual data
    }
  }

  // Error paths

  test("Unsupported HTTP method for /upload should be rejected") {
    Get("/upload") ~> routes ~> check {
      responseAs[String] shouldEqual "Not allowed. Supported methods List(POST)"
    }

    Put("/upload") ~> routes ~> check {
      responseAs[String] shouldEqual "Not allowed. Supported methods List(POST)"
    }

    Delete("/upload") ~> routes ~> check {
      responseAs[String] shouldEqual "Not allowed. Supported methods List(POST)"
    }
  }

  test("Invalid endpoint") {
    Post("/uploadd", testUploadFormData) ~> routes ~> check {
      responseAs[String] shouldEqual "The requested resource could not be found."
    }
  }

  test("Unsupported request content type") {
    Post("/upload") ~> routes ~> check {
      responseAs[String] shouldEqual "Not allowed. Supported request content types List(Set(multipart/form-data))"
    }
  }

  override def afterAll(): Unit = mediaServiceTestKit.shutdownTestKit()
}
