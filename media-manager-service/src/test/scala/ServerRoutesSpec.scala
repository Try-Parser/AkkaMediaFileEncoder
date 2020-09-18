import java.io.File
import java.nio.file.Files
import java.util.UUID

import akka.actor.testkit.typed.scaladsl.{ActorTestKit, ActorTestKitBase}
import akka.actor.typed.DispatcherSelector
import akka.actor.typed.scaladsl.adapter._
import akka.cluster.sharding.ClusterSharding
import akka.http.scaladsl.model.Multipart
import akka.http.scaladsl.testkit.ScalatestRouteTest
import com.typesafe.config.{Config, ConfigFactory}
import media.service.routes.ServiceRoutes
import org.scalatest.BeforeAndAfterAll
import org.scalatest.concurrent.Eventually
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import routes.RejectionHandlers

import scala.concurrent.ExecutionContextExecutor

class ServerRoutesSpec extends AnyFunSuite
  with Matchers
  with BeforeAndAfterAll
  with ScalatestRouteTest
  with Eventually
  with RejectionHandlers {

  private val configs: Config = ConfigFactory.load()
  val actorTestKit: ActorTestKit = ActorTestKit(ActorTestKitBase.testNameFromCallStack(), configs)

  implicit val ec: ExecutionContextExecutor = actorTestKit.system.dispatchers.lookup(DispatcherSelector.fromConfig("my-dispatcher"))
  //  implicit val ec: ExecutionContextExecutor = actorTestKit.system.dispatchers.lookup(DispatcherSelector.blocking())

  // Happy paths

  private val uploadedFilePath: String = ConfigFactory.load().getString("upload.path")
  val routes = ServiceRoutes(system.classicSystem.toTyped)

  private def read(file: File): String = {
    val source = scala.io.Source.fromFile(file, "UTF-8")
    try source.mkString
    finally source.close()
  }

  test("the /upload directive should upload a file into the server and return an JSON with file info") {

    implicit val cluster: ClusterSharding = ClusterSharding(system)
    val data = TestFiles.sampleData
//    val audio = TestFiles.sampleAudio
    val audio = File.createTempFile("akka-http-FileUploadDirectivesSpec", ".mp3")

    val filePath = Files.write(audio.toPath, data.getBytes)
    val contentType = TestFiles.sampleAudio.contentType
    val formData = Multipart.FormData.fromPath("file", contentType, filePath, 1)

    Post("/upload", formData) ~> routes ~> check {
      val uploadedFile: File = new File(uploadedFilePath)
      uploadedFile.exists() should be(true)
      read(audio) shouldEqual data
      response.status should be(200)
    }
  }

  test("the /convert directive should convert a file to a target format") {}

  test("the /convertStatus directive should c") {}

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

  test("Unsupported HTTP method for /play should be rejected") {
    val uuid = UUID.randomUUID()

    Post(s"/play/$uuid") ~> routes ~> check {
      responseAs[String] shouldEqual "Not allowed. Supported methods List(GET)"
    }

//    Put(s"/play/$uuid") ~> service ~> check {
//      responseAs[String] shouldEqual "Not allowed. Supported methods List(GET)"
//    }
//
//    Delete(s"/play/$uuid") ~> service ~> check {
//      responseAs[String] shouldEqual "Not allowed. Supported methods List(GET)"
//    }
  }

  test("Invalid endpoint") {
    Post("/upload/invalid-path") ~> routes ~> check {
      responseAs[String] shouldEqual "Page not found."
    }

    Post("/invalidddd") ~> routes ~> check {
      responseAs[String] shouldEqual "Page not found."
    }

    Get("/invalidddd") ~> routes ~> check {
      responseAs[String] shouldEqual "Page not found."
    }
  }

  test("Unsupported request content type") {
    Post("/upload") ~> routes ~> check {
      responseAs[String] shouldEqual "Not allowed. Supported request content types List(Set(multipart/form-data))"
    }
  }

  override def afterAll(): Unit = actorTestKit.shutdownTestKit()
}
