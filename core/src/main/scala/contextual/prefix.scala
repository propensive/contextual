/* Contextual, version 1.0.0. Copyright 2016 Jon Pretty, Propensive Ltd.
 *
 * The primary distribution site is: http://co.ntextu.al/
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the
 * License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied. See the License for the specific language governing permissions
 * and limitations under the License.
 */
package contextual

import language.experimental.macros

/** Companion and factory object for the [[Prefix]] class. */
object Prefix {
  /** Creates a new [[Prefix]]. This should be applied directly to a named value in an implicit
    * class that wraps a [[scala.StringContext]] to bind an interpolator object to a prefix of
    * the given name.
    *
    * A typical usage would be the implicit class,
    *
    * <pre>
    * implicit class FooPrefix(ctx: StringContext) {
    *   val foo = Prefix(FooInterpolator, ctx)
    * }
    * </pre>
    *
    * @param interpolator the [[Interpolator]] to bind to the prefix
    * @param stringContext the [[scala.StringContext]] to be wrapped
    * @return a new instance of a [[Prefix]]
    */
  def apply(interpolator: Interpolator, stringContext: StringContext):
      Prefix[Any, interpolator.ContextType, interpolator.type] =
    new Prefix(interpolator, stringContext.parts)


  /** Creates a new [[Prefix]] with refined return type of evaluate method. This should be applied
    * directly to a named value in an implicit class that wraps a [[scala.StringContext]] to bind an
    * interpolator object to a prefix of the given name.
    *
    * A typical usage would be the implicit class,
    *
    * <pre>
    * implicit class FooPrefix(ctx: StringContext) {
    *   val foo = Prefix.typed[Foo](FooInterpolator, ctx)
    * }
    * </pre>
    *
    * @param interpolator the [[Interpolator]] to bind to the prefix
    * @param stringContext the [[scala.StringContext]] to be wrapped
    * @return a new instance of a [[Prefix]]
    */
  def typed[ReturnType](interpolator: Interpolator, stringContext: StringContext):
      Prefix[ReturnType, interpolator.ContextType, interpolator.type] =
    new Prefix(interpolator, stringContext.parts)
}

/** A [[Prefix]] represents the attachment of an [[Interpolator]] to a [[scala.StringContext]],
  * typically using an implicit class. It has only a single method, [[apply]], with a signature
  * that's appropriate for fitting the shape of a desugared interpolated string application.
  *
  * @param interpolator the [[Interpolator]] object to bind to this prefix
  * @param parts a sequence of the literal parts of the interpolated string, taken from the
  * `parts` value of the [[scala.StringContext]]
  * @tparam PrefixContextType the context inferred from `interpolator`'s type member
  * @tparam InterpolatorType the singleton type of the [[Interpolator]]
  */
final class Prefix[ReturnType, PrefixContextType <: Context, InterpolatorType <: Interpolator { type
    ContextType = PrefixContextType }](interpolator: InterpolatorType, parts: Seq[String]) {

  /** The [[apply]] method is typically invoked as a result of the desugaring of a
    * [[scala.StringContext]] during parsing in Scalac. The method signature takes multiple
    * [[Interpolator.Embedded]] parameters, which are designed to be created as the result of
    * applying an implicit conversion, which will only succeed with appropriate
    * [[Interpolator.Embedding]] implicits for embedding that type within the interpolated
    * string.
    *
    * The method is implemented with the main [[contextual]] macro.
    *
    * @param expressions a sequence of expressions corresponding to each substitution
    * @return the evaluated result of the [[contextual]] macro */
  def apply(expressions: Interpolator.Embedded[interpolator.Input, interpolator.type]*): ReturnType =
    macro Macros.contextual[PrefixContextType, InterpolatorType]
}
