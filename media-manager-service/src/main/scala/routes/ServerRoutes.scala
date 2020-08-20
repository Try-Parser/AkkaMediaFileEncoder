package media.service.routes

import scala.concurrent.duration._
import scala.concurrent.Future
import scala.concurrent.ExecutionContext
import scala.util.{ Success, Failure }

import akka.actor.typed.ActorSystem

import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import akka.http.scaladsl.model.StatusCodes

import akka.cluster.sharding.typed.scaladsl.ClusterSharding

import akka.util.Timeout

import media.service.models.ServiceEncoder.{ 
	Data,
	Information,
	TKey,
	AddRecord,
	GetAllData,
	AllInfo
}

import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport

private[service] final class ServiceRoutes(system: ActorSystem[_]) extends SprayJsonSupport  {

	// shards
	private val sharding = ClusterSharding(system)

	// Actor Timeout
	implicit private val timeout: Timeout = system
		.settings
		.config
		.getDuration("media-manager-service.routes.ask-timeout")
		.toMillis
		.millis

	// f[] 
	private def processData(
		eid: Long, 
		data: Data
	): Future[Information] = sharding
		.entityRefFor(TKey, eid.toString)
		.ask(AddRecord(data, _))

	private def getAllData(eid: Long): Future[AllInfo] = 
		sharding
		.entityRefFor(TKey, eid.toString)
		.ask(GetAllData(_))

	val testFunc: Route = path("test" / LongNumber) { eid =>
		concat(
			get {
				onComplete(getAllData(eid)) { 
					case Success(info) => complete(info.toJson)
					case Failure(info) => complete("Invalid request")
				}
			},
			post {
				formFields("data") { data =>
					onSuccess(processData(eid, Data(data.toLong))) { p => 
						complete(StatusCodes.Accepted -> s"performed in ${p.eid}")
					}
				}
			}
		)
	}

	val uploadFile: Route = path("upload") {
		get {
			complete("uploadFile")
		}
	}	

	val convertFile: Route = path("convert") {
		get {
			complete("convertFile")
		}
	}	

	val convertStatus: Route = path("status") {
		get {
			complete("convertStatus")
		}
	}	

	val playFile: Route = path("play") {
		get {
			complete("playFile")
		}
	}
}

object ServiceRoutes {
	def apply(system: ActorSystem[_]): Route = {
		val route: ServiceRoutes = new ServiceRoutes(system)
		route.uploadFile ~ route.convertFile ~ route.convertStatus ~ route.playFile	~ route.testFunc
	}
}