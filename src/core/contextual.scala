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

case class ParseError(str: String) extends Exception(str)

trait Interpolator[Input, State, Result]:
  import unsafeExceptions.canThrowAny

  def parse(state: State, next: String): State throws ParseError
  def initial: State
  def complete(value: State): Result throws ParseError
  def insert(state: State, value: Option[Input]): State throws ParseError

  def expand(target: Expr[Interpolator[Input, State, Result]], ctx: Expr[StringContext],
                 seq: Expr[Seq[Any]])
            (using Quotes, Type[Input], Type[State], Type[Result]): Expr[Result] =
    import quotes.reflect.*
    
    def recur(seq: Seq[Expr[Any]], parts: Seq[String], state: State, expr: Expr[State])
        : Expr[Result] =
      seq match
        case '{ $head: h } +: tail =>
          val typeclass: Expr[Insertion[Input, h]] = Expr.summon[Insertion[Input, h]].getOrElse {
            val typeName: String = TypeRepr.of[h].widen.show
            report.error(s"contextual: can't substitute $typeName into this interpolated string")
            ???
          }
          
          val newState: State = parse(insert(state, None), parts.head)
          
          val next = '{$target.parse($target.insert($expr, Some($typeclass.embed($head))),
              ${Expr(parts.head)})}

          recur(tail, parts.tail, newState, next)
        
        case _ =>
          '{$target.complete($expr)}
    
    seq match
      case Varargs(exprs) =>
        val parts = ctx.value.get.parts
        try recur(exprs, parts.tail, parse(initial, parts.head), '{$target.parse($target.initial,
            ${Expr(parts.head)})(using CanThrow[ParseError])})
        catch case error@ParseError(message) =>
          report.error(s"contextual: $message")
          ???
      
      case _ =>
        report.error("contextual: expected varargs")
        ???

trait Insertion[Input, -T]:
  def embed(value: T): Input