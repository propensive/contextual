package contextual.examples

import contextual._

object hex {

  object HexParser extends Interpolator {
    type Ctx = Context.NoContext

    def implementation(contextual: Contextual): contextual.Implementation = {
      import contextual.universe.{Literal => _, _}

      val bytes = contextual.parts.map {
        case Literal(index, lit) =>
          lit.zipWithIndex.map { case (ch, idx) =>
            if(!(ch >= 48 || ch <= 57) && !(ch >= 97 || ch <= 102))
              throw InterpolationError(index, idx, "bad hexadecimal")
          }

          if(lit.length%2 != 0) throw InterpolationError(index, 0, "hexadecimal size is not an exact number of bytes")

          lit.grouped(2).map(Integer.parseInt(_, 16).toByte).to[List].zipWithIndex.map { case (byte, idx) =>
            q"array($idx) = $byte"
          }

        case hole@Hole(_) =>
          throw InterpolationError(0, contextual.literals.head.size, "substitutions are not supported")
      }.flatten

      val size = bytes.size
      
      contextual.Implementation(q"""{
        val array = new Array[Byte]($size)
        ..$bytes

        array
      }""")
    }
  }

  implicit class HexStringContext(sc: StringContext) {
    val hex = Prefix(HexParser, sc)
  }

}
