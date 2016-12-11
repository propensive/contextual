package contextual.examples

import contextual._
import scala.util.matching._

object email {

  case class EmailAddress(address: String)

  private val validEmail: Regex =
    """^[_A-Za-z0-9-\+]+(\.[_A-Za-z0-9-]+)*@[A-Za-z0-9-]+(\.[A-Za-z0-9]+)*(\.[A-Za-z]{2,})$""".r

  object EmailParser extends Interpolator {
    type Ctx = Context.NoContext

    def implementation(ctx: Contextual[StaticToken]): ctx.Implementation = {
      
      ctx.parts.foreach {
        
        case lit@Literal(_, string) =>
          if(validEmail.findFirstMatchIn(string).isEmpty)
            lit.abort(0, "this is not a valid email address")
      
        case hole@Hole(_, _) =>
          hole.abort("substitutions are not supported")
      }

      ctx.Implementation(ctx.literals.head)
    }

    override def parse(string: String): EmailAddress = EmailAddress(string)

  }

  implicit class EmailStringContext(sc: StringContext) { val email = Prefix(EmailParser, sc) }
}
