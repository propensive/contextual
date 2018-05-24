/*
  
  Contextual, version 1.1.0. Copyright 2018 Jon Pretty, Propensive Ltd.

  The primary distribution site is: https://propensive.com/

  Licensed under the Apache License, Version 2.0 (the "License"); you may not use
  this file except in compliance with the License. You may obtain a copy of the
  License at
  
      http://www.apache.org/licenses/LICENSE-2.0
 
  Unless required by applicable law or agreed to in writing, software
  distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
  WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
  License for the specific language governing permissions and limitations under
  the License.

*/
package contextual.data

import contextual._

object fqt {

  case class Fqt(name: String)

  object FqtParser extends Interpolator {

    type Output = Fqt

    def contextualize(interpolation: StaticInterpolation): Seq[ContextType] = Nil

    override def evaluator(contexts: Seq[ContextType], interpolation: StaticInterpolation):
        interpolation.universe.Tree = {
      import interpolation.universe.{Literal => _, _}
      import scala.reflect.macros._

      interpolation.parts.head match {
        case lit@Literal(index, string) =>
          val parsed = try interpolation.macroContext.parse(s"null.asInstanceOf[_root_.$string]") catch {
            case e: ParseException =>
              interpolation.abort(lit, e.pos.start, s"'$string' is not in the correct format for a type")
            case e: Exception =>
              interpolation.abort(lit, 0, s"some error occurred while reading the type")
          }
          try {
            val returnType = interpolation.macroContext.typecheck(parsed).tpe.toString
            q"_root_.contextual.data.fqt.Fqt($string)"
          } catch {
            case e: TypecheckException =>
              interpolation.abort(lit, e.pos.start, s"$string is not a valid type")
          }
        case hole: Hole =>
          interpolation.abort(hole, "compilation does not support substitutions")
      }
    }
  }

  implicit class FqtStringContext(sc: StringContext) { val fqt = Prefix(FqtParser, sc) }
}
