package contextual.examples

/**
  * Created by marius on 03.05.17.
  */
object Tests {
  import shell._
  import email._

  def testEmailAndShell() = {
    val res = email"aaa@ddd.com"
    val res2 = sh"""a b c d ${"e"} ${"f"}"""

    println(res.address)
    println(res2.args)
  }
}
