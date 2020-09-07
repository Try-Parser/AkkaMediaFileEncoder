//package media.state.models
//
//import java.time.Instant
//
//import scala.concurrent.duration._
//import akka.actor.typed.ActorRef
//import akka.actor.typed.ActorSystem
//import akka.actor.typed.Behavior
//import akka.actor.typed.SupervisorStrategy
//import akka.cluster.sharding.typed.scaladsl.ClusterSharding
//import akka.cluster.sharding.typed.scaladsl.Entity
//import akka.cluster.sharding.typed.scaladsl.EntityTypeKey
//import akka.pattern.StatusReply
//import akka.persistence.typed.PersistenceId
//import akka.persistence.typed.scaladsl.RetentionCriteria
//import akka.persistence.typed.scaladsl.Effect
//import akka.persistence.typed.scaladsl.EventSourcedBehavior
//import akka.persistence.typed.scaladsl.ReplyEffect
//import media.state.events.EventProcessorSettings
//import utils.traits.{CborSerializable, Command}
//
//object StateModel {
//  final case class State(items: Map[String, Int], checkoutDate: Option[Instant]) extends CborSerializable {
//
//    def isCheckedOut: Boolean =
//      checkoutDate.isDefined
//
//    def hasItem(itemId: String): Boolean =
//      items.contains(itemId)
//
//    def isEmpty: Boolean =
//      items.isEmpty
//
//    def updateItem(itemId: String, quantity: Int): State = {
//      quantity match {
//        case 0 => copy(items = items - itemId)
//        case _ => copy(items = items + (itemId -> quantity))
//      }
//    }
//
//    def removeItem(itemId: String): State =
//      copy(items = items - itemId)
//
//    def checkout(now: Instant): State =
//      copy(checkoutDate = Some(now))
//
//    def toSummary: Summary =
//      Summary(items, isCheckedOut)
//  }
//  object State {
//    val empty = State(items = Map.empty, checkoutDate = None)
//  }
//
//  final case class AddItem(itemId: String, quantity: Int, replyTo: ActorRef[StatusReply[Summary]]) extends Command
//  final case class RemoveItem(itemId: String, replyTo: ActorRef[StatusReply[Summary]]) extends Command
//  final case class AdjustItemQuantity(itemId: String, quantity: Int, replyTo: ActorRef[StatusReply[Summary]]) extends Command
//  final case class Checkout(replyTo: ActorRef[StatusReply[Summary]]) extends Command
//  final case class Get(replyTo: ActorRef[Summary]) extends Command
//  final case class Summary(items: Map[String, Int], checkedOut: Boolean) extends CborSerializable
//
//  sealed trait Event extends CborSerializable {
//    def cartId: String
//  }
//
//  final case class ItemAdded(cartId: String, itemId: String, quantity: Int) extends Event
//  final case class ItemRemoved(cartId: String, itemId: String) extends Event
//  final case class ItemQuantityAdjusted(cartId: String, itemId: String, newQuantity: Int) extends Event
//  final case class CheckedOut(cartId: String, eventTime: Instant) extends Event
//
//  val EntityKey: EntityTypeKey[Command] =
//    EntityTypeKey[Command]("State")
//
//  def init(system: ActorSystem[_], eventProcessorSettings: EventProcessorSettings): Unit = {
//    ClusterSharding(system).init(Entity(EntityKey) { entityContext =>
//      val n = math.abs(entityContext.entityId.hashCode % eventProcessorSettings.parallelism)
//      val eventProcessorTag = eventProcessorSettings.tagPrefix + "-" + n
//      StateModel(entityContext.entityId, Set(eventProcessorTag))
//    }.withRole("write-model"))
//  }
//
//  def apply(cartId: String, eventProcessorTags: Set[String]): Behavior[Command] = {
//    EventSourcedBehavior
//      .withEnforcedReplies[Command, Event, State](
//        PersistenceId(EntityKey.name, cartId),
//        State.empty,
//        (state, command) =>
//          if (state.isCheckedOut) checkedOutShoppingCart(cartId, state, command)
//          else openShoppingCart(cartId, state, command),
//        (state, event) => handleEvent(state, event))
//      .withTagger(_ => eventProcessorTags)
//      .withRetention(RetentionCriteria.snapshotEvery(numberOfEvents = 100, keepNSnapshots = 3))
//      .onPersistFailure(SupervisorStrategy.restartWithBackoff(200.millis, 5.seconds, 0.1))
//  }
//
//  private def openShoppingCart(cartId: String, state: State, command: Command): ReplyEffect[Event, State] =
//    command match {
//      case AddItem(itemId, quantity, replyTo) =>
//        if (state.hasItem(itemId))
//          Effect.reply(replyTo)(StatusReply.Error(s"Item '$itemId' was already added to this shopping cart"))
//        else if (quantity <= 0)
//          Effect.reply(replyTo)(StatusReply.Error("Quantity must be greater than zero"))
//        else
//          Effect
//            .persist(ItemAdded(cartId, itemId, quantity))
//            .thenReply(replyTo)(updatedCart => StatusReply.Success(updatedCart.toSummary))
//
//      case RemoveItem(itemId, replyTo) =>
//        if (state.hasItem(itemId))
//          Effect
//            .persist(ItemRemoved(cartId, itemId))
//            .thenReply(replyTo)(updatedCart => StatusReply.Success(updatedCart.toSummary))
//        else
//          Effect.reply(replyTo)(StatusReply.Success(state.toSummary)) // removing an item is idempotent
//
//      case AdjustItemQuantity(itemId, quantity, replyTo) =>
//        if (quantity <= 0)
//          Effect.reply(replyTo)(StatusReply.Error("Quantity must be greater than zero"))
//        else if (state.hasItem(itemId))
//          Effect
//            .persist(ItemQuantityAdjusted(cartId, itemId, quantity))
//            .thenReply(replyTo)(updatedCart => StatusReply.Success(updatedCart.toSummary))
//        else
//          Effect.reply(replyTo)(
//            StatusReply.Error(s"Cannot adjust quantity for item '$itemId'. Item not present on cart"))
//
//      case Checkout(replyTo) =>
//        if (state.isEmpty)
//          Effect.reply(replyTo)(StatusReply.Error("Cannot checkout an empty shopping cart"))
//        else
//          Effect
//            .persist(CheckedOut(cartId, Instant.now()))
//            .thenReply(replyTo)(updatedCart => StatusReply.Success(updatedCart.toSummary))
//
//      case Get(replyTo) =>
//        Effect.reply(replyTo)(state.toSummary)
//    }
//
//  private def checkedOutShoppingCart(cartId: String, state: State, command: Command): ReplyEffect[Event, State] =
//    command match {
//      case Get(replyTo) =>
//        Effect.reply(replyTo)(state.toSummary)
//      case cmd: AddItem =>
//        Effect.reply(cmd.replyTo)(StatusReply.Error("Can't add an item to an already checked out shopping cart"))
//      case cmd: RemoveItem =>
//        Effect.reply(cmd.replyTo)(StatusReply.Error("Can't remove an item from an already checked out shopping cart"))
//      case cmd: AdjustItemQuantity =>
//        Effect.reply(cmd.replyTo)(StatusReply.Error("Can't adjust item on an already checked out shopping cart"))
//      case cmd: Checkout =>
//        Effect.reply(cmd.replyTo)(StatusReply.Error("Can't checkout already checked out shopping cart"))
//    }
//
//  private def handleEvent(state: State, event: Event) = {
//    event match {
//      case ItemAdded(_, itemId, quantity)            => state.updateItem(itemId, quantity)
//      case ItemRemoved(_, itemId)                    => state.removeItem(itemId)
//      case ItemQuantityAdjusted(_, itemId, quantity) => state.updateItem(itemId, quantity)
//      case CheckedOut(_, eventTime)                  => state.checkout(eventTime)
//    }
//  }
//}
