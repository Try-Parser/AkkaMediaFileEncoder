//package media.state.routes
//
//import scala.concurrent.Future
//import akka.actor.typed.ActorRef
//import akka.actor.typed.ActorSystem
//import akka.cluster.sharding.typed.scaladsl.ClusterSharding
//import akka.http.scaladsl.model.StatusCodes
//import akka.http.scaladsl.server.Route
//import akka.pattern.StatusReply
//import akka.util.Timeout
//import media.state.models.StateModel
//
//object StateRoutes {
//  final case class AddItem(cartId: String, itemId: String, quantity: Int)
//  final case class UpdateItem(cartId: String, itemId: String, quantity: Int)
//}
//
//class StateRoutes()(implicit system: ActorSystem[_]) {
//  implicit private val timeout: Timeout =
//    Timeout.create(system.settings.config.getDuration("media-manager-state.routes.ask-timeout"))
//  private val sharding = ClusterSharding(system)
//
//  import StateRoutes._
//  import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._
//  import akka.http.scaladsl.server.Directives._
//  import JsonFormatss._
//
//  val shopping: Route =
//    pathPrefix("shopping") {
//      pathPrefix("carts") {
//        concat(
//          post {
//            entity(as[AddItem]) {
//              data =>
//                val entityRef =
//                  sharding.entityRefFor(StateModel.EntityKey, data.cartId)
//                val reply: Future[StatusReply[StateModel.Summary]] =
//                  entityRef.ask(StateModel.AddItem(data.itemId, data.quantity, _))
//                onSuccess(reply) {
//                  case StatusReply.Success(summary: StateModel.Summary) =>
//                    complete(StatusCodes.OK -> summary)
//                  case StatusReply.Error(reason) =>
//                    complete(StatusCodes.BadRequest -> reason)
//                }
//            }
//          },
//          put {
//            entity(as[UpdateItem]) {
//              data =>
//                val entityRef =
//                  sharding.entityRefFor(StateModel.EntityKey, data.cartId)
//
//                def command(replyTo: ActorRef[StatusReply[StateModel.Summary]]) =
//                  if (data.quantity == 0)
//                    StateModel.RemoveItem(data.itemId, replyTo)
//                  else
//                    StateModel.AdjustItemQuantity(data.itemId, data.quantity, replyTo)
//
//                val reply: Future[StatusReply[StateModel.Summary]] =
//                  entityRef.ask(command(_))
//                onSuccess(reply) {
//                  case StatusReply.Success(summary: StateModel.Summary) =>
//                    complete(StatusCodes.OK -> summary)
//                  case StatusReply.Error(reason) =>
//                    complete(StatusCodes.BadRequest -> reason)
//                }
//            }
//          },
//          pathPrefix(Segment) { cartId =>
//            concat(get {
//              val entityRef =
//                sharding.entityRefFor(StateModel.EntityKey, cartId)
//              onSuccess(entityRef.ask(StateModel.Get)) { summary =>
//                if (summary.items.isEmpty) complete(StatusCodes.NotFound)
//                else complete(summary)
//              }
//            }, path("checkout") {
//              post {
//                val entityRef =
//                  sharding.entityRefFor(StateModel.EntityKey, cartId)
//                val reply: Future[StatusReply[StateModel.Summary]] =
//                  entityRef.ask(StateModel.Checkout(_))
//                onSuccess(reply) {
//                  case StatusReply.Success(summary: StateModel.Summary) =>
//                    complete(StatusCodes.OK -> summary)
//                  case StatusReply.Error(reason) =>
//                    complete(StatusCodes.BadRequest -> reason)
//                }
//              }
//            })
//          })
//      }
//    }
//
//}
//
//object JsonFormatss {
//
//  import spray.json.RootJsonFormat
//  import spray.json.DefaultJsonProtocol._
//
//  implicit val summaryFormat: RootJsonFormat[StateModel.Summary] =
//    jsonFormat2(StateModel.Summary)
//  implicit val addItemFormat: RootJsonFormat[StateRoutes.AddItem] =
//    jsonFormat3(StateRoutes.AddItem)
//  implicit val updateItemFormat: RootJsonFormat[StateRoutes.UpdateItem] =
//    jsonFormat3(StateRoutes.UpdateItem)
//
//}
