/*

    Contextual, version 3.0.0. Copyright 2016-20 Jon Pretty, Propensive OÜ.

    The primary distribution site is: https://propensive.com/

    Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in
    compliance with the License. You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

    Unless required by applicable law or agreed to in writing, software distributed under the License is
    distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
    See the License for the specific language governing permissions and limitations under the License.

*/
package contextual.tests

import scala.util.{Failure, Success, Try}

import contextual.Interpolator
import contextual.tests.InterpolatorTest.{BadMatch, BadParse, Successful, TestResult}

trait InterpolatorTest[I <: Interpolator] {
  type Example = (() => I#Output, I#Output)
  def testName: String = this.getClass.getSimpleName
  def interpolator: I
  def examples: List[Example]
  def eq(x: I#Output, y: I#Output): Boolean = x == y

  def testExamples: (List[TestResult], List[TestResult]) = {
    examples.map { case (thunk, expected) =>
      Try(thunk()) match {
        case Success(result) if eq(result, expected) => Successful
        case Success(result) => BadMatch(expected, result)
        case Failure(_) => BadParse(expected)
      }
    }.partition {
      case Successful => true
      case _ => false
    }
  }

  def runTests(): Unit = {
    println(s"Running tests for $testName")
    val (succ, fail) = testExamples
    fail.foreach { f =>
      println(s"Test Failed: $f")
    }
    println(s"Results: ${succ.size} successes and ${fail.size} failures")
  }
}

object InterpolatorTest {
  sealed trait TestResult
  case object Successful extends TestResult
  case class BadParse[T](expected: T) extends TestResult
  case class BadMatch[T](expected: T, result: T) extends TestResult
}
