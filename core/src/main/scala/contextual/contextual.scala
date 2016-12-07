package contextual

import scala.reflect._, macros._
import scala.annotation.implicitNotFound

import language.experimental.macros
import language.higherKinds
import language.implicitConversions
import language.existentials

/** represents a compile-time failure in interpolation */
case class InterpolationError(part: Int, offset: Int, message: String) extends Exception
case class ParseError(msg: String) extends Exception

object Context {
  sealed trait NoContext extends Context
  object NoContext extends NoContext
}

trait Context {
  override def toString: String = getClass.getName.split("\\.").last.dropRight(1)
}

object Embedded {
  implicit def embed[CC <: (Context, Context), V, R, I <: Interpolator](value: V)(implicit handler: Handler[CC, V, R, I]): Embedded[R, I] =
    new Embedded[R, I] {
      def apply(ctx: Context): R = {
        handler(ctx).apply(value)
      }
    }
}

abstract class Embedded[R, I <: Interpolator] {
  def apply(ctx: Context): R
}

// FIXME: Do we need Simple and WithContext?
object Prefix {
  def apply(interpolator: Interpolator, stringContext: StringContext): Prefix[interpolator.Ctx, interpolator.type] =
    new Prefix(interpolator, stringContext.parts)
}

class Prefix[C <: Context, P <: Interpolator { type Ctx = C }](interpolator: P, parts: Seq[String]) {
  def apply(exprs: Embedded[interpolator.Inputs, interpolator.type]*): Any = macro Macros.contextual[C, P]
}

/*trait Parser extends Interpolator {
 
  def construct(tokens: Seq[RuntimeParseToken[Inputs]]): Any

  def initialState: Ctx
  def next: (Ctx, Char) => Ctx
  def endFailure(state: Ctx): Option[String] = None

  private def nextLiteral(lit: String, state: Ctx, holeNo: Int): Ctx = lit.indices.foldLeft(state) { (ctx: Ctx, idx: Int) =>
    try next(ctx, lit(idx)) catch {
      case ParseError(msg) => throw InterpolationError(holeNo, idx, msg)
    }
  }

  private def contexts(c: whitebox.Context)(tokens: Seq[Literal], holes: Seq[Hole[Ctx]]): Seq[Ctx] = {
    val tokensWithHoles = tokens.zip(holes.map(Some(_)) :+ None)
    
    val (finalHoles, _) = tokensWithHoles.foldLeft((List[Ctx](), initialState)) {
      case ((holes, state), (Literal(_, lit), hole)) =>
        val currentIndex = holes.length
        
        val holeState = nextLiteral(lit, state, holes.size)
        
        val context: Option[(Ctx, Ctx)] = hole.map(_.input.find(_._1 == holeState).getOrElse {
          val className = holeState.getClass.getName
          throw InterpolationError(currentIndex, tokens(currentIndex).string.length, s"expected a value suitable for context ${className.dropRight(1)}")
        })
        val afterHoleState = context.map(_._2).getOrElse(holeState)

        if(currentIndex + 1 == tokens.size) {
          endFailure(holeState).foreach { msg =>
            throw InterpolationError(tokens.length - 1, tokens.last.string.length, msg)
          }
        }

        (holeState :: holes, afterHoleState)
    }

    finalHoles.tail.reverse
  }

  override def compile[P: c.WeakTypeTag](c: whitebox.Context)(literals: Seq[String],
      parameters: Seq[c.Tree], holes: Seq[Hole[Ctx]]): c.Tree = {
    import c.universe.{Literal => _, _}

    val literalTokenTrees: Seq[c.Tree] = literals.to[List].zipWithIndex.map {
      case (lit, idx) => q"_root_.contextual.Literal($idx, $lit)"
    }

    val literalTokens: Seq[Literal] = literals.to[List].zipWithIndex.map {
      case (lit, idx) => Literal(idx, lit)
    }

    val contextTypes = contexts(c)(literalTokens, holes).zip(holes).zip(parameters).map { case ((ctx, hole), param) =>
      val className = ctx.getClass.getName
    
      val errorMsg = s"expected a value suitable for context ${className.dropRight(1)}"

      if(!hole.input.exists(_._1 == ctx)) c.error(param.pos, errorMsg)

      val classInstance = q"_root_.java.lang.Class.forName($className)"
      q"""$classInstance.getField("MODULE$$").get($classInstance) match {
            case c: _root_.contextual.Context => c
          }
      """
    }

    val parameterTokens = parameters.zip(contextTypes).map { case (param, ctx) =>
      q"_root_.contextual.Variable(${param}($ctx))"
    }

    val tokens = literalTokenTrees.head :: List(parameterTokens, literalTokenTrees.tail).transpose.flatten

    q"${weakTypeOf[P].termSymbol}.construct(_root_.scala.List(..${tokens}))"
  }
}*/

