/* Contextual, version 1.0.0. Copyright 2016 Jon Pretty, Propensive Ltd.
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
import javax.xml.xpath._
import scala.util._

object xpath {
  object XpathParser extends Interpolator {
    case class ContextType() extends Context
    type Output = XPathExpression

    def contextualize(interpolation: StaticInterpolation): Seq[ContextType] = {
      interpolation.parts match {
        case (lit@Literal(index, string)) :: Nil =>
          val xpathFactory = XPathFactory.newInstance()
          val xpath = xpathFactory.newXPath()
          Try(xpath.compile(string)) match {
            case Success(_) => Nil
            case Failure(e) =>
              interpolation.abort(lit, 0, s"xpath: could not parse expression: ${e.getCause.getMessage}")
          }
        case lit :: (hole@Hole(i, v)) :: _ =>
          interpolation.abort(Hole(i, v), "xpath: substitutions are not supported")
      }
    }

    def evaluate(interpolation: RuntimeInterpolation): XPathExpression = {
      val xpathFactory = XPathFactory.newInstance()
      val xpath = xpathFactory.newXPath()
      xpath.compile(interpolation.parts.mkString)
    }
  }

  implicit class XpathStringContext(sc: StringContext) { val xpath = Prefix(XpathParser, sc) }
}
