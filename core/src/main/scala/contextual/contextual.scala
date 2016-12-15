/* Contextual, version 0.14. Copyright 2016 Jon Pretty, Propensive Ltd.
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
import language.higherKinds
import language.implicitConversions
import language.existentials

/** Represents a compile-time failure in interpolation. */
case class InterpolationError(part: Int, offset: Int, message: String) extends Exception

object Prefix {
  /** Creates a new prefix. This should be applied directly to a named value in an implicit
    * class that wraps a `StringContext` to bind an interpolator object to a prefix of the
    * given name. */
  def apply(interpolator: Interpolator, stringContext: StringContext):
      Prefix[interpolator.Ctx, interpolator.type] =
    new Prefix(interpolator, stringContext.parts)
}

/** A `Prefix` represents the attachment of an `Interpolator` to a `StringContext`, typically
  * using an implicit class. It has only a single method, `apply`, with a signature that's
  * appropriate for fitting the shape of a desugared interpolated string application. */
class Prefix[C <: Context, P <: Interpolator { type Ctx = C }](interpolator: P,
    parts: Seq[String]) {

  /** The `apply` method is typically invoked as a result of the desugaring of a StringContext
    * during parsing in Scalac. The method signature takes multiple `Embedded` parameters,
    * which are designed to be created as the result of applying an implicit conversion, which
    * will only succeed with appropriate `Embedding` implicits for embedding that type within
    * the interpolated string.
    *
    * The method is implemented with the main `contextual` macro. */
  def apply(exprs: Interpolator.Embedded[interpolator.Inputs, interpolator.type]*): Any =
      macro Macros.contextual[C, P]

}

/** An `Interpolator` defines the compile-time and runtime behavior when interpreting an
  * interpolated string. */
trait Interpolator extends Interpolator.Parts { interpolator =>

  /** The `Contextual` type is a representation of the known compile-time information about an
    * interpolated string. Most importantly, this includes the literal parts of the interpolated
    * string; the constant parts which surround the variables parts that are substituted into
    * it. The `Contextual` type also provides details about these holes, specifically the
    * possible set of contexts in which the substituted value may be interpreted. */
  class Contextual[Parts >: Literal <: Part](val literals: Seq[String],
      val interspersions: Seq[Parts]) {

    override def toString = Seq("" +: interspersions, literals).transpose.flatten.mkString

    /** The macro context when expanding the `contextual` macro. */
    val context: compat.Context = null

    /** The expressions that are substituted into the interpolated string. */
    def expressions: Seq[context.Expr[Any]] = Nil

    lazy val universe: context.universe.type = context.universe

    def interpolatorTerm: Option[context.Symbol] = None

    /** Provides the sequence of `Literal`s and `Hole`s in this interpolated string. */
    def parts: Seq[Parts] = {
      val literalsHead +: literalsTail = literals.zipWithIndex.map { case (lit, idx) =>
        Literal(idx, lit)
      }

      literalsHead +: Seq(interspersions, literalsTail).transpose.flatten
    }

  }

  /** Validates the interpolated string, and returns a sequence of contexts for each hole in the
    * string. */
  def contextualize(contextual: Contextual[StaticPart]): Seq[Ctx]

  /** The macro evaluator that defines what code will be generated for this `Interpolator`. The
    * default implementation constructs a new runtime `Contextual` object, and invokes the
    * `evaluate` method on the `Interpolator`. */
  def evaluator(contexts: Seq[Ctx], contextual: Contextual[StaticPart]): contextual.context.Expr[Any] = {

    val c: contextual.context.type = contextual.context

    import c.universe.{Literal => _, _}

    val interpolatorTerm = contextual.interpolatorTerm.get

    val substitutions = contexts.zip(contextual.expressions).zipWithIndex.map {
      case ((ctx, Apply(Apply(_, List(value)), List(embedder))), idx) =>

        /* TODO: Avoid using runtime reflection to get context objects, if we can. */
        val reflectiveContextClass =
          q"_root_.java.lang.Class.forName(${ctx.getClass.getName})"

        val reflectiveContext =
          q"""$reflectiveContextClass.getField("MODULE$$").get($reflectiveContextClass)"""

        val castReflectiveContext = q"$reflectiveContext"

        q"$interpolatorTerm.Substitution($idx, $embedder($castReflectiveContext).apply($value))"
    }

    q"""$interpolatorTerm.evaluate(
      new $interpolatorTerm.Contextual[$interpolatorTerm.RuntimePart](
        _root_.scala.collection.Seq(..${contextual.literals}),
        _root_.scala.collection.Seq(..$substitutions)
      )
    )"""
  }

