/*

    Contextual, version 1.5.0. Copyright 2016-20 Jon Pretty, Propensive OÜ.

    The primary distribution site is: https://propensive.com/

    Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in
    compliance with the License. You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

    Unless required by applicable law or agreed to in writing, software distributed under the License is
    distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
    See the License for the specific language governing permissions and limitations under the License.

*/
package contextual

import scala.reflect._, macros.{Context => _, _}

import language.implicitConversions

/** An [[Interpolator]] defines the compile-time and runtime behavior when interpreting an
  * interpolated string. */
trait Interpolator { interpolator =>

  /** The type, which is typically sealed, of all [[Context]]s which may exist for
    * substitutions into this [[Interpolator]]. */
  type ContextType <: Context

  /** The common type that substitutions of any supported type will be converted to for
    * processing at runtime, in the `evaluate` method. This is necessary as embeddings may
    * be specified using typeclasses for ad-hoc types which are not known when defining the
    * [[Interpolator]]. For most purposes, `String` is a reasonable choice, but there may
    * be the need to attach additional metadata to substitutions (which depends on the
    * original type being substituted), in which case a case class wrapping a `String` with
    * additional fields may be a better choice. */
  type Input

  /** The type that will be used for refining return type of the evaluated result of the
    * [[contextual]] macro. This type should be equal to the return type of the `evaluate`
    * method. If the `evaluate` method isn't defined the default value should be `Any`.
    */
  type Output

  /** The [[RuntimeInterpolation]] type is a representation of the known runtime information
    * about an interpolated string. Most importantly, this includes the literal parts of the
    * interpolated string; the constant parts which surround the variables parts that are
    * substituted into it. The [[RuntimeInterpolation]] type also provides details about the
    * substituted values, in particular its context (which was determined at compile time).
    *
    * @param literals the literal parts of the interpolated string
    * @param substitutions the substituted values, evaluated to a common [[Input]] type */
  class RuntimeInterpolation(val literals: Seq[String],
      val substitutions: Seq[Substitution]) {

    /** A string representation of this [[RuntimeInterpolation]] */
    override def toString = Seq("" +: substitutions, literals).transpose.flatten.mkString

    /** Provides the sequence of [[Literal]]s and [[Hole]]s in this interpolated string. */
    def parts: Seq[RuntimePart] = {
      val literalsHead +: literalsTail = literals.zipWithIndex.map { case (lit, idx) =>
        Literal(idx, lit)
      }

      literalsHead +: Seq(substitutions, literalsTail).transpose.flatten
    }
  }

  /** The [[StaticInterpolation]] type is a representation of the known compile-time information
    * about an interpolated string. Most importantly, this includes the literal parts of the
    * interpolated string; the constant parts which surround the variables parts that are
    * substituted into it. The [[StaticInterpolation]] type also provides details about these
    * holes, specifically the possible set of contexts in which the substituted value may be
    * interpreted. */
  trait StaticInterpolation {
    
    val macroContext: whitebox.Context
    def literals: Seq[String]
    def holes: Seq[Hole]
    def literalTrees: Seq[macroContext.Tree]
    def holeTrees: Seq[macroContext.Tree]
    def interpolatorTerm: macroContext.Symbol

    /** A string representation of this [[StaticInterpolation]] */
    override def toString = Seq("" +: holes, literals).transpose.flatten.mkString

    /** The universe of the whitebox macro context, should it be required. */
    lazy val universe: macroContext.universe.type = macroContext.universe

    /** Provides the sequence of [[Literal]]s and [[Hole]]s in this interpolated string. */
    def parts: Seq[StaticPart] = {
      val literalsHead +: literalsTail = literals.zipWithIndex.map { case (lit, idx) =>
        Literal(idx, lit)
      }

      literalsHead +: Seq(holes, literalsTail).transpose.flatten
    }

