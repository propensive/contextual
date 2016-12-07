package contextual.examples

import contextual._

import java.util.regex._

object regex {

  object RegexParser extends Interpolator {
    type Ctx = Context.NoContext

    def implementation(ctx: Contextual): ctx.Implementation = {
      import ctx.universe._

      if(ctx.literals.size > 1) throw InterpolationError(0, ctx.literals.head.size, "substitution is not supported")

      try Pattern.compile(ctx.literals.head) catch {
        case p: PatternSyntaxException =>
          throw InterpolationError(0, p.getIndex, p.getMessage)
      }

      ctx.Implementation(q"_root_.java.util.regex.Pattern.compile(${ctx.literals.head})")

    }

  }

  implicit class RegexStringContext(sc: StringContext) {
    val regex = Prefix(RegexParser, sc)
  }

}
