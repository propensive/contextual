/*
    Contextual, version 2.4.0. Copyright 2016-21 Jon Pretty, Propensive OÜ.

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

class ContextualError(msg: String) extends Error(s"contextual: $msg")

case class InterpolationError(msg: String, offset: Maybe[Int] = Unset, length: Maybe[Int] = Unset)
extends ContextualError(msg)

trait Interpolator[Input, State, Result]:
  def initial: State
  def parse(state: State, next: String): State
  def skip(state: State): State
  def substitute(state: State, value: String): State = parse(state, value)
  def insert(state: State, value: Input): State
  def complete(value: State): Result

  def expand(target: Expr[Interpolator[Input, State, Result]], ctx: Expr[StringContext],
                 seq: Expr[Seq[Any]])
            (using Quotes, Type[Input], Type[State], Type[Result]): Expr[Result] =
    import quotes.reflect.*

    def shift(pos: Position, offset: Int, length: Int): Position =
      Position(pos.sourceFile, pos.start + offset, pos.start + offset + length)

    def rethrow[T](blk: => T, pos: Position): T =
      try blk catch case InterpolationError(msg, offset, length) =>
        throw PositionalError(msg, shift(pos, offset.otherwise(0),
            length.otherwise(pos.end - pos.start - offset.otherwise(0))))

    case class PositionalError(msg: String, position: Position) extends Error(msg)
    
    def recur(seq: Seq[Expr[Any]], parts: Seq[String], positions: Seq[Position], state: State,
                  expr: Expr[State]): Expr[Result] =
      seq match
        case '{ $head: h } +: tail =>

          val (newState, typeclass) = Expr.summon[Insertion[Input, h]].fold {
            val typeName: String = TypeRepr.of[h].widen.show
            
            report.errorAndAbort(
              s"contextual: can't substitute $typeName into this interpolated string",
              head.asTerm.pos
            )
          } {
            case '{ $typeclass: Substitution[Input, `h`, sub] } =>
              val substitution: String = TypeRepr.of[sub] match
                case ConstantType(StringConstant(str)) => str
                case _                                 => throw Impossible("should not happen")
            
              (rethrow(parse(rethrow(substitute(state, substitution), expr.asTerm.pos), parts.head),
                  positions.head), typeclass)
          
            case '{ $typeclass: eType } =>
              (rethrow(parse(rethrow(skip(state), expr.asTerm.pos), parts.head),
                  positions.head), typeclass)
            
            case _ =>
              throw Impossible("this case should never match")
          }

          val next = '{$target.parse($target.insert($expr, $typeclass.embed($head)),
              ${Expr(parts.head)})}

          recur(tail, parts.tail, positions.tail, newState, next)
        
        case _ =>
          rethrow(complete(state), Position.ofMacroExpansion)
          '{$target.complete($expr)}
    
    seq match
      case Varargs(exprs) =>
        val parts = ctx.value.getOrElse {
          report.errorAndAbort(s"contextual: the StringContext extension method parameter does "+
                                     "not appear to be inline")
        }.parts
        
        val positions: Seq[Position] = ctx match
          case '{ (${sc}: StringContext.type).apply(($parts: Seq[String])*) } =>
            parts match
              case Varargs(stringExprs) => stringExprs.to(List).map(_.asTerm.pos)
              case _                    => throw Impossible("expected Varargs")
          
          case _ =>
            throw Impossible("expected expression of the form `StringContext.apply(args)`")
        
        try recur(exprs, parts.tail, positions.tail, rethrow(parse(initial, parts.head),
            positions.head), '{$target.parse($target.initial, ${Expr(parts.head)})})
        catch
          case error@PositionalError(message, pos) =>
            report.errorAndAbort(s"contextual: $message", pos)

          case error@InterpolationError(message, _, _) =>
            report.errorAndAbort(s"contextual: $message", Position.ofMacroExpansion)
      
      case _ =>
        report.errorAndAbort("contextual: expected varargs")

trait Insertion[Input, -T]:
  def embed(value: T): Input

trait Substitution[Input, -T, S <: String & Singleton] extends Insertion[Input, T]