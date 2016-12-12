/* Contextual, version 0.12. Copyright 2016 Jon Pretty, Propensive Ltd.
 *
 * The primary distribution site is: http://co.ntextu.al/
 * 
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software distributed under the
 * License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied. See the License for the specific language governing permissions
 * and limitations under the License.
 */
package contextual.examples

import contextual._
import scala.util.matching._

object email {

  case class EmailAddress(address: String)

  private val validEmail: Regex =
    """^[_A-Za-z0-9-\+]+(\.[_A-Za-z0-9-]+)*@[A-Za-z0-9-]+(\.[A-Za-z0-9]+)*(\.[A-Za-z]{2,})$""".r

  object EmailParser extends Interpolator {

    def implement(ctx: Contextual[StaticPart]): ctx.Implementation = {
      
      ctx.parts.foreach {
        case lit@Literal(_, string) =>
          if(validEmail.findFirstMatchIn(string).isEmpty)
            lit.abort(0, "this is not a valid email address")
      
        case hole@Hole(_, _) =>
          hole.abort("substitutions are not supported")
      }

      ctx.doEvaluation(contexts = Nil)
    }

    def evaluate(contextual: Contextual[RuntimePart]): EmailAddress =
      EmailAddress(contextual.parts.mkString)

  }

  implicit class EmailStringContext(sc: StringContext) { val email = Prefix(EmailParser, sc) }
}
