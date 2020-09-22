package media.state.media

import java.io.PrintStream
import ws.schild.jave.progress.EncoderProgressListener
import ws.schild.jave.info.MultimediaInfo;

case class Progress(out: PrintStream = System.out, prefix: String = "") extends EncoderProgressListener {
	override def sourceInfo(info: MultimediaInfo): Unit = {
		out.println("000000000000000000000000000000 : --- > Source Info < --- : 000000000000000000000000000000")
		out.println(info)
		out.println("000000000000000000000000000000 : --- > Source Info < --- : 000000000000000000000000000000")
	}

	override def progress(permil: Int): Unit = {
		out.println(s"000000000000000000000000000000 : --- > Progress: ${(permil / 1000.00)*100} < --- : 000000000000000000000000000000")
	}

	override def message(message: String): Unit = {
		out.println("000000000000000000000000000000 : --- > Messages < --- : 000000000000000000000000000000")
		out.println(message)
		out.println("000000000000000000000000000000 : --- > Messages < --- : 000000000000000000000000000000")
	}
}