/* Contextual, version 1.1.0. Copyright 2018 Jon Pretty, Propensive Ltd.
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
package contextual.data

import contextual._
import scala.util.matching._

object email {
  case class EmailAddress(address: String)

  private val validEmail: Regex =
    """^[_A-Za-z0-9-\+]+(\.[_A-Za-z0-9-]+)*@[A-Za-z0-9-]+(\.[A-Za-z0-9]+)*(\.[A-Za-z]{2,})$""".r

  object EmailParser extends Verifier[EmailAddress] {
    def check(string: String) =
      if(validEmail.findFirstMatchIn(string).isEmpty) Left((0, "could not parse email address"))
      else Right(EmailAddress(string))
  }

  implicit class EmailStringContext(sc: StringContext) { val email = Prefix(EmailParser, sc) }
}
