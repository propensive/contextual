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

import java.util.regex._

import language.experimental.macros

object regex {

  object RegexParser extends Interpolator {

    def contextualize(interpolation: StaticInterpolation): Seq[ContextType] = {

      interpolation.parts.foreach {
        case lit@Literal(_, string) =>
          try Pattern.compile(interpolation.literals.head) catch {
            case p: PatternSyntaxException =>

              // We take only the interesting part of the error message
              val message = p.getMessage.split(" near").head
              interpolation.error(lit, p.getIndex - 1, message)
          }

        case hole@Hole(_, _) =>
          interpolation.abort(hole, "substitution is not supported")
      }

      Nil
    }

    def evaluate(interpolation: RuntimeInterpolation): Pattern =
      Pattern.compile(interpolation.parts.mkString)

  }

  implicit class RegexStringContext(sc: StringContext) {
    def regex(expressions: String*): Pattern =
      macro Macros.contextual[RegexParser.ContextType, RegexParser.type]
  }

}
