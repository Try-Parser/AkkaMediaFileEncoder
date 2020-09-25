package utils.actors

import akka.actor.typed.{
	ActorSystem,
	ActorRef,
	Behavior
}
import akka.cluster.sharding.typed.ShardingEnvelope
import akka.cluster.sharding.typed.scaladsl.{
	ClusterSharding,
	Entity,
	EntityContext,
	EntityTypeKey
}
import akka.actor.typed.scaladsl.Behaviors
import utils.traits.CborSerializable
import utils.concurrent.FTE

import scala.reflect._

class Actor[C <: ShardActor[_, _, _]](implicit cTag: ClassTag[C]) {
	val actor = cTag
		.runtimeClass
		.getDeclaredConstructor()
		.newInstance()
		.asInstanceOf[C]
}

class ShardActor[Command <: CborSerializable, Event <: CborSerializable, State](name: String) extends FTE[Command, Event, State] {
	implicit val actorName: String = name

	def setupSource(ev: SourceBehavior[Behavior, ActorRef]): Behavior[Command] = 
		Behaviors.setup(ctx => ev(ctx.self))

	def init(key: EntityTypeKey[Command])(createBehavior: EntityContext[Command] => Behavior[Command])
		(implicit sys: ActorSystem[_]): Unit =
			ClusterSharding(sys).init(Entity(key)(createBehavior))

	def init(
		key: EntityTypeKey[Command],
		createBehavior: EntityContext[Command] => Behavior[Command]
	)(entityM: Entity[Command, ShardingEnvelope[Command]] => Entity[Command, ShardingEnvelope[Command]])(implicit sys: ActorSystem[_]): Unit =
		ClusterSharding(sys).init(entityM(Entity(key)(createBehavior)))
}