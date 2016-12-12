package contextual.examples

import contextual._
import scala.util.matching._

object email {

  case class EmailAddress(address: String)

  private val validEmail: Regex =
    """^[_A-Za-z0-9-\+]+(\.[_A-Za-z0-9-]+)*@[A-Za-z0-9-]+(\.[A-Za-z0-9]+)*(\.[A-Za-z]{2,})$""".r

  object EmailParser extends Interpolator {

    def implementation(ctx: Contextual[StaticPart]): ctx.Implementation = {
      
      ctx.parts.foreach {
        case lit@Literal(_, string) =>
          if(validEmail.findFirstMatchIn(string).isEmpty)
            lit.abort(0, "this is not a valid email address")
      
        case hole@Hole(_, _) =>
          hole.abort("substitutions are not supported")
      }

      ctx.runtimeEval(contexts = Nil)
    }

    def eval(contextual: Contextual[RuntimePart]): EmailAddress =
      EmailAddress(contextual.parts.mkString)

  }

  implicit class EmailStringContext(sc: StringContext) { val email = Prefix(EmailParser, sc) }
}
