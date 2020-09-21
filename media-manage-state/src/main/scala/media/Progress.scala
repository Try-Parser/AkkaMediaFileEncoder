package media.state.media

import java.io.PrintStream
import ws.schild.jave.progress.EncoderProgressListener
import ws.schild.jave.info.MultimediaInfo;

case class Progress(out: PrintStream = System.out, prefix: String = "") extends EncoderProgressListener {
	override def sourceInfo(info: MultimediaInfo): Unit = {
		out.println("source info: 000000000000000000000000000000 : --- > . < --- : 000000000000000000000000000000 " + info)
	}

	override def progress(permil: Int): Unit = {
		out.println("progress: 000000000000000000000000000000 : --- > . < --- : 000000000000000000000000000000 " + permil)
	}

	override def message(message: String): Unit = {
		out.println("message: 000000000000000000000000000000 : --- > . < --- : 000000000000000000000000000000 " + message)
	}
}