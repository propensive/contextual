/* Contextual, version 0.5. Copyright 2016 Jon Pretty, Propensive Ltd.
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

import scala.reflect._, macros._
import scala.annotation.implicitNotFound

import language.experimental.macros
import language.higherKinds
import language.implicitConversions
import language.existentials

/** Represents a compile-time failure in interpolation. */
case class InterpolationError(part: Int, offset: Int, message: String) extends Exception

object Context {
  sealed trait NoContext extends Context
  object NoContext extends NoContext
}

/** A `Context` describes the nature of the position in an interpolated string where a
  * substitution is made, and determines how values of a particular type should be interpreted
  * in the given position. */
trait Context {
  override def toString: String = getClass.getName.split("\\.").last.dropRight(1)
}

object Embedded {
  implicit def embed[CC <: (Context, Context), V, R, I <: Interpolator](value: V)
      (implicit handler: Handler[CC, V, R, I]): Embedded[R, I] =
    new Embedded[R, I] {
      def apply(ctx: Context): R = {
        handler(ctx).apply(value)
      }
    }
}

abstract class Embedded[R, I <: Interpolator] {
  def apply(ctx: Context): R
}

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
  def apply(exprs: Embedded[interpolator.Inputs, interpolator.type]*): Any =
    macro Macros.contextual[C, P]
}

/** An `Interpolator` defines the compile-time and runtime behavior when interpreting an
  * interpolated string. */
trait Interpolator { interpolator =>
    
  sealed trait RuntimeParseToken[+I]
  sealed trait CompileParseToken[+I]

  case class Hole[C <: Context](input: Set[(C, C)]) extends CompileParseToken[(C, C)] {
    override def toString: String = input.mkString("[", "|", "]")
  }

  case class Literal(index: Int, string: String) extends CompileParseToken[Nothing] with
      RuntimeParseToken[Nothing] {
    override def toString: String = string
  }

  case class Variable[+I](value: I) extends RuntimeParseToken[I] {
    override def toString: String = value.toString
  }

  type Ctx <: Context
  type Inputs  

  /** The `Contextual` type is a representation of the known compile-time information about an
    * interpolated string. Most importantly, this includes the literal parts of the interpolated
    * string; the constant parts which surround the variables parts that are substituted into
    * it. The `Contextual` type also provides details about these holes, specifically the
    * possible set of contexts in which the substituted value may be interpreted. */
  abstract class Contextual(val literals: Seq[String], val holes: Seq[Hole[_]]) {

    val context: whitebox.Context
    def expressions: Seq[context.Tree]
    
    lazy val universe: context.universe.type = context.universe
    
    object Implementation {
      def apply[T: Implementer](v: T): Implementation = new Implementation {
        type Type = T
        def value: Type = v
        def implementer: Implementer[Type] = implicitly[Implementer[Type]]
      }
    }

    trait Implementation {
      type Type
      def value: Type
      def implementer: Implementer[Type]
      def tree: context.Tree = implementer.tree(value)
    }

    object Implementer {
      implicit val string: Implementer[String] = new Implementer[String] {
        def tree(string: String): context.Tree = {
          import context.universe._
          q"$string"
        }
      }
      
      implicit val quasiquotes: Implementer[context.Tree] = new Implementer[context.Tree] {
        def tree(expr: context.Tree): context.Tree = expr
      }
    }
    
    @implicitNotFound("cannot create an implementation based on the type ${T}")
    trait Implementer[T] {
      def tree(value: T): context.Tree
    }

    def parts: Seq[CompileParseToken[_]] = {
      val head :: tail = literals.to[List].zipWithIndex.map { case (lit, idx) =>
        Literal(idx, lit)
      }
      
      head :: List(holes, tail).transpose.flatten
    }
  
  }

  def implementation(contextual: Contextual): contextual.Implementation

  def parse(string: String): Any = string

  class Embedding[I] protected[Interpolator] () {
    def apply[CC <: (Context, Context), R](cases: Case[CC, I, R]*):
        Handler[CC, I, R, interpolator.type] = new Handler(cases.to[List])
  }

  def embed[I]: Embedding[I] = new Embedding()
}

object transition {
  def apply[C <: Context, C2 <: Context, V, R](context: C, after: C2)(fn: V => R):
      Case[(C, C2), V, R] = Case(context, after, fn)
}

case class Case[-CC <: (Context, Context), -V, +R](context: Context, after: Context, fn: V => R)

class Handler[CC <: (Context, Context), V, R, I <: Interpolator](
    val cases: List[Case[CC, V, R]]) {
  
  def apply[C2](c: C2)(implicit ev: CC <:< (C2, Context)): V => R =
    cases.find(_.context == c).get.fn
}
