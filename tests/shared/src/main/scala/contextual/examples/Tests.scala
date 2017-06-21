package contextual.examples

/**
  * Created by marius on 03.05.17.
  */
object Tests {
  import contextual.examples._
  import shell._
  import email._
  import binary._

  def testEmailAndShell() = {
    val res = email"aaa@ddd.com"
    val res2 = sh"""a b c d ${"e"} ${"f"}"""

    println(res.address)
    println(res2.args)
  }

  def testBinary() = {
    val v1 = bin"011000010110000101100010"
    val b = "01100010"
    val v2 = bin"0110000101100001$b"

    println(v1.map(_.toChar).mkString)
    println(v2.map(_.toChar).mkString)
  }
}
