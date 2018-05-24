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
import fqt._

object scalac {

  sealed trait Compilation {
    def returns: Option[Fqt]
    def success: Boolean = returns.isDefined
  }
  case class TypecheckError(message: String) extends Compilation { def returns = None }
  case class Returns(returnType: Fqt) extends Compilation { def returns = Some(returnType) }

  object ScalacParser extends Interpolator {

    type Output = Compilation

    def contextualize(interpolation: StaticInterpolation): Seq[ContextType] = Nil

    override def evaluator(contexts: Seq[ContextType], interpolation: StaticInterpolation):
        interpolation.universe.Tree = {
      import interpolation.universe.{Literal => _, _}
      import scala.reflect.macros._

      interpolation.parts.head match {
        case lit@Literal(index, string) =>
          val parsed = try interpolation.macroContext.parse(string) catch {
            case e: ParseException =>
              interpolation.abort(lit, e.pos.start, s"failed to parse Scala: ${e.msg}")
          }
          try {
            val returnType = interpolation.macroContext.typecheck(parsed).tpe.toString
            q"""_root_.contextual.data.scalac.Returns(
                _root_.contextual.data.fqt.Fqt($returnType)): Compilation"""
          } catch {
            case e: TypecheckException =>
              q"""_root_.contextual.data.scalac.TypecheckError(${e.msg}): Compilation"""
          }
        case hole: Hole =>
          interpolation.abort(hole, "compilation does not support substitutions")
      }
    }
  }

  implicit class ScalacStringContext(sc: StringContext) { val scalac = Prefix(ScalacParser, sc) }
}