    private def position(part: StaticPart, offset: Int): macroContext.Position = {
      val errorTree: macroContext.Tree = part match {
        case Hole(index, _) => holeTrees(index)
        case Literal(index, _) => literalTrees(index)
      }
      
      errorTree.pos.withPoint(errorTree.pos.start + offset)
    }

    /** Report a compile error `message`, at the index `offset` within the [[Literal]] `part`,
      * and continue evaluating the macro, whilst compilation will ultimately fail.
      *
      * @param part the [[Literal]] part containing the error
      * @param offset the index of the error within the [[Literal]] part
      * @param message the error message to report */
    def error(part: Literal, offset: Int, message: String): Unit =
      macroContext.error(position(part, offset), message)

    /** Report a compile error `message`, at the index `offset` within the [[Literal]] `part`,
      * and stop further evaluation of the macro.
      *
      * @param part the [[Literal]] part containing the error
      * @param offset the index of the error within the [[Literal]] part
      * @param message the error message to report */
    def abort(part: Literal, offset: Int, message: String): Nothing =
      macroContext.abort(position(part, offset), message)

    /** Report a compile warning `message`, at the index `offset` within the [[Literal]] `part`,
      * and continue evaluating the macro, potentially succeeding despite the warning.
      *
      * @param part the [[Literal]] part containing the warning
      * @param offset the index of the warning within the [[Literal]] part
      * @param message the warning message to report */
    def warn(part: Literal, offset: Int, message: String): Unit =
      macroContext.warning(position(part, offset), message)

    /** Report a compile error `message`, at the [[Hole]] `part`, and continue evaluating the
      * macro, whilst compilation will ultimately fail.
      *
      * @param part the [[Hole]] part containing the error
      * @param message the error message to report */
    def error(part: Hole, message: String): Unit =
      macroContext.error(position(part, 0), message)

    /** Report a compile error `message`, at the [[Hole]] `part`, and stop evaluating the macro.
      *
      * @param part the [[Hole]] part containing the error
      * @param message the error message to report */
    def abort(part: Hole, message: String): Nothing =
      macroContext.abort(position(part, 0), message)

    /** Report a compile warning `message`, at the [[Hole]] `part`, and continue evaluating the
      * macro, potentially succeeding despite the warning.
      *
      * @param part the [[Hole]] part containing the warning
      * @param message the warning message to report */
    def warn(part: Hole, message: String): Unit =
      macroContext.warning(position(part, 0), message)

  }

  /** Validates the interpolated string, and returns a sequence of contexts for each hole in the
    * string.
    *
    * Each element of the sequence corresponds to a hole in the interpolated string, and
    * determines how substitutions should be interpreted for that hole. Typically, the
    * [[Context]] for a particular hole will be calculated by parsing the [[Literal]] part(s) of
    * the interpolated string before the hole.
    *
    * For example, when interpolating the JSON interpolated string
    * `json"""{ "key": \$value }"""`, parsing the literal `"""{ "key": """` should reveal that
    * the hole has a "context" where any other JSON value could be substituted. For other
    * interpolated strings, such as `json"""{ "id": "id-\$str" }"""`, parsing the first literal
    * would determine that the first hole (where `str` is inserted) is suitable for
    * substituting any string-like value, but not, say, a JSON object or array.
    *
    * These different contexts are represented by objects which subtype [[Context]], a sequence
    * of which should be returned from this method.
    *
    * @param interpolation the context of the interpolated string
    * @return the sequence of [[Context]]s corresponding to the holes
    */
  def contextualize(interpolation: StaticInterpolation): Seq[ContextType]

