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

object shell {

  case class Process(args: String*) {
    override def toString = args.filter(!_.isEmpty).mkString("Process(", ", ", ")")
  }

  sealed trait ShellContext extends Context
  case object InSingleQuotes extends ShellContext
  case object InDoubleQuotes extends ShellContext
  case object InUnquotedParam extends ShellContext
  case object NewParam extends ShellContext

  object ShellInterpolator extends Interpolator {
    type ContextType = ShellContext
    type Input = String
    type Output = Process

    def evaluate(interpolation: RuntimeInterpolation): Process = {
      val command = interpolation.parts.mkString
      val (_, params) = parseLiteral(NewParam, command)
      Process(params: _*)
    }

    def contextualize(interpolation: StaticInterpolation): Seq[ContextType] = {
      val (contexts, finalState) = interpolation.parts.foldLeft((List[ContextType](), NewParam:
          ShellContext)) {
        case ((contexts, state), lit@Literal(_, string)) =>
          val (newState, _) = parseLiteral(state, string)
          (contexts, newState)

        case ((contexts, state), hole@Hole(_, _)) =>
          val newState = hole(state).getOrElse(interpolation.abort(hole,
              "this type cannot be substituted here"))
          (newState :: contexts, newState)
      }

      if(finalState == InSingleQuotes || finalState == InDoubleQuotes) {
        val lit@Literal(_, _) = interpolation.parts.last
        interpolation.abort(lit, lit.string.length, "unclosed quoted parameter")
      }

      contexts
    }

    private def parseLiteral(state: ContextType, string: String): (ContextType, List[String]) =
      string.foldLeft((state, List[String](""))) {
        case ((NewParam, params), ' ') =>
          (NewParam, params)
        
        case ((InUnquotedParam, params), ' ') =>
          (NewParam, params :+ "")
        
        case ((InSingleQuotes, params), '\'') =>
          (InUnquotedParam, params)
        
        case ((InDoubleQuotes, params), '"') =>
          (InUnquotedParam, params)
        
        case ((InUnquotedParam | NewParam, params), '"') =>
          (InDoubleQuotes, params)
        
        case ((InUnquotedParam | NewParam, params), '\'') =>
          (InSingleQuotes, params)
        
        case ((NewParam, params), ch) =>
          (InUnquotedParam, params :+ s"$ch")
        
        case ((state, rest :+ cur), ch) =>
          (state, rest :+ s"$cur$ch")
      }
    }

  implicit val embedStrings = ShellInterpolator.embed[String](
    Case(NewParam, InUnquotedParam) { s => '"'+s.replaceAll("\\\"", "\\\\\"")+'"' },
    Case(InUnquotedParam, InUnquotedParam) { s => '"'+s.replaceAll("\\\"", "\\\\\"")+'"' },
    Case(InSingleQuotes, InSingleQuotes) { s => s.replaceAll("'", """'"'"'""") },
    Case(InDoubleQuotes, InDoubleQuotes) { s => s.replaceAll("\\\"", "\\\\\"") }
  )
  
  implicit class ShellStringContext(sc: StringContext) {
    def sh(expressions: String*): Process =
      macro Macros.contextual[ShellContext, ShellInterpolator.type]
  }

}
