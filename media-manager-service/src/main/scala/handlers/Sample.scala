package handlers

import akka.util.ByteString

sealed trait Event
case class Element(content: ByteString) extends Event
case object ReachedEnd extends Event
case class FailureOccured(ex: Exception) extends Event
