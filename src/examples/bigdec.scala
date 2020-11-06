/*

    Contextual, version 1.5.0. Copyright 2016-20 Jon Pretty, Propensive OÜ.

    The primary distribution site is: https://propensive.com/

    Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in
    compliance with the License. You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

    Unless required by applicable law or agreed to in writing, software distributed under the License is
    distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
    See the License for the specific language governing permissions and limitations under the License.

*/
package contextual.examples

import contextual._

import language.experimental.macros

object bigDecimal {
  object BigDecimalParser extends Verifier[BigDecimal] {
    def check(string: String): Either[(Int, String), BigDecimal] =
      try Right(BigDecimal(string))
      catch { case e: NumberFormatException => Left((0, "could not parse decimal")) }
  }

  implicit class BigDecimalStringContext(sc: StringContext) {
    def d(expressions: String*): BigDecimal =
      macro Macros.contextual[BigDecimalParser.ContextType, BigDecimalParser.type]
  }
}

object bigInt {
  object BigIntParser extends Verifier[BigInt] {
    def check(string: String): Either[(Int, String), BigInt] =
      try Right(BigInt(string))
      catch { case e: NumberFormatException => Left((0, "could not parse integer")) }
  }

  implicit class BigIntStringContext(sc: StringContext) {
    def i(expressions: String*): BigInt =
      macro Macros.contextual[BigIntParser.ContextType, BigIntParser.type]
  }
}
