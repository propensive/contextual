package contextual

import scala.reflect._, macros.whitebox

import language.experimental.macros

/** Object containing the main macro providing Contextual's functionality. */
object Macros {
  def contextual[C <: Context, P <: Interpolator { type Ctx = C }: c.WeakTypeTag]
      (c: whitebox.Context)(exprs: c.Tree*): c.Tree = {
    
    import c.universe.{Literal => AstLiteral, _}
    
    /* Get the string literals from the constructed `StringContext`. */
    val astLiterals = c.prefix.tree match {
      case Select(Apply(_, List(Apply(_, lits))), _) => lits
    }
    
    val literals = astLiterals.map { case AstLiteral(Constant(str: String)) => str }
   
    /* Get the "context" types derived from each parameter. */
    val appliedParameters = c.macroApplication match {
      case Apply(_, params) => params
    }
   
    /* Work out Java name of the class we want to instantiate. This is necessary because classes
     * defined within objects have the names of their parent objects encoded in their class
     * names, yet are presented in symbols in the standard "dotted" style, e.g.
     * `package.Object.Class` is encoded as `package.Object$Class`. */
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
    
    /* Get an instance of the Interpolator class. */
    val interpolator = try {
      getModule[P](weakTypeOf[P])
    } catch {
      case e: Exception => c.abort(c.enclosingPosition, e.toString)
    }

    val parameterTypes: Seq[interpolator.Hole] = appliedParameters.zipWithIndex.map {
      case (Apply(Apply(TypeApply(_, List(contextType, _, _, _)), _), _), idx) =>
        val types: Set[Type] = contextType.tpe match {
          case SingleType(_, singletonType) => Set(singletonType.typeSignature)
          case RefinedType(intersectionTypes, _) => intersectionTypes.to[Set]
          case typ: Type => Set(typ)
        }

        val contextObjects = types.map { t =>
          (getModule[C](t.typeArgs(0)), getModule[C](t.typeArgs(1)))
        }.toMap

        interpolator.Hole(idx, contextObjects)
    }

    val combinedParts: Seq[interpolator.StaticToken] =
      interpolator.Literal(0, literals.head) +: Seq(
        parameterTypes.to[List],
        literals.to[List].tail.zipWithIndex.map { case (v, i) =>
          interpolator.Literal(i + 1, v)
        }
      ).transpose.flatten


    val contextual =
      new interpolator.Contextual[interpolator.StaticToken](literals, parameterTypes) {
        val context: c.type = c
        def expressions = exprs
      }

    try interpolator.implementation(contextual).tree catch {
      case InterpolationError(part, offset, message) =>
        val (errorLiteral, length) = astLiterals(part) match {
          case lit@AstLiteral(Constant(str: String)) => (lit, str.length)
        }

        /* Calculate the error position from the start of the corresponding literal part, plus
         * the offset. */
        val realOffset = if(offset < 0) length else (offset min length)
        val errorPosition = errorLiteral.pos.withPoint(errorLiteral.pos.start + realOffset)

        c.abort(errorPosition, message)
    }
  }
}


