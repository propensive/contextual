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
