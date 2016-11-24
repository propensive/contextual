package contextual.examples

import contextual._
import dsls._

object Testing {
  
  def main(args: Array[String]): Unit = {
    
    val name = "some"
    val sym = 'foo

    val x = simple"""Here is a $sym $name with the same value "in $name $sym quotes"."""
    
    println(x)
  }
}

