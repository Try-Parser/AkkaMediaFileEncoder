package utils.actors

import akka.actor.typed.{ActorSystem, Behavior}
import akka.cluster.sharding.typed.ShardingEnvelope
import akka.cluster.sharding.typed.scaladsl.{ClusterSharding, Entity, EntityContext, EntityTypeKey}
import utils.traits.CborSerializable

import scala.reflect._

class Actor[C <: ShardActor[_]](implicit cTag: ClassTag[C]) {
	val actor = cTag
		.runtimeClass
		.getDeclaredConstructor()
		.newInstance()
		.asInstanceOf[C]
}

class ShardActor[T <: CborSerializable](name: String) {
	implicit val actorName: String = name

	def init(key: EntityTypeKey[T])(createBehavior: EntityContext[T] => Behavior[T])
		(implicit sys: ActorSystem[_]): Unit =
			ClusterSharding(sys).init(Entity(key)(createBehavior))

	def init(key: EntityTypeKey[T], createBehavior: EntityContext[T] => Behavior[T])( entityM: Entity[T, ShardingEnvelope[T]] => Entity[T, ShardingEnvelope[T]])(implicit sys: ActorSystem[_]): Unit =
		ClusterSharding(sys).init(entityM(Entity(key)(createBehavior)))

}