  /** The macro evaluator that defines what code will be generated for this [[Interpolator]].
    * The  default implementation constructs a new [[RuntimeInterpolation]] object, and invokes
    * a user-defined method called `evaluate` on the [[Interpolator]].
    *
    * Note that the `evaluate` method is not part of the explicit [[Interpolator]] interface,
    * and can be defined with type parameters or implicit parameters, as desired. It must only
    * conform to a shape such that it may be invoked (in macro-generated code) with
    *
    * <pre>
    * interpolator.evaluate(interpolation)
    * </pre>
    *
    * @param contexts the sequence of contexts corresponding to each hole in the interpolated
    * string, as the result of the compile-time invocation of [[contextualize]].
    * @param interpolation the static context in which evaluation is done
    */
  def evaluator(contexts: Seq[ContextType], interpolation: StaticInterpolation):
      interpolation.macroContext.Tree = {

    import interpolation.macroContext.universe._

    val substitutions = contexts.zip(interpolation.holeTrees).zipWithIndex.map {
      case ((ctx, Apply(Apply(_, List(value)), List(embedder))), idx) =>

        val cls = ctx.getClass
        val init :+ last = cls.getName.dropRight(1).split("\\.").toList

        val elements = init ++ last.split("\\$").toList
        
        val selector = elements.foldLeft(q"_root_": Tree) { case (t, p) =>
          Select(t, TermName(p))
        }

        q"""${interpolation.interpolatorTerm}.Substitution(
          $idx,
          $embedder($selector).apply($value)
        )"""
    }

    q"""${interpolation.interpolatorTerm}.evaluate(
      new ${interpolation.interpolatorTerm}.RuntimeInterpolation(
        _root_.scala.collection.immutable.Seq(..${interpolation.literals}),
        _root_.scala.collection.immutable.Seq(..$substitutions)
      )
    )"""
  }

  /** Factory for creating [[Embedder]]s.
    *
    * @tparam Value the type for which this [[Embedding]] will create [[Embedder]]s for */
  class Embedding[Value, Input] private[Interpolator]() {

    /** Factory method for creating [[Embedder]]s for embedding values of type `Value` for
      * an [[Interpolator]] of type `InterpolatorType`, typically inferring the type parameters.
      *
      * @param cases the functions for converting the `Value` type to `Input` for each supported
      * [[Context]]
      * @tparam ContextPair the intersection of "before" and "after" contexts, inferred from the
      * least upper-bound of the [[Case]]s
      * @tparam Input the common input type for this [[Interpolator]]
      * @return a new [[Embedder]] which handles the type `Value` for a number of [[Context]]s
      */
    def apply[ContextPair <: (Context, Context)]
        (cases: Case[ContextPair, Value, Input]*):
        Embedder[ContextPair, Value, Input, interpolator.type] =
      new Embedder(cases.toSeq)
  }

  /** Intermediate factory method for making new [[Embedder]] typeclasses, via the
    * [[Embedding]] class, which only exists as a half-way house for inferring most type
    * parameters, while having the type `Value` specified explicitly.
    *
    * @tparam Value
    * */
  def embed[Value]: Embedding[Value, Input] = new Embedding()

  /** The common supertype of runtime and compile-time (static) parts of an interpolated
    * string, namely [[Literal]]s (common to both runtime and compile-time contexts),
    * [[Hole]]s (compile-time only) and [[Substitution]]s (runtime only). */
  sealed trait Part extends Product with Serializable

  /** Sealed trait of parts that are known at compile-time. This is only [[Literal]] and
   *  [[Hole]] values. Note that [[Literal]]s are also available at runtime. */
  sealed trait StaticPart extends Part with Product with Serializable { def index: Int }

  /** Sealed trait of parts that are known at runtime. This is only [[Literal]] and
    * [[Substitution]] values. Note that [[Literal]]s are also available at compile-time. */
  sealed trait RuntimePart extends Part with Product with Serializable { def index: Int }

  /** A [[Hole]] represents all that is known at compile-time about a substitution into an
    * interpolated string. */
  case class Hole(index: Int, input: Map[ContextType, ContextType]) extends StaticPart {

    /** A string representation of a hole, indicating its possible contexts */
    override def toString: String = input.keys.mkString("[", "|", "]")

    /** Gets the post-substitution [[Context]], provided the pre-substitution [[Context]] is
      * defined for this [[Hole]].
      *
      * @param context the pre-substitution [[Context]]
      * @return the post-substitution [[Context]], or `None` if it is not defined */
    def apply(context: ContextType): Option[ContextType] = input.get(context)
  }

