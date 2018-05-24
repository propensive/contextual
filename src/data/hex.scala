/*
  
  Contextual, version 1.1.0. Copyright 2018 Jon Pretty, Propensive Ltd.

  The primary distribution site is: https://propensive.com/

  Licensed under the Apache License, Version 2.0 (the "License"); you may not use
  this file except in compliance with the License. You may obtain a copy of the
  License at
  
      http://www.apache.org/licenses/LICENSE-2.0
 
  Unless required by applicable law or agreed to in writing, software
  distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
  WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
  License for the specific language governing permissions and limitations under
  the License.

*/
package contextual.data

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
          val hexString =
            if(string.startsWith("0x")) string.drop(2)
            else if (string.startsWith("#")) string.drop(1)
            else string
          
          val invalidDigits = hexString.zipWithIndex.filterNot { case (ch, _) =>
            val lowerCh = ch.toLower
            !(lowerCh < 48 || (lowerCh > 57 && ch < 97) || lowerCh > 102)
          }

          invalidDigits.foreach { case (ch, idx) =>
            interpolation.error(lit, idx, "bad hexadecimal digit")
          }

          if(invalidDigits.nonEmpty) interpolation.abort(lit, 0,
            "hexadecimal string has invalid digits")

          if(hexString.length%2 != 0) interpolation.abort(lit, 0,
              "hexadecimal size is not an exact number of bytes")

          hexString.grouped(2).map(Integer.parseInt(_, 16).toByte).to[List].zipWithIndex.map {
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
