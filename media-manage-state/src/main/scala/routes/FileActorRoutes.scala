package media.state.routes

import akka.actor.typed.ActorSystem
import akka.cluster.sharding.typed.scaladsl.ClusterSharding
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import akka.pattern.StatusReply
import akka.util.Timeout
import media.state.json.JsonFormats._
import media.state.models.FileActorModel
import media.state.models.FileActorModel.File
import media.state.routes.FileActorRoutes.AddFile

import scala.concurrent.Future
import scala.concurrent.duration.DurationLong

final class FileActorRoutes(system: ActorSystem[_]) extends SprayJsonSupport {
  implicit private val timeout: Timeout = system
    .settings
    .config
    .getDuration("media-manager-state.routes.ask-timeout")
    .toMillis
    .millis

  private val sharding = ClusterSharding(system)


  val file: Route =
    pathPrefix("file") {
      concat(
        post {
          entity(as[AddFile]) {
            data =>
              val entityRef =
                sharding.entityRefFor(FileActorModel.TypeKey, data.file.fileId.toString)
              val reply: Future[StatusReply[FileActorModel.Get]] =
                entityRef.ask(FileActorModel.AddFile(data.file, _))
              onSuccess(reply) {
                case StatusReply.Success(summary: FileActorModel.Get) =>
                  complete(StatusCodes.OK -> summary)
                case StatusReply.Error(reason) =>
                  complete(StatusCodes.BadRequest -> reason)
              }
          }
        },
        pathPrefix(Segment) { fileId =>
          concat(
            get {
              val entityRef =
                sharding.entityRefFor(FileActorModel.TypeKey, fileId)
              onSuccess(entityRef.ask(FileActorModel.GetFile)) { summary =>
                complete(summary)
              }
            }
          )
        }
      )
    }
}

object FileActorRoutes {
  final case class AddFile(file: File)

  def apply(system: ActorSystem[_]): Route = {
    val route: FileActorRoutes = new FileActorRoutes(system)
    route.file
  }
}

