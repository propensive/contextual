package contextual.examples

import contextual._

object binary {

  object BinParser extends Interpolator {
    type Ctx = Context.NoContext

    def implementation(ctx: Contextual[StaticToken]): ctx.Implementation = {
      import ctx.universe.{Literal => _, _}
      
      val bytes = ctx.parts.flatMap {
        case lit@Literal(index, string) =>

          // Fail on any uses of non-binary characters
          string.zipWithIndex.map { case (ch, idx) =>
            if(ch != '0' && ch != '1') lit.abort(idx, "only '0' and '1' are valid")
          }

          // Fail if it's the wrong length
          if(string.length%8 != 0) lit.abort(0, "binary size is not an exact number of bytes")

          // Convert the string to a sequence of assignment operations
          string.grouped(8).map(Integer.parseInt(_, 2).toByte).to[List].zipWithIndex.map {
            case (byte, idx) => q"array($idx) = $byte"
          }

        case hole@Hole(_, _) =>

          // We don't support substitutions (yet)
          hole.abort("can't make substitutions")
      
      }

      val size = bytes.size
     
      // The code that will be evaluated at runtime
      ctx.Implementation(q"""{
        val array = new Array[Byte]($size)
        ..$bytes

        array
      }""")
    }
  }

  implicit class BinaryStringContext(sc: StringContext) { val bin = Prefix(BinParser, sc) }
}
