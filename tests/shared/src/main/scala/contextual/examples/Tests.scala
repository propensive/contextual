package contextual.examples

import contextual.examples._
import shell._
import email._
import binary._
import hex._

object Tests {

  object BinaryTest extends InterpolatorTest[BinParser.type] {
    def interpolator = BinParser

    override def eq(x: Array[Byte], y: Array[Byte]): Boolean =
      x.toList == y.toList

    def examples =
      List(
        (() => bin"00000000", Array(0.toByte)),
        (() => bin"11111111", Array(255.toByte)),
        (() => bin"00001111", Array(15.toByte)),
        (() => bin"01010101", Array(85.toByte)),
        (() => bin"0101010100001111", Array(85.toByte, 15.toByte))
      )
  }

  object EmailTest extends InterpolatorTest[EmailParser.type] {
    def interpolator = EmailParser

    def examples =
      List(
        (() => email"aaa@ddd.com", EmailAddress("aaa@ddd.com")),
        (() => email"complex+test@dep.company.eu", EmailAddress("complex+test@dep.company.eu"))
      )
  }

  object HexTest extends InterpolatorTest[HexParser.type] {
    def interpolator = HexParser

    override def eq(x: Array[Byte], y: Array[Byte]): Boolean =
      x.toList == y.toList

    def examples =
      List(
        (() => hex"00", Array(0.toByte)),
        (() => hex"ff", Array(255.toByte)),
        (() => hex"0f", Array(15.toByte)),
        (() => hex"55", Array(85.toByte)),
        (() => hex"550f", Array(85.toByte, 15.toByte))
      )
  }

  object ShTest extends InterpolatorTest[ShellInterpolator.type] {
    def interpolator = ShellInterpolator

    override def eq(x: Process, y: Process): Boolean =
      x.args.filter(_.nonEmpty).toList == y.args.filter(_.nonEmpty).toList

    def examples =
      List(
        (() => sh"""a b c d ${"e"} ${"f"}""", Process("a","b","c","d","e","f"))
      )
  }

  val tests: List[InterpolatorTest[_]] = List(BinaryTest, EmailTest, HexTest, ShTest)

  def runTests() = tests.foreach(_.runTests())
}
