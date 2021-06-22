package contextual

import scala.quoted.*
import scala.compiletime.*

case class ParseError(str: String) extends Exception(str)

trait Interpolator[Input, State, Result]:
  def parse(state: State, next: String): State
  def initial: State
  def complete(value: State): Result
  def insert(state: State, value: Option[Input]): State

  def expand(target: Expr[Interpolator[Input, State, Result]], ctx: Expr[StringContext], seq: Expr[Seq[Any]])
            (using Quotes, Type[Input], Type[State], Type[Result]): Expr[Result] =
    import quotes.reflect.*
    
    def recur(seq: Seq[Expr[Any]], parts: Seq[String], state: State, expr: Expr[State]): Expr[Result] =
      seq match
        case '{ $head: h } +: tail =>
          val typeclass: Expr[Insertion[Input, h]] = Expr.summon[Insertion[Input, h]].getOrElse {
            val typeName: String = TypeRepr.of[h].widen.show
            report.error(s"contextual: can't substitute $typeName into this interpolated string")
            ???
          }
          
          val newState: State = parse(insert(state, None), parts.head)
          val next = '{$target.parse($target.insert($expr, Some($typeclass.embed($head))), ${Expr(parts.head)})}

          recur(tail, parts.tail, newState, next)
        
        case _ =>
          '{$target.complete($expr)}
    
    seq match
      case Varargs(exprs) =>
        val parts = ctx.value.get.parts
        try recur(exprs, parts.tail, parse(initial, parts.head), '{$target.parse($target.initial,
            ${Expr(parts.head)})})
        catch case error@ParseError(message) =>
          report.error(s"contextual: $message")
          throw error

trait Insertion[Input, -T]:
  def embed(value: T): Input