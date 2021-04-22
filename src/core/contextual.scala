package contextual

import scala.quoted.*
import scala.compiletime.*

trait Verifier[Wip]:
  type Result
  
  def parse(state: Wip, next: String): Wip
  def initial: Wip
  def complete(value: Wip): Result


trait Substitution[Wip, T]:
  def embed(before: Wip, value: T): Wip

object Macros:
  def expand[Wip: Type](target: Expr[Verifier[Wip]], ctx: Expr[StringContext], tuple: Expr[Tuple])(using Quotes): Expr[Any] =
    import quotes.reflect.*

    def substitutions(tuple: Expr[Tuple]): List[(Expr[Any], Expr[Substitution[Wip, _]])] = tuple match
      case '{ $x: h *: t } =>
        val tc = Expr.summon[Substitution[Wip, h]].getOrElse { report.error("Can't find substitution"); ??? }
        ('{$x.head}, tc) :: substitutions('{$x.tail})
      case _ =>
        Nil

    val parts = ctx.value.get.parts

    def wrap(todo: Seq[String], tupleExprs: Seq[(Expr[Any], Expr[Substitution[Wip, _]])], expr: Expr[Wip]): Expr[Wip] =
      if todo.isEmpty then expr
      else
        val (e, typeclass) = tupleExprs.head
        val embedded = '{$typeclass.asInstanceOf[Substitution[Wip, Any]].embed($expr, $e)}
        wrap(todo.tail, tupleExprs.tail, '{$target.parse($embedded, ${Expr(todo.head)})})

    val wip: Expr[Wip] = wrap(ctx.value.get.parts.tail, substitutions(tuple), '{$target.parse($target.initial, ${Expr(ctx.value.get.parts.head)})})

    '{$target.complete($wip)}
