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
package contextual.examples

import contextual._

object binary {

  object BinParser extends Interpolator {

    def contextualize(interpolation: StaticInterpolation) = Nil

    override def evaluator(contexts: Seq[ContextType], interpolation: StaticInterpolation):
        interpolation.universe.Tree = {
      import interpolation.universe.{Literal => _, _}

      val bytes = interpolation.parts.flatMap {
        case lit@Literal(index, string) =>

          // Fail on any uses of non-binary characters
          string.zipWithIndex.map { case (ch, idx) =>
            if(ch != '0' && ch != '1')
              interpolation.error(lit, idx, "only '0' and '1' are valid")
          }

          // Fail if it's the wrong length
          if(string.length%8 != 0) interpolation.abort(lit, 0,
              "binary size is not an exact number of bytes")

          // Convert the string to a sequence of assignment operations
          string.grouped(8).map(Integer.parseInt(_, 2).toByte).to[List].zipWithIndex.map {
            case (byte, idx) => q"array($idx) = $byte"
          }

        case hole@Hole(_, _) =>

          // We don't support substitutions (yet)
          interpolation.abort(hole, "can't make substitutions")

      }

      val size = bytes.size

      // The code that will be evaluated at runtime
      q"""{
        val array = new Array[Byte]($size)
        ..$bytes

        array
      }"""
    }
  }

  implicit class BinaryStringContext(sc: StringContext) { val bin = Prefix(BinParser, sc) }
}
