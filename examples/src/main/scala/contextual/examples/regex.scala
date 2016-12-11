package contextual.examples

import contextual._

import java.util.regex._

object regex {

  object RegexParser extends Interpolator {
    type Ctx = Context.NoContext

    def implementation(ctx: Contextual[StaticToken]): ctx.Implementation = {
      import ctx.universe.{Literal => _, _}

      ctx.parts.foreach {
        case lit@Literal(_, string) =>
          try Pattern.compile(ctx.literals.head) catch {
            case p: PatternSyntaxException =>

              // We take only the interesting part of the message
              val message = p.getMessage.split(" near").head
              lit.abort(p.getIndex, message)
          }

        case hole@Hole(_, _) =>
          hole.abort("substitution is not supported")
      }

      ctx.Implementation(q"_root_.java.util.regex.Pattern.compile(${ctx.literals.head})")

    }

  }

  implicit class RegexStringContext(sc: StringContext) {
    val regex = Prefix(RegexParser, sc)
  }

}
