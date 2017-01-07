package contextual.examples

import com.twitter.util.Eval
import contextual.{Interpolator, Prefix}

object scala2 {

  object Scala2Parser extends Interpolator {

    def contextualize(interpolation: StaticInterpolation): Seq[ContextType] = {

      interpolation.parts.foreach {
        case lit@Literal(_, string) =>
          try {
            new Eval().apply[Any](string)
          } catch {
            case e: Eval.CompilerException =>
              interpolation.abort(lit, 0, e.getMessage)
          }
        case hole@Hole(_, _) =>
          interpolation.abort(hole, "substitution is not supported")
      }

      Nil
    }

    def evaluate[A](interpolation: RuntimeInterpolation): A =
      new Eval().apply(interpolation.parts.mkString)
  }

  implicit class Scala2StringContext(sc: StringContext) { val scala = Prefix(Scala2Parser, sc) }

}
