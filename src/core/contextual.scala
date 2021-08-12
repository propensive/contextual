/*
    Contextual, version 2.0.0. Copyright 2016-21 Jon Pretty, Propensive OÜ.

    The primary distribution site is: https://propensive.com/

    Licensed under the Apache License, Version 2.0 (the "License"); you may not use this
    file except in compliance with the License. You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

    Unless required by applicable law or agreed to in writing, software distributed under the
    License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
    either express or implied. See the License for the specific language governing permissions
    and limitations under the License.
*/

package contextual

import scala.quoted.*
import scala.compiletime.*

import rudiments.*

class ContextualError(msg: String) extends Exception(str"contextual: $msg")

case class InterpolationError(msg: String, offset: Maybe[Int] = Unset, length: Maybe[Int] = Unset)
extends ContextualError(msg)

trait Interpolator[Input, State, Result]:
  def parse(state: State, next: String): State exposes InterpolationError
  def initial: State
  def complete(value: State): Result exposes InterpolationError
  def insert(state: State, value: Option[Input]): State exposes InterpolationError

  def expand(target: Expr[Interpolator[Input, State, Result]], ctx: Expr[StringContext],
                 seq: Expr[Seq[Any]])
            (using Quotes, Type[Input], Type[State], Type[Result]): Expr[Result] =
    import quotes.reflect.*

    def shift(pos: Position, offset: Int, length: Int) =
      Position(pos.sourceFile, pos.start + offset, pos.start + offset + length)

    def rethrow[T](blk: => T, pos: Position): T =
      try blk catch case InterpolationError(msg, offset, length) =>
        throw PositionalError(msg, shift(pos, offset.otherwise(0),
            length.otherwise(pos.end - pos.start - offset.otherwise(0))))

    case class PositionalError(msg: String, position: Position) extends Exception(msg)
    def recur(seq: Seq[Expr[Any]], parts: Seq[String], positions: Seq[Position], state: State,
                  expr: Expr[State]): Expr[Result] =
      seq match
        case '{ $head: h } +: tail =>
          val typeclass: Expr[Insertion[Input, h]] = Expr.summon[Insertion[Input, h]].getOrElse {
            val typeName: String = TypeRepr.of[h].widen.show
            
            report.throwError(
              s"contextual: can't substitute $typeName into this interpolated string",
              head.asTerm.pos
            )
          }
          
          val newState: State =
            rethrow(parse(rethrow(insert(state, None), expr.asTerm.pos), parts.head), positions.head)
          
          val next = '{$target.parse($target.insert($expr, Some($typeclass.embed($head))),
              ${Expr(parts.head)})}

          recur(tail, parts.tail, positions.tail, newState, next)
        
        case _ =>
          rethrow(complete(state), Position.ofMacroExpansion)
          '{$target.complete($expr)}
    
    seq match
      case Varargs(exprs) =>
        val parts = ctx.value.get.parts
        
        val positions: Seq[Position] = ctx match
          case '{ (${sc}: StringContext.type).apply(($parts: Seq[String])*) } =>
            parts match
              case Varargs(stringExprs) => stringExprs.to(List).map(_.asTerm.pos)
              case _                    => throw Impossible("expected Varargs")
          
          case _ =>
            throw Impossible("expected expression of the form `StringContext.apply(args)`")
        
        try recur(exprs, parts.tail, positions.tail, rethrow(parse(initial, parts.head), positions.head),
            '{$target.parse($target.initial,
            ${Expr(parts.head)})/*(using CanThrow[InterpolationError])*/})
        catch
          case error@PositionalError(message, pos) =>
            report.throwError(s"contextual: $message", pos)

          case error@InterpolationError(message, _, _) =>
            report.throwError(s"contextual: $message", Position.ofMacroExpansion)
      
      case _ =>
        report.throwError("contextual: expected varargs")

trait Insertion[Input, -T]:
  def embed(value: T): Input