/*
    Contextual, version [unreleased]. Copyright 2023 Jon Pretty, Propensive OÜ.

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
import digression.*

import language.experimental.captureChecking

case class InterpolationError(error: Text, offset: Maybe[Int] = Unset, length: Maybe[Int] = Unset)
extends Error(err"$error at $offset-$length")

trait Verifier[ResultType]
extends Interpolator[Nothing, Maybe[ResultType], ResultType]:
  def verify(text: Text): ResultType
  protected def initial: Maybe[ResultType] = Unset
  protected def parse(state: Maybe[ResultType], next: Text): Maybe[ResultType] = verify(next)
  protected def skip(state: Maybe[ResultType]): Maybe[ResultType] = state
  protected def insert(state: Maybe[ResultType], value: Nothing): Maybe[ResultType] = state
  protected def complete(value: Maybe[ResultType]): ResultType = value.option.get
  
  def expand
      (context: Expr[StringContext])(using Quotes, Type[ResultType])
      (using thisType: Type[this.type])
      : Expr[ResultType] = expand(context, '{Nil})(using thisType)

trait Interpolator[InputType, StateType, ResultType]:
  given CanThrow[InterpolationError] = ###

  protected def initial: StateType
  protected def parse(state: StateType, next: Text): StateType
  protected def skip(state: StateType): StateType
  protected def substitute(state: StateType, value: Text): StateType = parse(state, value)
  protected def insert(state: StateType, value: InputType): StateType
  protected def complete(value: StateType): ResultType

  def expand
      (context: Expr[StringContext], seq: Expr[Seq[Any]])
      (using thisType: Type[this.type])
      (using Quotes, Type[InputType], Type[StateType], Type[ResultType])
      : Expr[ResultType] =
    import quotes.reflect.*

    val target = (thisType: @unchecked) match
      case '[thisType] =>
        val ref = Ref(TypeRepr.of[thisType].typeSymbol.companionModule)
        ref.asExprOf[Interpolator[InputType, StateType, ResultType]]

    def shift(pos: Position, offset: Int, length: Int): Position =
      Position(pos.sourceFile, pos.start + offset, pos.start + offset + length)

    def rethrow[ResultType](block: -> ResultType, pos: Position): ResultType =
      try block catch case err: InterpolationError => err match
        case InterpolationError(msg, off, len) =>
          erased given CanThrow[PositionalError] = unsafeExceptions.canThrowAny
          throw PositionalError(msg, shift(pos, off.or(0), len.or(pos.end - pos.start - off.or(0))))

    case class PositionalError(error: Text, position: Position)
    extends Error(err"error $error at position $position")
    
    def recur(seq: Seq[Expr[Any]], parts: Seq[String], positions: Seq[Position], state: StateType,
                  expr: Expr[StateType]): Expr[ResultType] throws PositionalError =
      seq match
        case '{$head: headType} +: tail =>
          def notFound: Nothing =
            val typeName: String = TypeRepr.of[headType].widen.show
            
            fail(s"can't substitute $typeName into this interpolated string", head.asTerm.pos)

          val (newState, typeclass) = Expr.summon[Insertion[InputType, headType]].fold(notFound):
            case '{$typeclass: Substitution[InputType, headType, subType]} =>
              val substitution: String = (TypeRepr.of[subType].asMatchable: @unchecked) match
                case ConstantType(StringConstant(string)) =>
                  string
            
              (rethrow(parse(rethrow(substitute(state, Text(substitution)), expr.asTerm.pos),
                  Text(parts.head)), positions.head), typeclass)
          
            case '{$typeclass: eType} =>
              (rethrow(parse(rethrow(skip(state), expr.asTerm.pos), Text(parts.head)),
                  positions.head), typeclass)

            case _ =>
              throw Mistake("Should never match")
            
          val next = '{$target.parse($target.insert($expr, $typeclass.embed($head)),
              Text(${Expr(parts.head)}))}

          recur(tail, parts.tail, positions.tail, newState, next)
        
        case _ =>
          rethrow(complete(state), Position.ofMacroExpansion)
          '{$target.complete($expr)}
    
    val exprs: Seq[Expr[Any]] = seq match
      case Varargs(exprs) => exprs
      case _              => Nil
        
    val parts = context.value.getOrElse:
      fail(s"the StringContext extension method parameter does not appear to be inline")
    .parts
    
    val positions: Seq[Position] = (context: @unchecked) match
      case '{(${sc}: StringContext.type).apply(($parts: Seq[String])*)} =>
        (parts: @unchecked) match
          case Varargs(stringExprs) => stringExprs.to(List).map(_.asTerm.pos)
    
    try recur(exprs, parts.tail, positions.tail, rethrow(parse(initial, Text(parts.head)),
        positions.head), '{$target.parse($target.initial, Text(${Expr(parts.head)}))})
    catch
      case err: PositionalError => err match
        case PositionalError(error, pos) => fail(s"$error", pos)

      case err: InterpolationError => err match
        case InterpolationError(error, _, _) => fail(s"$error", Position.ofMacroExpansion)
      
trait Insertion[InputType, -T]:
  def embed(value: T): InputType

trait Substitution[InputType, -T, S <: String & Singleton] extends Insertion[InputType, T]
