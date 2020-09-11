package media.state.handlers

import akka.Done
import akka.actor.typed.ActorSystem
import akka.actor.typed.eventstream.EventStream
import akka.projection.eventsourced.EventEnvelope
import akka.projection.scaladsl.Handler
import org.slf4j.LoggerFactory
import utils.traits.Event

import scala.concurrent.Future

class StateProjectionHandler(
  tag: String, 
  system: ActorSystem[_]) extends Handler[EventEnvelope[Event]] {
    val log = LoggerFactory.getLogger(getClass)

    override def process(envelope: EventEnvelope[Event]): Future[Done] = {
      log.info("EventProcessor({}) consumed {} from {} with seqNr {}",
        tag,
        envelope.event,
        envelope.persistenceId,
        envelope.sequenceNr)
      system.eventStream ! EventStream.Publish(envelope.event)
      Future.successful(Done)
    }
}
