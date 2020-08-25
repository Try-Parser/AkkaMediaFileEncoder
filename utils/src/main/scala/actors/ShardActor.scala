package utils.actors

import scala.reflect._

import akka.actor.typed.{
	ActorSystem,
	Behavior
}

import akka.cluster.sharding.typed.scaladsl.{
	ClusterSharding,
	Entity,
	EntityTypeKey,
	EntityContext
}

import utils.traits.CborSerializable

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
}