package media.state.media

import java.io.PrintStream
import ws.schild.jave.progress.EncoderProgressListener
import ws.schild.jave.info.MultimediaInfo;

case class Progress(out: PrintStream = System.out, prefix: String = "") extends EncoderProgressListener {
	override def sourceInfo(info: MultimediaInfo): Unit = {
		out.println("source info: >>>>>>>>>>>>>>>>>> " + info)
	}

	override def progress(permil: Int): Unit = {
		out.println("progress: >>>>>>>>>>>>>>>>>> " + permil)
	}

	override def message(message: String): Unit = {
		out.println("message: >>>>>>>>>>>>>>>>>> " + message)
	}
}