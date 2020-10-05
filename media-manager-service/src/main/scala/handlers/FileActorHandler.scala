package media.service.handlers

import utils.concurrent.SysLog
import utils.traits.{
	Command,
	Response
}
import java.util.UUID

import akka.actor.typed.scaladsl.AskPattern.{
	Askable,
	schedulerFromActorSystem}
import akka.actor.typed.scaladsl.Behaviors

import scala.concurrent.Future
import akka.util.Timeout
import akka.cluster.sharding.typed.scaladsl.{
	ClusterSharding,
	Entity
}
import akka.actor.typed.{
	ActorSystem,
	SupervisorStrategy
}
import akka.cluster.typed.{
	ClusterSingleton,
	SingletonActor
}
import media.state.models.actors.FileActor.{
	AddFile,
	CompressFile,
	ConvertFile,
	File,
	FileNotFound,
	FileProgress,
	Get,
	GetFile,
	MediaDescription,
	TypeKey,
	createBehavior => CreateBehavior
}
import media.state.events.EventProcessorSettings
import media.fdk.json.{
	MultiMedia,
	PreferenceSettings
}
import media.state.models.actors.FileActorListModel
import spray.json.{
	JsObject,
	JsString
}


private[service] class FileActorHandler(shards: ClusterSharding, sys: ActorSystem[_])
	(implicit timeout: Timeout) extends SysLog(sys.log) {

	implicit private val sett = EventProcessorSettings(sys)
	implicit private val system = sys

	val regionId = com.typesafe.config.ConfigFactory
		.load()
		.getString("media-manager-service.region.id")
	
	shards.init(Entity(TypeKey)(CreateBehavior))

	// def uploadFile(meta: FileInfo, byteSource: Source[ByteString, _]): Future[MultiMedia] = 
	// 	byteSource.runFold(ByteString.empty)(_ ++ _).flatMap { byteS => 
	// 		println(s"Message upload : ${byteS.size}")
	// 		shards.entityRefFor(TypeKey, UUID.randomUUID.toString)
	// 			.ask(AddFile(File(
	// 				meta.fileName, 
	// 				byteS.toArray, 
	// 				meta.contentType.toString,
	// 				0
	// 			), _)).map(extractMedia)(sys.executionContext)
	// 	}(sys.executionContext)

	def uploadFile(newName: String, contentType: String, regionId: UUID): Future[MultiMedia] = 
		shards.entityRefFor(TypeKey, regionId.toString)
			.ask(AddFile(File(newName, contentType, 0), _))
			.map(extractMedia)(sys.executionContext)

	def transferFile(name: String, data: Array[Byte], regionId: UUID): Future[Response] =
		shards.entityRefFor(TypeKey, regionId.toString)
			.ask(CompressFile(data, name, _))

	def getFile(fileId: UUID): Future[Response] = {
		/*** test to get the region state 
		import akka.cluster.sharding.typed.GetShardRegionState
		import akka.cluster.sharding.ShardRegion.CurrentShardRegionState
		import akka.actor.typed.scaladsl.Behaviors

		val replyTo: ActorRef[CurrentShardRegionState] = 
			sys.systemActorOf[CurrentShardRegionState](Behaviors.receive { (context, message) =>
				println(s"00000000000000000000000000000000000000000000000000000000000000000")
				println(context)
				println(context.getClass)
				println(message)
				println(s"00000000000000000000000000000000000000000000000000000000000000000")
				Behaviors.stopped
			}, "region-state")

		shards.shardState ! GetShardRegionState(TypeKey, replyTo)
		 END ***/
		
		shards.entityRefFor(TypeKey, fileId.toString)
			.ask(GetFile(_))
	}

	val singletonActor =
		ClusterSingleton(sys)
			.init(SingletonActor(Behaviors.supervise(FileActorListModel())
				.onFailure[Exception](SupervisorStrategy.restart), "FileListActor"))

	def getFiles(key: Option[String]): Future[Command] = {
		singletonActor.ask(FileActorListModel.GetFiles(key, _))
	}

	def startConvert(mm: PreferenceSettings): Future[JsObject] = {
		getFile(mm.id).flatMap { 
			case Get(file, status) => 
				convertFile(mm.updateFileName(file.fileName))
			case FileNotFound => Future(JsObject(
				"id" -> JsString(mm.id.toString),
				"reason" -> JsString("file not found.")))(sys.executionContext)
			case _ => 
				Future(JsObject("error" -> JsString("unknown")))(sys.executionContext)
		}(sys.executionContext)
	}

	def convertFile(mm: PreferenceSettings): Future[JsObject] = 
		shards.entityRefFor(TypeKey, UUID.randomUUID.toString)
			.ask(ConvertFile(mm, _)).map { case FileProgress(fileName, id, progress) => 
				JsObject(
					"file_name" -> JsString(fileName),
					"id" -> JsString(id.toString),
					"status" -> JsString(progress)
				)
			}(sys.executionContext)

	private def extractMedia(media: MediaDescription): MultiMedia = 
		MultiMedia(media.mediaInfo, media.duration, media.format)

}

private[service] object FileActorHandler {
	def apply(shards: ClusterSharding)(implicit t: Timeout, sys: ActorSystem[_]): FileActorHandler = 
		new FileActorHandler(shards, sys)
}