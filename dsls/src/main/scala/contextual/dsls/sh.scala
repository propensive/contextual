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

    def implementation(ctx: Contextual): ctx.Implementation = {
      import ctx.universe.{Literal => _, _}

      val (params, finalState) = ctx.parts.foldLeft(("" :: Nil, NewParam: ShellContext)) {
        case ((params, state), lit@Literal(_, string)) =>
          string.foldLeft((params, state)) {
            
            case ((params, NewParam), ' ') =>
              (params, NewParam)
            
            case ((params, InUnquotedParam), ' ') =>
              ("" :: params, NewParam)
            
            case ((params, InSingleQuotes), '\'') =>
              (params, InUnquotedParam)
            
            case ((params, InDoubleQuotes), '"') =>
              (params, InUnquotedParam)
            
            case ((params, InUnquotedParam | NewParam), '"') =>
              (params, InDoubleQuotes)
            
            case ((current :: done, InUnquotedParam | NewParam), '\'') =>
              (current :: done, InSingleQuotes)
           
            case ((current :: done, NewParam), ch) =>
              ((current+ch) :: done, InUnquotedParam)
            
            case ((current :: done, state), ch) =>
              (current+ch :: done, state)
          }

        case ((params, state), hole@Hole(index, transitions)) =>
          (state.toString :: params, transitions.get(state).getOrElse {
            hole.abort("can't handle this type here")
          })
      }

      if(finalState == InSingleQuotes || finalState == InDoubleQuotes)
        ctx.parts.last match { case lit: Literal => lit.abort(lit.string.length, "unclosed quoted parameter") }

      val paramTokens = params.reverse.map { p => q"$p" }

      ctx.Implementation(q"_root_.contextual.examples.shell.Process(..$paramTokens)")

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
