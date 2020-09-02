package utils.implicits

object Global {
	implicit class GuardInt(value: Int) {
		def nonZeroInt(): Option[Int] =
			 if (value <= 0) None else Some(value)
	}

	implicit class GuardString(value: String) {
		def nonEmptyString(): Option[String] =
			if (value.trim.isEmpty) None else Some(value.trim)
	}
}