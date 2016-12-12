package contextual.examples

import contextual._

import java.util.regex._

object regex {

  object RegexParser extends Interpolator {

    def implement(ctx: Contextual[StaticPart]): ctx.Implementation = {
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

      ctx.doEvaluation(contexts = Nil)
    }

    def evaluate(ctx: Contextual[RuntimePart]): Pattern = Pattern.compile(ctx.parts.mkString)

  }

  implicit class RegexStringContext(sc: StringContext) {
    val regex = Prefix(RegexParser, sc)
  }

}
