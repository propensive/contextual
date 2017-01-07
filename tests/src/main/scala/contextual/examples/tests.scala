/* Contextual, version 1.0.0. Copyright 2016 Jon Pretty, Propensive Ltd.
 *
 * The primary distribution site is: http://co.ntextu.al/
 * 
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software distributed under the
 * License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied. See the License for the specific language governing permissions
 * and limitations under the License.
 */
package contextual.tests

object Testing {
  
  def main(args: Array[String]): Unit = {
    
    val str = "some"

    import contextual.examples._
    import shell._
    import scala2._
    import scalac._

    sh"foo bar $str baz"
    sh"""a b c d ${"e"} ${"f"}"""

    println(scala"21 + 21") // 42

    val ctx: Context = scalac"object D { def apply(i: Int) = i * 2 }"
    println(ctx("D(21)")) // 42

    // does not compile
    // tests.scala:38: Compiler exception error: line 1: not found: value x
    // scala"x"

    // does not compile
    // tests.scala:42: Compiler exception error: line -1: expected class or object definition
    // scalac"x"
  }
}