  /** Represents a known value (at runtime) that is substituted into an interpolated string.
    *
    * @param index the integer index of this substitution within the interpolated string
    * @param value the substituted value, converted to the common input type */
  case class Substitution(index: Int, value: Input) extends RuntimePart {

    /** Gets the substituted value. 
      *
      * @return the substituted value, converted to the common input type */
    def apply(): Input = value

    /** The string representation of the substituted value */
    override def toString = value.toString
  }

  /** Represents a fixed, constant part of an interpolated string, known at compile-time.
    *
    * @param index the integer index of this literal within the interpolated string
    * @param string the actual string literal */
  case class Literal(index: Int, string: String) extends StaticPart with RuntimePart {
    /** The string literal */
    override def toString: String = string
  }
}

/** Factory object for creating [[Case]]s. */
object Case {
  /** Creates a new [[Case]] for instances of type `Value`, specifying the `context`
    * in which that type may be substituted, and `after` context.
    *
    * For example, the case defined by,
    *
    * <pre>
    * Case(Param, AfterParam) { (p: Int) =&gt; s"'\$p'" }
    * </pre>
    *
    * handles substitutions at a hole with a hypothetical `Param` [[Context]] of integer
    * values, converting them to a string by wrapping them in single-quotes, and specifying the
    * hypothetical `AfterParam` [[Context]] for the continuation of parsing, following the
    * substitution.
    *
    * For any [[Interpolator]] which parses the interpolated string, allows substitutions, and
    * uses multiple [[Context]]s, the `after` value is important in determining how making the
    * substitution changes the parse context. For example, considering the hole in,
    *
    * <pre>
    * json"""{ "key": \$value }"""
    * </pre>
    *
    * prior to the substitution of `value`, the [[Context]] would be a value representing a
    * position where any JSON value may be substituted, but following the substitution, i.e.
    * after the JSON value has been substituted, the parsing context has changed, as we would
    * only permit a comma (followed by more key/value pairs), or a closing brace; nothing else
    * is acceptable. We would therefore specify an `after` [[Context]] which represents this
    * state.
    *
    * @param context the [[Context]] before the substitution
    * @param after the [[Context]] after the substitution
    * @param conversion the conversion function to the [[Interpolator]]'s common input type
    * @tparam Before the type (typically inferred) of the [[Context]] before the transition
    * @tparam After the type (typically inferred) of the [[Context]] after the transition
    * @tparam Value the type for which this [[Case]] handles conversion
    * @tparam Input the common input type to which all substitutions are converted, often (but
    * not always) `String`
    * @return a new [[Case]] for handling embedding a `Value` in the specified [[Context]] */
  def apply[Before <: Context, After <: Context, Value, Input](context: Before, after: After)
      (conversion: Value => Input): Case[(Before, After), Value, Input] =
    new Case(context, after, conversion)
}

/** A [[Case]] specifies for a particular [[Context]] how a value of type `Value` should
  * be converted into the appropriate `Input` type to an [[Interpolator]], and how the
  * application of the value should change the [[Context]] in the interpolated string.
  *
  * @param context the [[Context]] before the substitution
  * @param after the [[Context]] after the substitution is made
  * @tparam ContextPair the "before" and "after" contexts, represented as a pair
  * @tparam Value the type for which this [[Case]] handles conversion
  * @tparam Input the common input type to which all substitutions are converted for this
  * [[Interpolator]], often (but not always) `String`
  * */
class Case[-ContextPair <: (Context, Context), -Value, +Input] private[contextual]
    (val context: Context, val after: Context, val conversion: Value => Input)

