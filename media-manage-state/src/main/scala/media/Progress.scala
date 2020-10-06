package media.state.media

import java.io.PrintStream
import akka.actor.typed.ActorSystem
import akka.util.ByteString
import ws.schild.jave.progress.EncoderProgressListener
import ws.schild.jave.info.MultimediaInfo;
import media.state.handlers.SocketClient
import akka.http.scaladsl.model.ws.{TextMessage, BinaryMessage }

case class Progress(
	out: PrintStream = System.out, 
	prefix: String = "", 
	progressId: java.util.UUID = null
)(implicit system: ActorSystem[_]) extends EncoderProgressListener {

	private val (socket, connected) = SocketClient.getFlow()

	override def sourceInfo(info: MultimediaInfo): Unit = {
		out.println(s"000000000000000000000000000000 : --- > Source Info: $prefix < --- : 000000000000000000000000000000")
		out.println(info)
		out.println(s"000000000000000000000000000000 : --- > Source Info: $prefix < --- : 000000000000000000000000000000")
		connected.onComplete {
			_ => println("Socket connected")
		}(system.executionContext)
	}

	override def progress(permil: Int): Unit = {
		val percent = (permil / 1000.00)*100
		out.println(s"000000000000000000000000000000 : --- > Progress: ${percent} < --- : 000000000000000000000000000000")
		socket ! TextMessage.Strict(s""" { "id": "$progressId", "msg": "$percent" } """.toString)
		if(percent == 100.0) {
			println("end") 
			socket ! BinaryMessage.Strict(ByteString(0x00))
		}
	}

	override def message(message: String): Unit = {
		out.println(s"000000000000000000000000000000 : --- > Messages: $prefix < --- : 000000000000000000000000000000")
		out.println(message)
		out.println(s"000000000000000000000000000000 : --- > Messages: $prefix < --- : 000000000000000000000000000000")
	}
}