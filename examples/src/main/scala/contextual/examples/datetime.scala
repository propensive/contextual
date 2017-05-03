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
package contextual.examples

import scala.util.Try

import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

import contextual._

/*
 * parses a YYYY-MM-DDThh:mm:ss into a Java 8 LocalDateTime. Every time element in a string can be substituted by an Int, but a substitution may not span multiple elements
 */
object datetime {

  sealed trait DateTimeContext extends Context

  trait DateValue extends DateTimeContext

  case object InYear extends DateTimeContext
  case object InMonth extends DateTimeContext
  case object InDay extends DateTimeContext
  case object InHour extends DateTimeContext
  case object InMinute extends DateTimeContext
  case object InSecond extends DateTimeContext

  trait Separator extends DateTimeContext

  case object YearMonth extends Separator
  case object MonthDay extends Separator
  case object DayHour extends Separator
  case object HourMinute extends Separator
  case object MinuteSecond extends Separator
  case object AfterSecond extends Separator

  object DateTimeParser extends Interpolator {

    override type ContextType = DateTimeContext
    override type Input = String
    override type Output = LocalDateTime

    override def contextualize(interpolation: StaticInterpolation): Seq[DateTimeContext] = {
      val (contexts, finalState) = interpolation.parts.foldLeft((List[ContextType](), InYear: DateTimeContext)) {
        case ((contexts, state), lit@Literal(_, string)) =>
          val newState = parseLiteral(state, string, lit, interpolation)
          (contexts, newState)
        case ((_, s : Separator), hole@Hole(_, _)) => {
          interpolation.abort(hole, "this type cannot be substituted here")
        }
        case ((contexts, state), hole@Hole(_, _)) => {
          val newState = hole(state).getOrElse(interpolation.abort(hole,
            "this type cannot be substituted here"))
          (newState :: contexts, newState)
        }
      }
      if (finalState != AfterSecond) {
        val lit@Literal(_, _) = interpolation.parts.last
        interpolation.abort(lit, lit.string.length, "not all date/time values specified")
      }
      contexts
    }

    def evaluate(interpolation: RuntimeInterpolation): LocalDateTime = {
      val interpolatedString = interpolation.parts.zipWithIndex.map {
        case (Substitution(_, value), ix) =>
          // the the first element is a substitution it has to be formatted as a year  other wise it is two digit number
          (if (ix == 0) "%04d" else "%02d").format(value.toInt)
        case (Literal(_, value), _) => value
      }.mkString
      LocalDateTime.parse(interpolatedString, DateTimeFormatter.ISO_LOCAL_DATE_TIME)
    }
    //
    private def checkParam(param: String, max: Int, literal: Literal, pos: Int, interpolation: StaticInterpolation): Unit = {
      val valid = Try(param.toInt).map { v => if (v > max) interpolation.abort(literal, pos, s"value is to high"); true
      }.getOrElse(false)
      if (!valid) interpolation.abort(literal, pos, "Illegal value")
    }

    val SPLIT_DATE_WITH_DELIMITERS = """((?<=-)|(?=-)|(?<=T)|(?=T)|(?<=:)|(?=:))""".r
    //  SPLIT_DATE_WITH_DELIMITERS.split("2017-04-15T12:00:30") => Array(2017, -, 04, -, 15, T, 12, :, 00, :, 30)

    private def parseLiteral(state: ContextType, string: String, literal: Literal, interpolation: StaticInterpolation): ContextType = {
      val (finalState, _) = SPLIT_DATE_WITH_DELIMITERS.split(string).foldLeft((state, 0)) {
        case ((InYear, pos), year) => {
          checkParam(year, Int.MaxValue, literal, pos, interpolation)
          (YearMonth, pos + year.size)
        }
        case ((InMonth, pos), month) => {
          checkParam(month, 12, literal, pos, interpolation)
          (MonthDay, pos + month.size)
        }
        case ((InDay, pos), day) => {
          checkParam(day, 31, literal, pos, interpolation)
          (DayHour, pos + day.size)
        }
        case ((InHour, pos), hour) => {
          checkParam(hour, 24, literal, pos, interpolation)
          (HourMinute, pos + hour.size)
        }
        case ((InMinute, pos), hour) => {
          checkParam(hour, 60, literal, pos, interpolation)
          (MinuteSecond, pos + hour.size)
        }
        case ((InSecond, pos), hour) => {
          checkParam(hour, 60, literal, pos, interpolation)
          (AfterSecond, pos + hour.size)
        }
        case ((YearMonth, pos), "-") => (InMonth, pos + 1)
        case ((MonthDay, pos), "-") => (InDay, pos + 1)
        case ((DayHour, pos), "T") => (InHour, pos + 1)
        case ((HourMinute, pos), ":") => (InMinute, pos + 1)
        case ((MinuteSecond, pos), ":") => (InSecond, pos + 1)
        case ((_, pos), _) => interpolation.abort(literal, pos, "Illegal value")
      }
      finalState
    }
  }

  implicit class DateTimeParserContext(sc: StringContext) {
    val datetime = Prefix(DateTimeParser, sc)
  }

  implicit val embedInts = DateTimeParser.embed[Int](
    Case(InYear, YearMonth) { s => s.toString },
    Case(InMonth, MonthDay) { s => s.toString },
    Case(InDay, DayHour) { s => s.toString },
    Case(InHour, HourMinute) { s => s.toString },
    Case(InMinute, MinuteSecond) { s => s.toString },
    Case(InSecond, AfterSecond) { s => s.toString },
    Case(MonthDay, InDay) { s => s.toString }
  )
}