package contextual

import scala.reflect._, macros._
import scala.annotation.implicitNotFound

import language.experimental.macros
import language.higherKinds
import language.implicitConversions
import language.existentials

/** represents a compile-time failure in interpolation */
case class InterpolationError(part: Int, offset: Int, message: String)

object Context {
  sealed trait NoContext extends Context
  object NoContext extends NoContext
}

trait Context

object Embedded {
  implicit def embed[C <: Context, V, R](value: V)(implicit handler: Handler[C, V, R]): Embedded[R] {
      type Ctx = C
      type Value = V
    } = new Embedded[R] {
    def apply(ctx: Context): R = {
      handler.handle(ctx).apply(value)
    }
    type Ctx = C
    type Value = V
  }
}

abstract class Embedded[R] {
  type Ctx
  type Value
  def apply(ctx: Context): R
}

object Prefix {
  def simple[P <: Interpolator { type Ctx = Context.NoContext }](interpolator: P, stringContext: StringContext) =
    new Prefix[Context.NoContext, P](interpolator, stringContext.parts)

  class WithContext[C <: Context] private[Prefix]() {
    def apply[P <: Interpolator { type Ctx = C }](interpolator: P, stringContext: StringContext) = 
      new Prefix[C, P](interpolator, stringContext.parts)
  }

  def withContext[C <: Context] = new WithContext[C]()
}

class Prefix[C <: Context, P <: Interpolator { type Ctx = C }](interpolator: P, parts: Seq[String]) {
  def apply(exprs: Embedded[interpolator.Inputs]*): Any = macro Macros.contextual[interpolator.Result, C, P]
}

sealed trait RuntimeParseToken[+I]
sealed trait CompileParseToken[+I]

case class Hole[I](input: Set[I]) extends CompileParseToken[I]

case class Literal(index: Int, string: String) extends CompileParseToken[Nothing] with
    RuntimeParseToken[Nothing]

case class Variable[+I](value: I) extends RuntimeParseToken[I]

trait Parser extends Interpolator {
 
  def contexts(tokens: Seq[Literal]): Seq[Ctx]
  
  def construct(tokens: Seq[RuntimeParseToken[Inputs]]): Result

  def compile[P: c.WeakTypeTag](c: whitebox.Context)(literals: Seq[String],
      parameters: Seq[c.Tree], holes: Seq[Hole[Ctx]]): c.Tree = {
    import c.universe.{Literal => _, _}

    val literalTokenTrees: Seq[c.Tree] = literals.to[List].zipWithIndex.map {
      case (lit, idx) => q"_root_.contextual.Literal($idx, $lit)"
    }

    val literalTokens: Seq[Literal] = literals.to[List].zipWithIndex.map {
      case (lit, idx) => Literal(idx, lit)
    }

    val contextTypes = contexts(literalTokens).zip(holes).zip(parameters).map { case ((ctx, hole), param) =>
      val className = ctx.getClass.getName
      
      if(!hole.input.contains(ctx)) c.error(param.pos, s"expected a value suitable for context ${className.dropRight(1)}")
      
      val classInstance = q"_root_.java.lang.Class.forName($className)"
      q"""$classInstance.getField("MODULE$$").get($classInstance).asInstanceOf[Context]"""
    }

    val parameterTokens = parameters.zip(contextTypes).map { case (param, ctx) =>
      q"_root_.contextual.Variable(${param}($ctx))"
    }

    val tokens = literalTokenTrees.head :: List(parameterTokens, literalTokenTrees.tail).transpose.flatten

    q"${weakTypeOf[P].termSymbol}.construct(_root_.scala.List(..${tokens}))"
  }
}

trait Interpolator {
  type Ctx <: Context
  type Inputs  
  type Result

  def compile[P: c.WeakTypeTag](c: whitebox.Context)(literals: Seq[String],
      parameters: Seq[c.Tree], holes: Seq[Hole[Ctx]]): c.Tree
  
