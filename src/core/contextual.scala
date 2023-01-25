/*
    Contextual, version 0.4.0. Copyright 2016-23 Jon Pretty, Propensive OÜ.

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

case class InterpolationError(error: Text, offset: Maybe[Int] = Unset, length: Maybe[Int] = Unset)
extends Error(err"$error at $offset-$length")

trait Verifier[Result] extends Interpolator[Nothing, Maybe[Result], Result]:
  def verify(text: Text): Result
  protected def initial: Maybe[Result] = Unset
  protected def parse(state: Maybe[Result], next: Text): Maybe[Result] = verify(next)
  protected def skip(state: Maybe[Result]): Maybe[Result] = state
  protected def insert(state: Maybe[Result], value: Nothing): Maybe[Result] = state
  protected def complete(value: Maybe[Result]): Result = value.or(throw Mistake("should be impossible"))

trait Interpolator[Input, State, Result]:
  given CanThrow[InterpolationError] = compiletime.erasedValue

  protected def initial: State
  protected def parse(state: State, next: Text): State
  protected def skip(state: State): State
  protected def substitute(state: State, value: Text): State = parse(state, value)
  protected def insert(state: State, value: Input): State
  protected def complete(value: State): Result

  def expand(target: Expr[Interpolator[Input, State, Result]], ctx: Expr[StringContext], seq: Expr[Seq[Any]])
            (using Quotes, Type[Input], Type[State], Type[Result])
            : Expr[Result] =
    import quotes.reflect.*

    def shift(pos: Position, offset: Int, length: Int): Position =
      Position(pos.sourceFile, pos.start + offset, pos.start + offset + length)

    def rethrow[T](blk: => T, pos: Position): T =
      import unsafeExceptions.canThrowAny
      try blk catch case err: InterpolationError =>
        err match
          case InterpolationError(msg, offset, length) =>
            throw PositionalError(msg, shift(pos, offset.or(0), length.or(pos.end - pos.start - offset.or(0))))

    case class PositionalError(error: Text, position: Position)
    extends Error(err"error $error at position $position")
    
    def recur(seq: Seq[Expr[Any]], parts: Seq[String], positions: Seq[Position], state: State,
                  expr: Expr[State]): Expr[Result] =
      seq match
        case '{ $head: h } +: tail =>
          def notFound: Nothing =
            val typeName: String = TypeRepr.of[h].widen.show
            
            report.errorAndAbort(s"contextual: can't substitute $typeName into this interpolated string",
                head.asTerm.pos)

          val (newState, typeclass) = Expr.summon[Insertion[Input, h]].fold(notFound):
            case '{ $typeclass: Substitution[Input, `h`, sub] } =>
              val substitution: String = TypeRepr.of[sub] match
                case ConstantType(StringConstant(string)) => string
                case other                                => throw Mistake(s"unexpected type: $other")
            
              (rethrow(parse(rethrow(substitute(state, Text(substitution)), expr.asTerm.pos), Text(parts.head)),
                  positions.head), typeclass)
          
            case '{ $typeclass: eType } =>
              (rethrow(parse(rethrow(skip(state), expr.asTerm.pos), Text(parts.head)), positions.head),
                  typeclass)
            
            case _ =>
              throw Mistake("this case should never match")

          val next = '{$target.parse($target.insert($expr, $typeclass.embed($head)), Text(${Expr(parts.head)}))}

          recur(tail, parts.tail, positions.tail, newState, next)
        
        case _ =>
          rethrow(complete(state), Position.ofMacroExpansion)
          '{$target.complete($expr)}
    
    seq match
      case Varargs(exprs) =>
        val parts = ctx.value.getOrElse:
          report.errorAndAbort(s"contextual: the StringContext extension method parameter does "+
                                     "not appear to be inline")
        .parts
        
        val positions: Seq[Position] = ctx match
          case '{ (${sc}: StringContext.type).apply(($parts: Seq[String])*) } => parts match
            case Varargs(stringExprs) => stringExprs.to(List).map(_.asTerm.pos)
            case _                    => throw Mistake("expected Varargs")
          
          case _ =>
            throw Mistake("expected expression of the form `StringContext.apply(args)`")
        
        try recur(exprs, parts.tail, positions.tail, rethrow(parse(initial, Text(parts.head)), positions.head),
            '{$target.parse($target.initial, Text(${Expr(parts.head)}))})
        catch
          case err: PositionalError => err match
            case PositionalError(error, pos) =>
              report.errorAndAbort(s"contextual: $error", pos)

          case err: InterpolationError => err match
            case InterpolationError(error, _, _) =>
              report.errorAndAbort(s"contextual: $error", Position.ofMacroExpansion)
      
      case _ =>
        report.errorAndAbort("contextual: expected varargs")

trait Insertion[Input, -T]:
  def embed(value: T): Input

trait Substitution[Input, -T, S <: String & Singleton] extends Insertion[Input, T]