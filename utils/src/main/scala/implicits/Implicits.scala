package utils.implicits

object Global {
	implicit class GuardInt(value: Int) {
		val NonZeroInt: Int => Option[Int] = i => if (i <= 0) None else Some(i)
		val NonEmptyString: String => Option[String] = s => if (s.isEmpty) None else Some(s)
	}
}