package contextual

import scala.quoted._

trait Embeddable[T]:
  def apply(x: T): Param

case class Param(x: Expr[Any], embeddable: Embeddable[_])

given conv[T: Embeddable] as Conversion[T, Param] = { x => null}

given Embeddable[Int] = x => Param(null, null)
given Embeddable[String] = x => Param(null, null)

class Context:
  type Input

  trait Embeddable[T]:
    def apply(value: T): Input

object Context:
  def barImpl(expr: Expr[Seq[Param]], parts: Expr[Seq[String]])
             (using qc: QuoteContext)
             : Expr[String] =
    import qc.reflect._

    val constants: List[String] = parts.unseal match
      case Inlined(_, _, Select(Inlined(_, _, Apply(_, List(Typed(Repeated(xs, _), y)))), _)) =>
        xs.map { case Literal(Constant.String(str)) => str }

    expr.unseal match
      case x as Inlined(_, _, Typed(Repeated(xs, _), y)) =>
        println(x)
        //println(xs)
    '{"foo"}

val ctx = Context()

extension (inline sc: StringContext):
  inline def bar(inline expr: Param*): String = ${Context.barImpl('expr, '{sc.parts})}