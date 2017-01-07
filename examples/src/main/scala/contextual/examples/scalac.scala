package contextual.examples

import com.twitter.util.Eval
import contextual.{Interpolator, Prefix}

object scalac {

  object ScalacParser extends Interpolator {

    def contextualize(interpolation: StaticInterpolation): Seq[ContextType] = {

      interpolation.parts.foreach {
        case lit@Literal(_, string) =>
          try {
            new Eval().compile(string)
          } catch {
            case e: Eval.CompilerException =>
              interpolation.abort(lit, 0, e.getMessage)
          }
        case hole@Hole(_, _) =>
          interpolation.abort(hole, "substitution is not supported")
      }

      Nil
    }

    def evaluate(interpolation: RuntimeInterpolation): Context = {
      val eval = new Eval()
      eval.compile(interpolation.parts.mkString)
      new Context(eval)
    }
  }

  implicit class ScalacStringContext(sc: StringContext) { val scalac = Prefix(ScalacParser, sc) }

  class Context(eval: Eval) {
    def apply[A](s: String) = eval.inPlace[A](s)
  }
}
