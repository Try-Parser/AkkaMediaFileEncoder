package utils.concurrent

import org.slf4j.Logger

class SysLog(log: Logger) {
	def info(message: String): Unit = log.info(s"00000000000   $message    00000000000")
	def debug(message: String): Unit = log.debug(s"00000000000   $message    00000000000")
	def error(message: String): Unit = log.debug(s"00000000000   $message    00000000000")
	def warn(message: String): Unit = log.debug(s"00000000000   $message    00000000000")
}