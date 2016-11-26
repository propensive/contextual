package contextual.dsls

import contextual._
import scala.reflect.macros.whitebox

object binary {

  case class Binary(data: Array[Byte]) extends AnyVal

  object BinParser extends Interpolator {
    type Ctx = Context.NoContext
    type Inputs = Array[Byte]

    def compile[P: c.WeakTypeTag](c: whitebox.Context)(literals: Seq[String], parameters: Seq[c.Tree], holes: Seq[Hole[(Ctx, Ctx)]]): c.Tree = {
      import c.universe._

      if(literals.size > 1) throw InterpolationError(0, literals.head.size, "can't substitute")

      literals.zipWithIndex.foreach { case (lit, idx) =>
        lit.zipWithIndex.foreach { case (ch, idx2) =>
          if(!List('1', '0').contains(ch)) throw InterpolationError(idx, idx2, "bad binary")
        }
      }

      if(literals.head.size%8 != 0) throw InterpolationError(0, 0, "binary size is not an exact number of bytes")

      val bytes = literals.head.grouped(8).map(Integer.parseInt(_, 2).toByte).to[List].zipWithIndex.map { case (b, i) =>
        q"a($i) = $b"
      }
      val size = bytes.size
      q"""{
        val a = new Array[Byte]($size)
        ..$bytes

        a
      }"""
    }

  }

  implicit class BinaryStringContext(sc: StringContext) {
    val bin = Prefix.simple(BinParser, sc)
  }

}