/** An [[Embedder]] defines, for an [[Interpolator]], `InterpolatorType`, a type `Value` should
  * be converted to the common input type `Input`, when substituted into different context
  * positions. These should be created using the [[Interpolator#embed]] method.
  *
  * @param cases the functions for converting the `Value` type to `Input` for each supported
  * [[Context]]
  * @tparam ContextPair the intersection of "before" and "after" contexts, inferred from the
  * least upper-bound of the [[Case]]s
  * @tparam Value the type this [[Embedder]] is defining substitution functions for
  * @tparam Input the common input type to which all substitutions are converted for this
  * [[Interpolator]]
  * @tparam InterpolatorType the [[Interpolator]] singleton type for which this [[Embedder]] is
  * defining substitution functions */
class Embedder[ContextPair <: (Context, Context), Value, Input,
    InterpolatorType <: Interpolator](val cases: Seq[Case[ContextPair, Value, Input]]) {

  /** Retrieve the conversion function from the `Value` type to the `Input` type for the
    * specified [[Context]].
    *
    * @param holeContext the context of the hole for which to retrieve the conversion function
    * @tparam HoleContext the singleton type corresponding to the context of the hole
    * @return the function for converting the `Value` type to an `Input` for the specified
    * Context
    */
  def apply[HoleContext](holeContext: HoleContext)
      (implicit evidence: ContextPair <:< (HoleContext, Context)): Value => Input =
    cases.find(_.context == holeContext).get.conversion
}

/** Companion object for the [[Interpolator]], containing related definitions. */
object Interpolator {

  /** The [[embed]] implicit method which automatically converts acceptable types to the
    * [[Embedded]] type, binding them with their corresponding [[Embedder]], which defines how
    * that type should be converted to a common input type in different contexts.
    *
    * @param value the value of type `Value` being embedded
    * @param embedder the implicit [[Embedder]] for values of type `Value`, whose existence (or
    * not) determines whether embedding of the `Value` type is possible
    * @tparam ContextPair the inferred intersection of "before" and "after" contexts for the
    * embedding, inferred by the implicit [[Embedder]] instance
    * @tparam Value the type of the value being embedded, inferred from the type being
    * substituted into the interpolated string
    * @tparam Input the common type to which all substitutions are converted for this
    * [[Interpolator]]
    * @tparam InterpolatorType the singleton type of the [[Interpolator]], inferred from the
    * expected type of the [[Embedded]] parameter
    * @return an [[Embedded]] instance, matching the required type of the holes in the
    * interpolated string
    */
  implicit def embed[ContextPair <: (Context, Context), Value, Input,
      InterpolatorType <: Interpolator](value: Value)
      (implicit embedder: Embedder[ContextPair, Value, Input, InterpolatorType]):
      Embedded[Input, InterpolatorType] =
    new Embedded[Input, InterpolatorType] {
      def apply(context: Context): Input = embedder(context).apply(value)
    }

  /** A value which has been embedded as a substitution into an interpolated string, using the
    * implicit [[embed]] method.
    *
    * @tparam Input the common input type for all substitutions for this [[Interpolator]]
    * @tparam InterpolatorType the type of [[Interpolator]] for which this value is embedded */
  abstract class Embedded[Input, InterpolatorType <: Interpolator] private[contextual] {
    /** Apply the conversion function for the specified `context`
      *
      * @param context the [[Context]] to lookup for this embedding
      * @return the common `Input` type for this interpolator */
    def apply(context: Context): Input
  }
}

abstract class Verifier[Out] extends Interpolator {
 
  type Output = Out
  type ContextType = Context

  def check(string: String): Either[(Int, String), Out]

  def noSubsMsg: String = "substitutions are not permitted"

  def contextualize(interpolation: StaticInterpolation): Seq[ContextType] = {
    interpolation.parts.foreach {
      case lit@Literal(_, string) =>
        check(string) match {
          case Left((pos, error)) =>
            interpolation.abort(lit, pos, error)
          case Right(_) => ()
        }
      case hole@Hole(_, _) =>
        interpolation.abort(hole, noSubsMsg)
    }
    Nil
  }

  def evaluate(contextual: RuntimeInterpolation): Out =
    check(contextual.parts.mkString).toOption.get

}