  def verify(constants: Seq[CompileParseToken[Ctx]]): Seq[InterpolationError]

  class Embedding[I] protected[Interpolator] () {
    def apply[C <: Context, R](cases: Case[C, I, R]*): Handler[C, I, R] =
      new Handler(cases.to[List])
  }

  def embed[I]: Embedding[I] = new Embedding[I]()
}

object Macros {
  def contextual[R, C <: Context, P <: Interpolator { type Ctx = C }: c.WeakTypeTag](c: whitebox.Context)(exprs: c.Tree*): c.Tree = {
    import c.universe.{Literal => AstLiteral, _}
    
    // Get the string literals from the constructed `StringContext`.
    val astLiterals = c.prefix.tree match {
      case Select(Apply(_, List(Apply(_, lits))), _) => lits
    }
    
    val literals = astLiterals.map { case AstLiteral(Constant(str: String)) => str }
   
    // Get the "context" types derived from each parameter.
    val appliedParameters = c.macroApplication match {
      case Apply(_, params) => params
    }
   
    // Work out Java name of the class we want to instantiate. This is necessary because classes
    // defined within objects have the names of their parent objects encoded in their class names,
    // yet are presented in symbols in the standard "dotted" style, e.g. `package.Object.Class` is
    // encoded as `package.Object$Class`.
    def javaClassName(sym: Symbol): String =
      if(sym.owner.isPackage) sym.fullName
      else if(sym.owner.isModuleClass) s"${javaClassName(sym.owner)}$$${sym.name}"
      else s"${javaClassName(sym.owner)}.${sym.name}"

    def getModule[M](tpe: Type) = {
      val typeName = javaClassName(tpe.typeSymbol)
      val cls = Class.forName(s"$typeName$$")
      cls.getField("MODULE$").get(cls).asInstanceOf[M]
    }

    val parameterTypes: Seq[Hole[C]] = appliedParameters.map {
      case Apply(Apply(TypeApply(_, List(contextType, _, _)), _), _) =>
        val types: Set[Type] = contextType.tpe match {
          case SingleType(_, singletonType) => Set(singletonType.typeSignature)
          case RefinedType(intersectionTypes, _) => intersectionTypes.to[Set]
        }
       
        Hole[C](types.map { t => getModule[C](t.erasure) })
    }

    val interpolatorName = javaClassName(weakTypeOf[P].typeSymbol)
    
    // Get an instance of the Interpolator class
    val interpolator = try {
      getModule[P](weakTypeOf[P])
    } catch {
      case e: Exception => c.abort(c.enclosingPosition, e.toString)
    }

    val combinedParts: List[CompileParseToken[C]] = Literal(0, literals.head) :: List(
        parameterTypes.to[List], literals.to[List].tail.zipWithIndex.map { case (v, i) =>
        Literal(i + 1, v) }).transpose.flatten


    // Fail on any errors found during parsing
    interpolator.verify(combinedParts).foreach {
      case InterpolationError(part, offset, message) =>
        val errorLiteral = astLiterals(part) match {
          case lit@AstLiteral(Constant(str: String)) => lit
        }

        // Calculate the error position from the start of the corresponding literal part, plus
        // the offset.
        val errorPosition = errorLiteral.pos.withPoint(errorLiteral.pos.start + offset)
      
        c.error(errorPosition, message)
    }

    val result = interpolator.compile(c)(literals, exprs, parameterTypes)
    result
  }
}


object into {
  def apply[C <: Context, V, R](context: C)(fn: V => R): Case[C, V, R] = Case(context, fn)
}

case class Case[-C <: Context, -V, +R](context: Context, fn: V => R)

class Handler[C <: Context, V, R](val cases: List[Case[C, V, R]]) {
  def handle[C2](c: C2)(implicit ev: C <:< C2): V => R = {
    cases.find(_.context == c).get.fn
  }
}
