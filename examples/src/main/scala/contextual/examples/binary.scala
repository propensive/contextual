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
package contextual.examples

import contextual._

object binary {

  sealed trait BinaryReprContext extends Context
  case object BinaryReprHole extends BinaryReprContext

  object BinParser extends Interpolator {
    type ContextType = BinaryReprContext
    type Input = String
    type Output = Array[Byte]

    def contextualize(interpolation: StaticInterpolation) = Nil

    override def evaluator(contexts: Seq[ContextType], interpolation: StaticInterpolation):
        interpolation.universe.Tree = {
      import interpolation.universe.{Literal => _, _}

      val substitutions = interpolation.holeTrees.zipWithIndex.map {
        case (Apply(Apply(_, List(value)), List(embedder)), idx) =>
          q"""${interpolation.interpolatorTerm}.Substitution(
            $idx,
            $value
          ).value"""
      }

      val (_, litSize, res) = interpolation.parts.foldLeft((substitutions, 0, Seq[Tree]())) {
        case ((substitutions, litSize, finalTree), lit@Literal(index, string)) =>

          // Fail on any uses of non-binary characters
          string.zipWithIndex.map { case (ch, idx) =>
            if(ch != '0' && ch != '1')
              interpolation.error(lit, idx, "only '0' and '1' are valid")
          }

          (substitutions, litSize + string.size, finalTree :+ q"finalStr += $string")

        case ((hSubstition :: tSubstitutions, litSize, finalTree), hole@Hole(_, _)) =>
          (tSubstitutions, litSize, finalTree :+ q"""{
              val v = $hSubstition
              if (v.exists(c => c != '1' && c != '0'))
                throw new java.lang.IllegalArgumentException("only '0' and '1' are valid")
              finalStr += v
            }""")

        case (_, hole@Hole(_, _)) =>
          interpolation.abort(hole, "found a hole with no valid substitution to include")
      }

      if (substitutions.isEmpty && litSize % 8 != 0)
        interpolation.abort(interpolation.parts.head.asInstanceOf[Literal], 0,
                            "binary size is not an exact number of bytes")

      q"""{
        var finalStr = ""
        ..$res
        finalStr.grouped(8).map(Integer.parseInt(_, 2).toByte).toArray
      }"""
    }
  }

  implicit val embedStrings = BinParser.embed[String](
    Case(BinaryReprHole, BinaryReprHole) { identity }
  )

  implicit class BinaryStringContext(sc: StringContext) { val bin = Prefix(BinParser, sc) }
}
