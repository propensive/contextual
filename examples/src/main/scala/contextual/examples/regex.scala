/* Contextual, version 0.14. Copyright 2016 Jon Pretty, Propensive Ltd.
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
package contextual.examples

import contextual._

import java.util.regex._

object regex {

  object RegexParser extends Interpolator {

    def implement(ctx: Contextual[StaticPart]): Seq[Ctx] = {
      import ctx.universe.{Literal => _, _}

      ctx.parts.foreach {
        case lit@Literal(_, string) =>
          try Pattern.compile(ctx.literals.head) catch {
            case p: PatternSyntaxException =>

              // We take only the interesting part of the error message
              val message = p.getMessage.split(" near").head
              lit.abort(p.getIndex - 1, message)
          }

        case hole@Hole(_, _) =>
          hole.abort("substitution is not supported")
      }

      Nil
    }

    def evaluate(ctx: Contextual[RuntimePart]): Pattern =
      Pattern.compile(ctx.parts.mkString)

  }

  implicit class RegexStringContext(sc: StringContext) {
    val regex = Prefix(RegexParser, sc)
  }

}
