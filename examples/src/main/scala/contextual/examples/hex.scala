package contextual.examples

import contextual._

object hex {

  object HexParser extends Interpolator {
    type Ctx = Context.NoContext

    def implementation(contextual: Contextual[StaticToken]): contextual.Implementation = {
      import contextual.universe.{Literal => _, _}

      val bytes = contextual.parts.flatMap {
        case lit@Literal(index, string) =>
          string.zipWithIndex.foreach { case (ch, idx) =>
            if(ch < 48 || (ch > 57 && ch < 97) || ch > 102) lit.abort(idx, "bad hexadecimal")
          }

          if(string.length%2 != 0) lit.abort(0,
                "hexadecimal size is not an exact number of bytes")

          string.grouped(2).map(Integer.parseInt(_, 16).toByte).to[List].zipWithIndex.map {
            case (byte, idx) => q"array($idx) = $byte"
          }

        case hole@Hole(_, _) =>
          hole.abort("substitutions are not supported")
      
      }

      val size = bytes.size
      
      contextual.Implementation(q"""{
        val array = new Array[Byte]($size)
        ..$bytes

        array
      }""")
    }
  }

  implicit class HexStringContext(sc: StringContext) { val hex = Prefix(HexParser, sc) }
}
