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

    def eval(contexts: Contextual[RuntimeToken]): Process = {
      
      Process("foobar")
    }

    def implementation(ctx: Contextual[StaticToken]): ctx.Implementation = {
      import ctx.universe.{Literal => _, _}

      val (contexts, finalState) = ctx.parts.foldLeft((List[Ctx](), NewParam: ShellContext)) {
        case ((contexts, state), lit@Literal(_, string)) =>
          (contexts, string.foldLeft(state) {
            
            case (NewParam, ' ') =>
              NewParam
            
            case (InUnquotedParam, ' ') =>
              NewParam
            
            case (InSingleQuotes, '\'') =>
              InUnquotedParam
            
            case (InDoubleQuotes, '"') =>
              InUnquotedParam
            
            case (InUnquotedParam | NewParam, '"') =>
              InDoubleQuotes
            
            case (InUnquotedParam | NewParam, '\'') =>
              InSingleQuotes
           
            case (NewParam, ch) =>
              InUnquotedParam
            
            case (state, ch) =>
              state
          })

        case ((contexts, state), hole@Hole(_, _)) =>
          val newState = hole(state)
          (newState :: contexts, newState)
      }

      if(finalState == InSingleQuotes || finalState == InDoubleQuotes)
        ctx.parts.last match { case lit: Literal => lit.abort(lit.string.length, "unclosed quoted parameter") }

      ctx.runtimeEval(contexts)
    }

  }

  implicit val embedStrings = ShellInterpolator.embed[String](
    Transition(NewParam, InUnquotedParam)(identity),
    Transition(InUnquotedParam, InUnquotedParam) { s => '"'+s.replaceAll("\\\"", "\\\\\"")+'"' },
    Transition(InSingleQuotes, InSingleQuotes) { s => s.replaceAll("'", """'"'"'""") },
    Transition(InDoubleQuotes, InDoubleQuotes) { s => s.replaceAll("\\\"", "\\\\\"") }
  )
  
  implicit class ShellStringContext(sc: StringContext) {
    val sh = Prefix(ShellInterpolator, sc)
  }

}
