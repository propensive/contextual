package contextual.examples

import contextual._
import scala.util.matching._

object email {

  case class EmailAddress(address: String)

  object EmailParser extends Interpolator {
    type Ctx = Context.NoContext

    private val validEmail: Regex = """^[_A-Za-z0-9-\+]+(\.[_A-Za-z0-9-]+)*@[A-Za-z0-9-]+(\.[A-Za-z0-9]+)*(\.[A-Za-z]{2,})$""".r

    def implementation(ctx: Contextual): ctx.Implementation = {
      import ctx.universe._

      if(ctx.literals.size > 1)
        throw InterpolationError(0, ctx.literals.head.size, "substitutions are not supported")
      
      if(validEmail.findFirstMatchIn(ctx.literals.head).isEmpty)
        throw InterpolationError(0, 0, "this is not a valid email address")

      ctx.Implementation(ctx.literals.head)
    }

    override def parse(string: String): EmailAddress = EmailAddress(string)

  }

  implicit class EmailStringContext(sc: StringContext) {
    val regex = Prefix(EmailParser, sc)
  }

}
