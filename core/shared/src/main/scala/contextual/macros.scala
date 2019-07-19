/* Contextual, version 1.1.0. Copyright 2018 Jon Pretty, Propensive Ltd.
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

import scala.reflect._, macros.whitebox

/** Macro bundle class containing the main macro providing Contextual's functionality. */
object Macros {

  def contextual[C <: Context, I <: Interpolator { type ContextType = C }: c.WeakTypeTag]
      (c: whitebox.Context)(expressions: c.Tree*): c.Tree = {
    import c.universe.{Literal => AstLiteral, _}

    /* Get the string literals from the constructed `StringContext`. */
    val astLiterals = c.prefix.tree match {
      case Select(Apply(_, List(Apply(_, lits))), _)           => lits
      case Select(Apply(Apply(_, List(Apply(_, lits))), _), _) => lits
    }

    val stringLiterals: Seq[String] = astLiterals.map {
      case AstLiteral(Constant(str: String)) => str
    }

    /* Get the "context" types derived from each parameter. */
    val appliedParameters: Seq[Tree] = c.macroApplication match {
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

      try {
        val cls = Class.forName(s"$typeName$$")
        cls.getField("MODULE$").get(cls).asInstanceOf[M]
      } catch {
        case e: ClassNotFoundException =>
          c.abort(c.enclosingPosition, s"""Class "${typeName}" could not be found. This usually means you are trying to use an interpolator in the same compilation unit as the one in which you defined it. Please try compiling interpolators first, separately from the code using them.""")
      }
    }

    /* Get an instance of the Interpolator class. */
    val interpolator = try getModule[I](weakTypeOf[I]) catch {
      case e: Exception => c.abort(c.enclosingPosition, e.toString)
    }

    val parameterTypes: Seq[interpolator.Hole] = appliedParameters.zipWithIndex.map {
      case (Apply(Apply(TypeApply(_, List(contextType, _, _, _)), _), _), idx) =>
        val types: Set[Type] = contextType.tpe match {
          case SingleType(_, singletonType) => Set(singletonType.typeSignature)
          case RefinedType(intersectionTypes, _) => intersectionTypes.toSet
          case typ: Type => Set(typ)
        }

        val contextObjects = types.map { t =>
          (getModule[C](t.typeArgs(0)), getModule[C](t.typeArgs(1)))
        }.toMap

        interpolator.Hole(idx, contextObjects)
    }

    val interpolation: interpolator.StaticInterpolation { val macroContext: c.type } =
      new interpolator.StaticInterpolation {
        val macroContext: c.type = c
        val literals: Seq[String] = stringLiterals
        val holes: Seq[interpolator.Hole] = parameterTypes

        def holeTrees: Seq[c.Tree] = expressions
        def literalTrees: Seq[c.Tree] = astLiterals
        def interpolatorTerm: c.Symbol = weakTypeOf[I].termSymbol
      }

    val contexts: Seq[interpolator.ContextType] = interpolator.contextualize(interpolation)

    if(contexts.size != interpolation.holes.size)
      c.abort(
        c.enclosingPosition,
        s"`contextualize` must return exactly one ContextType for each hole"
      )

    interpolator.evaluator(contexts, interpolation)
  }
}
