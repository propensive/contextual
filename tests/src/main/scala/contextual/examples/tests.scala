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
package contextual.tests

object Testing {
  
  def main(args: Array[String]): Unit = {

    import contextual.examples._
    import shell._
    import email._
    import datetime._

    val res = email"aaa@ddd.com"
    val res2 = sh"""a b c d ${"e"} ${"f"}"""

    println(res.address)
    println(res2.args)

    val date = datetime"2017-04-15T12:00:00"

    val month = 4
    val date2 = datetime"2017-$month-15T12:00:00"

    println(date2)
// The following expressions will produce compile errors
//    val wrongdate1 = datetime"2017-04-15T12:00"
//    val wrongdate2 = datetime"2017-04-15T12:00:00"
//    val wrongdate3 = datetime"2017-13-15T12:00:00"

  }
}