trait Interpolator { interpolator =>
    
  sealed trait RuntimeParseToken[+I]
  sealed trait CompileParseToken[+I]

  case class Hole[C <: Context](input: Set[(C, C)]) extends CompileParseToken[(C, C)] {
    override def toString: String = "<--->"
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
      val head :: tail = literals.to[List].zipWithIndex.map { case (lit, idx) => Literal(idx, lit) }
      head :: List(holes, tail).transpose.flatten
    }
  
  }

  def implementation(contextual: Contextual): contextual.Implementation

  def parse(string: String): Any = string

  class Embedding[I] protected[Interpolator] () {
    def apply[CC <: (Context, Context), R](cases: Case[CC, I, R]*): Handler[CC, I, R, interpolator.type] =
      new Handler(cases.to[List])
  }

  def embed[I]: Embedding[I] = new Embedding()
}

object Macros {
  def contextual[C <: Context, P <: Interpolator { type Ctx = C }: c.WeakTypeTag](c: whitebox.Context)(exprs: c.Tree*): c.Tree = {
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

    def getModule[M](tpe: Type): M = {
      val typeName = javaClassName(tpe.typeSymbol)
      val cls = Class.forName(s"$typeName$$")
      
      cls.getField("MODULE$").get(cls).asInstanceOf[M]
    }

    val interpolatorName = javaClassName(weakTypeOf[P].typeSymbol)
    
    // Get an instance of the Interpolator class
    val interpolator = try {
      getModule[P](weakTypeOf[P])
    } catch {
      case e: Exception => c.abort(c.enclosingPosition, e.toString)
    }

    val parameterTypes: Seq[interpolator.Hole[C]] = appliedParameters.map {
      case Apply(Apply(TypeApply(_, List(contextType, _, _, _)), _), _) =>
        val types: Set[Type] = contextType.tpe match {
          case SingleType(_, singletonType) => Set(singletonType.typeSignature)
          case RefinedType(intersectionTypes, _) => intersectionTypes.to[Set]
          case typ: Type => Set(typ)
        }
        
        interpolator.Hole[C](types.map { t => (getModule[C](t.typeArgs(0)), getModule[C](t.typeArgs(1))) })
    }

    val combinedParts: List[interpolator.CompileParseToken[(C, C)]] =
      interpolator.Literal(0, literals.head) :: List(
        parameterTypes.to[List], literals.to[List].tail.zipWithIndex.map { case (v, i) =>
            interpolator.Literal(i + 1, v) }
      ).transpose.flatten


    val contextual = new interpolator.Contextual(literals, parameterTypes) {
      val context: c.type = c
      def expressions = exprs
    }

    try interpolator.implementation(contextual).tree catch {
      case InterpolationError(part, offset, message) =>
        val errorLiteral = astLiterals(part) match {
          case lit@AstLiteral(Constant(str: String)) => lit
        }
        // Calculate the error position from the start of the corresponding literal part, plus
        // the offset.
        val errorPosition = errorLiteral.pos.withPoint(errorLiteral.pos.start + offset)
      
        c.abort(errorPosition, message)
    }
  }
}


object transition {
  def apply[C <: Context, C2 <: Context, V, R](context: C, after: C2)(fn: V => R): Case[(C, C2), V, R] = Case(context, after, fn)
}

case class Case[-CC <: (Context, Context), -V, +R](context: Context, after: Context, fn: V => R)

class Handler[CC <: (Context, Context), V, R, I <: Interpolator](val cases: List[Case[CC, V, R]]) {
  def apply[C2](c: C2)(implicit ev: CC <:< (C2, Context)): V => R =
    cases.find(_.context == c).get.fn
}

