package utils.concurrent

trait FTE[Command, Event, State] {
	
	type CommandHandler[R[_, _]] = (State, Command) => R[Event, State]

	type EventHandler = (State, Event) => State

	type SourceBehavior[B[_], A[_]] = (A[Command]) => B[Command]
}