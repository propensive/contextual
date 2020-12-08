/*

    Contextual, version 3.0.0. Copyright 2016-20 Jon Pretty, Propensive OÜ.

    The primary distribution site is: https://propensive.com/

    Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in
    compliance with the License. You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

    Unless required by applicable law or agreed to in writing, software distributed under the License is
    distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
    See the License for the specific language governing permissions and limitations under the License.

*/
package contextual

import contextual.examples._

import scalac._
import probably._

object Tests extends Suite("Contextual tests") {

  def run(test: Runner): Unit = {
  }
}
/*
  object BinaryTest extends InterpolatorTest[BinParser.type] {
    def interpolator = BinParser

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
}*/
