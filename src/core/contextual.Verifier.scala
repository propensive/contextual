/*
    Contextual, version 0.26.0. Copyright 2025 Jon Pretty, Propensive OÜ.

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

import anticipation.*
import vacuous.*

trait Verifier[ResultType]
extends Interpolator[Nothing, Optional[ResultType], ResultType]:
  def verify(text: Text): ResultType
  protected def initial: Optional[ResultType] = Unset
  protected def parse(state: Optional[ResultType], next: Text): Optional[ResultType] = verify(next)
  protected def skip(state: Optional[ResultType]): Optional[ResultType] = state
  protected def insert(state: Optional[ResultType], value: Nothing): Optional[ResultType] = state
  protected def complete(value: Optional[ResultType]): ResultType = value.option.get

  def expand(context: Expr[StringContext])(using Quotes, Type[ResultType])(using thisType: Type[this.type])
  :     Expr[ResultType] = expand(context, '{Nil})(using thisType)
