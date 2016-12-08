package contextual.examples

import contextual._

object binary {

  object BinParser extends Interpolator {
    type Ctx = Context.NoContext

    def implementation(contextual: Contextual): contextual.Implementation = {
      import contextual.universe.{Literal => _, _}
      
      val bytes = contextual.parts.map {
        case lit@Literal(index, string) =>
          string.zipWithIndex.map { case (ch, idx) =>
            if(ch != '0' && ch != '1') lit.abort(idx, "only '0' and '1' are valid")
          }

          if(string.length%8 != 0) lit.abort(0, "binary size is not an exact number of bytes")

          string.grouped(8).map(Integer.parseInt(_, 2).toByte).to[List].zipWithIndex.map {
            case (byte, idx) => q"array($idx) = $byte"
          }

        case hole@Hole(_, _) =>
          hole.abort("can't make substitutions")
      
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
