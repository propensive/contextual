package contextual

import scala.quoted.*
import scala.compiletime.*

case class ParseError(str: String) extends Exception()

trait Interpolator[Input, State, Result]:
  def parse(state: State, next: String): State
  def initial: State
  def complete(value: State): Result
  def insert(state: State, value: Option[Input]): State

  def expand(target: Expr[Interpolator[Input, State, Result]], ctx: Expr[StringContext], seq: Expr[Seq[Any]])
            (using Quotes, Type[Input], Type[State], Type[Result]): Expr[Result] =
    import quotes.reflect.*
    def recur(seq: Seq[Expr[Any]], parts: Seq[String], state: State, expr: Expr[State]): Expr[Result] =
      try seq match
        case '{ $head: h } +: tail =>
          val typeclass = Expr.summon[Insertion[Input, h]].getOrElse {
            val typeName = TypeRepr.of[h].widen.show
            report.error(s"contextual: can't substitute $typeName into this interpolated string")
            ???
          }
          val newState = parse(insert(state, None), parts.head)
          val newExpr = '{$target.parse($target.insert($expr, Some($typeclass.embed($head))), ${Expr(parts.head)})}

          recur(tail, parts.tail, newState, newExpr)
        case _ =>
          '{$target.complete($expr)}
      catch case error@ParseError(message) =>
        report.error(s"contextual: $message")
        throw error
    
    seq match
      case Varargs(exprs) =>
        val parts = ctx.value.get.parts
        recur(exprs, parts.tail, parse(initial, parts.head), '{$target.parse($target.initial, ${Expr(parts.head)})})

trait Insertion[Input, -T]:
  def embed(value: T): Input