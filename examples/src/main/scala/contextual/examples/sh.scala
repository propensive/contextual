/* Contextual, version 0.13. Copyright 2016 Jon Pretty, Propensive Ltd.
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
    type Ctx = ShellContext
    type Inputs = String

    def evaluate(ctx: Contextual[RuntimePart]): Process = {
      val command = ctx.parts.mkString
      val (_, params) = parseLiteral(NewParam, command)
      Process(params: _*)
    }

    def implement(ctx: Contextual[StaticPart]): ctx.Implementation = {
      import ctx.universe.{Literal => _, _}

      val (contexts, finalState) = ctx.parts.foldLeft((List[Ctx](), NewParam: ShellContext)) {
        case ((contexts, state), lit@Literal(_, string)) =>
          val (newState, _) = parseLiteral(state, string)
          (contexts, newState)

        case ((contexts, state), hole@Hole(_, _)) =>
          val newState = hole(state)
          (newState :: contexts, newState)
      }

      if(finalState == InSingleQuotes || finalState == InDoubleQuotes) {
        val lit@Literal(_, _) = ctx.parts.last
        lit.abort(lit.string.length, "unclosed quoted parameter")
      }

      ctx.doEvaluation(contexts)
    }

    private def parseLiteral(state: Ctx, string: String): (Ctx, List[String]) =
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
    Transition(NewParam, InUnquotedParam) { s => '"'+s.replaceAll("\\\"", "\\\\\"")+'"' },
    Transition(InUnquotedParam, InUnquotedParam) { s => '"'+s.replaceAll("\\\"", "\\\\\"")+'"' },
    Transition(InSingleQuotes, InSingleQuotes) { s => s.replaceAll("'", """'"'"'""") },
    Transition(InDoubleQuotes, InDoubleQuotes) { s => s.replaceAll("\\\"", "\\\\\"") }
  )
  
  implicit class ShellStringContext(sc: StringContext) {
    val sh = Prefix(ShellInterpolator, sc)
  }

}
