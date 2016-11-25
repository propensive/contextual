package contextual.examples

import contextual._
import dsls.sjson._

object Testing {
  
  def main(args: Array[String]): Unit = {
    
    val str = "some"

    println(sjson"""{ "foo$str" : "$str } """)
  }
}

