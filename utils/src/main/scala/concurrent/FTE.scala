package utils.concurrent

import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{Behavior, ActorRef}

trait FTE {
	type CMD[R[_, _], C, E, S] = (S, C) => R[E, S]
	type EVT[S, E] = (S, E) => S
}

object FTE {
	type BH[C, B[_]] = B[C]

	type BHC[C] = (ActorRef[C]) => Behavior[C]

	def behave[C](ev: BHC[C]): Behavior[C]  = 
		Behaviors.setup(ctx => ev(ctx.self))	
}