  class Embedding[I] private[Interpolator]() {
    def apply[CC <: (Context, Context), R](cases: Transition[CC, I, R]*):
        Embedder[CC, I, R, interpolator.type] = new Embedder(cases)
  }

  /** Intermediate constructor method for making new `Embedder` typeclasses, via the
    * `Embedding` class, which only exists as a half-way house for inferring most type
    * parameters, while having the type `I` specified explicitly. */
  def embed[I]: Embedding[I] = new Embedding()
}

/** Factory object for creating `Transitions`. */
object Transition {
  /** Creates a new `Transition` for instances of type `Value`, specifying the `context` in
    * which that type may be substituted, and `after` context. */
  def apply[Before <: Context, After <: Context, Value, Input](context: Before, after: After)
      (fn: Value => Input): Transition[(Before, After), Value, Input] =
    new Transition(context, after, fn)
}

/** A `Transition` specifies for a particular `Context` how a value of type `Value` should be
  * converted into the appropriate `Input` type to an `Interpolator`, and how the application of
  * the value should change the `Context` in the interpolated string. */
class Transition[-CC <: (Context, Context), -Value, +Input] private[contextual]
    (val context: Context, val after: Context, val fn: Value => Input)

/** An `Embedder` defines, for an `Interpolator`, `I`, a type `V` should be converted to the
  * common input type 'R', when substituted into different context positions. */
class Embedder[CC <: (Context, Context), V, R, I <: Interpolator](
    val cases: Seq[Transition[CC, V, R]]) {

  def apply[C2](c: C2)(implicit ev: CC <:< (C2, Context)): V => R =
    cases.find(_.context == c).get.fn
}

/** A `Context` describes the nature of the position in an interpolated string where a
  * substitution is made, and determines how values of a particular type should be interpreted
  * in the given position. */
trait Context {
  override def toString: String = getClass.getName.split("\\.").last.dropRight(1)
}

object Interpolator {

  /** The `embed` implicit method which automatically converts acceptable types to the
    * `Embedded` type, binding them with their corresponding `Embedder`, which defines how that
    * type should be converted to a common input type in different contexts. */
  implicit def embed[CC <: (Context, Context), V, R, I <: Interpolator](value: V)
      (implicit embedder: Embedder[CC, V, R, I]): Embedded[R, I] =
    new Embedded[R, I] { def apply(ctx: Context): R = embedder(ctx).apply(value) }

  /** A value which has been embedded as a substitution into an interpolated string, using the
    * implicit `embed` method. */
  abstract class Embedded[R, I <: Interpolator] private[contextual] {
    def apply(ctx: Context): R
  }


  /** Provides case classes representing the literal parts, and interspersed parts of an
    * interpolated string. */
  trait Parts {

    type Ctx <: Context
    type Inputs

    sealed trait Part extends Product with Serializable

    /** Sealed trait of parts that are known at compile-time. This is only `Literal` and `Hole`
      * values. Note that `Literal`s are also available at runtime. */
    sealed trait StaticPart extends Part with Product with Serializable { def index: Int }

    /** Sealed trait of parts that are known at runtime. This is only `Literal` and
      * `Substitution` values. Note that `Literal`s are also available at compile-time. */
    sealed trait RuntimePart extends Part with Product with Serializable { def index: Int }

    /** A `Hole` represents all that is known at compile-time about a substitution into an
      * interpolated string. */
    case class Hole(index: Int, input: Map[Ctx, Ctx]) extends StaticPart {

      override def toString: String = input.keys.mkString("[", "|", "]")

      def apply(ctx: Ctx): Ctx =
        input.get(ctx).getOrElse(abort(
            "values of this type cannot be substituted in this position"))

      /** Aborts compilation, positioning the caret at this hole in the interpolated string,
        *  displaying the error message, `message`. */
      def abort(message: String): Nothing = throw InterpolationError(index, -1, message)
    }

    /** Represents a known value (at runtime) that is substituted into an interpolated string.
      */
    case class Substitution(index: Int, val value: Inputs) extends RuntimePart {

      /** Gets the substituted value. */
      def apply(): Inputs = value

      override def toString = value.toString
    }

    /** Represents a fixed, constant part of an interpolated string, known at compile-time. */
    case class Literal(index: Int, string: String) extends StaticPart with RuntimePart {

      override def toString: String = string

      /** Aborts compilation, positioning the caret at the `offset` into this literal part of
        * the  interpolated string, displaying the error message, `message`. */
      def abort(offset: Int, message: String) = throw InterpolationError(index, offset, message)
    }
  }
}
