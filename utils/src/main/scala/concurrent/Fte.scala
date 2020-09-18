package utils.concurrent

import scala.concurrent.{ Future, Await }
import scala.concurrent.duration._

object FTE {
	def response[T](cb: T): FTE[T] = 
		FTE(Future(cb)(scala.concurrent.ExecutionContext.global))

	def response[T](cb: Future[T]): FTE[T] = FTE(cb)
}

case class Runtime(duration: Int) {
	def unsafeRun[T](f: Future[T]) = Await.result(f, duration.second) 
}

object Runtime {
	def default(): Runtime = Runtime(1) 
	def apply(duration: Long): Runtime = Runtime(duration)
}

case class FTE[T](cb: Future[T]) {
	def unsafeRun(): T = Runtime.default.unsafeRun(cb)
}