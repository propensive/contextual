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

object hex {

  object HexParser extends Interpolator {

    type Output = Array[Byte]

    def contextualize(interpolation: StaticInterpolation): Seq[ContextType] = Nil

    override def evaluator(contexts: Seq[ContextType], interpolation: StaticInterpolation):
        interpolation.universe.Tree = {
      import interpolation.universe.{Literal => _, _}

      val bytes = interpolation.parts.flatMap {
        case lit@Literal(index, string) =>
          string.toLowerCase.zipWithIndex.foreach { case (ch, idx) =>
            if(ch < 48 || (ch > 57 && ch < 97) || ch > 102)
              interpolation.error(lit, idx, "bad hexadecimal digit")
          }

          if(string.length%2 != 0) interpolation.abort(lit, 0,
              "hexadecimal size is not an exact number of bytes")

          string.grouped(2).map(Integer.parseInt(_, 16).toByte).to[List].zipWithIndex.map {
            case (byte, idx) => q"array($idx) = $byte"
          }

        case hole@Hole(_, _) =>
          interpolation.abort(hole, "substitutions are not supported")

      }

      val size = bytes.size

      q"""{
        val array = new Array[Byte]($size)
        ..$bytes

        array
      }"""
    }
  }

  implicit class HexStringContext(sc: StringContext) { val hex = Prefix(HexParser, sc) }
}
