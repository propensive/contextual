package contextual.tests

import contextual._

object Testing {
  
  def main(args: Array[String]): Unit = {
    
    val str = "some"

    import contextual.examples._
    import shell._

    sh"foo bar $str baz"
    sh"""a b c d ${"e"} ${"f"}"""

  }
}

