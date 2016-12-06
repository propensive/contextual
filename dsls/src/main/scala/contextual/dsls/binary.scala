package contextual.dsls

import contextual._

object binary {

  object BinParser extends Interpolator {
    type Ctx = Context.NoContext

    def implementation(contextual: Contextual): contextual.Implementation = {
      import contextual.universe.{Literal => _, _}

      val bytes = contextual.parts.map {
        case Literal(index, lit) =>
          lit.zipWithIndex.map { case (ch, idx) =>
            if(ch != '0' && ch != '1') throw InterpolationError(index, idx, "bad binary")
          }

          if(lit.length%8 != 0) throw InterpolationError(index, 0, "binary size is not an exact number of bytes")

          lit.grouped(8).map(Integer.parseInt(_, 2).toByte).to[List].zipWithIndex.map { case (byte, idx) =>
            q"array($idx) = $byte"
          }

        case hole@Hole(_) =>
          throw InterpolationError(0, contextual.literals.head.size, "can't substitute")
      }.flatten

      val size = bytes.size
      
      contextual.Implementation(q"""{
        val array = new Array[Byte]($size)
        ..$bytes

        array
      }""")
    }
  }

  implicit class BinaryStringContext(sc: StringContext) {
    val bin = Prefix(BinParser, sc)
  }

}
