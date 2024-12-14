/*
    Contextual, version [unreleased]. Copyright 2024 Jon Pretty, Propensive OÜ.

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

import language.experimental.captureChecking

import scala.quoted.*
import scala.compiletime.*

import fulminate.*
import rudiments.*
import vacuous.*
import anticipation.*

trait Interpolator[InputType, StateType, ResultType]:
  given CanThrow[InterpolationError] = ###
  given Realm = realm"contextual"

  protected def initial: StateType
  protected def parse(state: StateType, next: Text): StateType
  protected def skip(state: StateType): StateType
  protected def substitute(state: StateType, value: Text): StateType = parse(state, value)
  protected def insert(state: StateType, value: InputType): StateType
  protected def complete(value: StateType): ResultType

  case class PositionalError(positionalMessage: Message, start: Int, end: Int)(using Diagnostics)
  extends Error(m"error $positionalMessage at position $start")

  def expand(context: Expr[StringContext], seq: Expr[Seq[Any]])(using thisType: Type[this.type])
     (using Quotes, Type[InputType], Type[StateType], Type[ResultType])
          : Expr[ResultType] =

    expansion(context, seq)(1)

  def expansion
     (context: Expr[StringContext], seq: Expr[Seq[Any]])
     (using thisType: Type[this.type])
     (using Quotes, Type[InputType], Type[StateType], Type[ResultType])
          : (StateType, Expr[ResultType]) =
    import quotes.reflect.*

    val ref = Ref(TypeRepr.of(using thisType).typeSymbol.companionModule)
    val target = ref.asExprOf[Interpolator[InputType, StateType, ResultType]]

    def rethrow[SuccessType](block: => SuccessType, start: Int, end: Int): SuccessType =
      try block catch case err: InterpolationError => err match
        case InterpolationError(msg, off, len) =>
          erased given CanThrow[PositionalError] = unsafeExceptions.canThrowAny
          given Diagnostics = Diagnostics.omit

          throw PositionalError(msg, start + off.or(0), start + off.or(0) + len.or(end - start - off.or(0)))

    def recur
       (seq:       Seq[Expr[Any]],
        parts:     Seq[String],
        positions: Seq[Position],
        state:     StateType,
        expr:      Expr[StateType])
            : (StateType, Expr[ResultType]) throws PositionalError =

      seq match
        case '{$head: headType} +: tail =>
          def notFound: Nothing =
            val typeName: String = TypeRepr.of[headType].widen.show

            abandon(m"can't substitute ${Text(typeName)} into this interpolated string", head.asTerm.pos)

          val (newState, typeclass) = Expr.summon[Insertion[InputType, headType]].fold(notFound): insertion =>
            (insertion: @unchecked) match
              case '{$typeclass: Substitution[InputType, headType, subType]} =>
                val substitution: String = (TypeRepr.of[subType].asMatchable: @unchecked) match
                  case ConstantType(StringConstant(string)) =>
                    string

                (rethrow(parse(rethrow(substitute(state, Text(substitution)), expr.asTerm.pos.start, expr.asTerm.pos.end),
                    Text(parts.head)), positions.head.start, positions.head.end), typeclass)

              case '{$typeclass: eType} =>
                (rethrow(parse(rethrow(skip(state), expr.asTerm.pos.start, expr.asTerm.pos.end), Text(parts.head)),
                    positions.head.start, positions.head.end), typeclass)

          val next = '{$target.parse($target.insert($expr, $typeclass.embed($head)),
              Text(${Expr(parts.head)}))}

          recur(tail, parts.tail, positions.tail, newState, next)

        case _ =>
          rethrow(complete(state), Position.ofMacroExpansion.start, Position.ofMacroExpansion.end)
          (state, '{$target.complete($expr)})

    val exprs: Seq[Expr[Any]] = seq match
      case Varargs(exprs) => exprs
      case _              => Nil

    val parts = context.value.getOrElse:
      abandon(m"the StringContext extension method parameter does not appear to be inline")

    . parts

    val positions: Seq[Position] = (context: @unchecked) match
      case '{(${sc}: StringContext.type).apply(($parts: Seq[String])*)} =>
        (parts: @unchecked) match
          case Varargs(stringExprs) => stringExprs.to(List).map(_.asTerm.pos)

    try recur(exprs, parts.tail, positions.tail, rethrow(parse(initial, Text(parts.head)),
        positions.head.start, positions.head.end), '{$target.parse($target.initial, Text(${Expr(parts.head)}))})
    catch
      case err: PositionalError => err match
        case PositionalError(message, start, end) => abandon(message, Position(Position.ofMacroExpansion.sourceFile, start, end))

      case err: InterpolationError => err match
        case InterpolationError(message, _, _) => abandon(message, Position.ofMacroExpansion)
