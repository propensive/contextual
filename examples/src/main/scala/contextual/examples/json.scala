package contextual.examples

import contextual._

object json {

  sealed trait JsonContext extends Context
  case object InString extends JsonContext
  case object ValuePosition extends JsonContext
  case object KeyPosition extends JsonContext
  case object InvalidPosition extends JsonContext

  object JsonParser extends Interpolator {
    type Ctx = JsonContext

    def implementation(ctx: Contextual): ctx.Implementation = {
      var i = 0
      var n = 0

      def s = ctx.literals(n)
      def cur = if(i >= s.length) '\u0000' else s(i)

      def fail(expected: String) = throw InterpolationError(n, i, s"expected $expected, but found: '$cur'")
      def failPosition(expected: String) = throw InterpolationError(n, i, s"expected $expected, but found: '$cur'")
      def duplicateKey(start: Int, key: String) = throw InterpolationError(n, start, s"duplicate key: $key")

      def takeWhitespace(): Unit = while(cur.isWhitespace) next()
      def nextLiteral(): Unit = { n += 1; i = 0 }

      def consume(cs: Char*): Unit = cs foreach { c =>
        if(cur == c) next() else fail(s"'$c'")
      }

      def next() = i += 1

      def takeValue(): Unit = cur match {
        case '{' => takeObject()
        case '[' => takeArray()
        case '"' => takeString()
        case c if c.isDigit || c == '-' => takeNumber()
        case 't' => takeTrue()
        case 'f' => takeFalse()
        case 'n' => takeNull()
        case '\u0000' =>
          println(ctx.holes(n))
          if(n + 1 < ctx.literals.length) nextLiteral()
          else fail("new token or interpolated value")
        case _ => fail("new token")
      }

      def takeTrue() = consume('t', 'r', 'u', 'e')
      def takeFalse() = consume('f', 'a', 'l', 's', 'e')
      def takeNull() = consume('n', 'u', 'l', 'l')

      def takeNumber() = {
        if(cur == '-') next()

        if(cur == '0') next()
        else if(cur.isDigit) while(cur.isDigit) next()
        else fail("digit")

        if(cur == '.') {
          next()
          if(cur.isDigit) next() else fail("digit")
          while(cur.isDigit) next()
        }

        if(cur == 'e' || cur == 'E') {
          next()
          if(cur == '+' || cur == '-') next()
          if(cur.isDigit) next() else fail("digit")
          while(cur.isDigit) next()
        }
      }

      def takeObject(): Unit = {
        var seen: Set[String] = Set()
        def takeKeyValue(): Unit = {
          val start = i
          cur match {
            case '\u0000' =>
              println(ctx.holes(n))
              if(n + 1 < ctx.literals.length) {
                //if(!stringsUsed(n)) throw InterpolationError(n, i, "expected a string-like type")
                nextLiteral()
              } else fail("new token or interpolated value")
                case '"' =>
                  takeString()
                  val key = s.substring(start + 1, i - 1)
                  if(seen contains key) duplicateKey(start, key) else seen += key
          }
          takeWhitespace()
            cur match {
              case ':' =>
                consume(':')
                takeWhitespace()
                takeValue()
                takeWhitespace()
                cur match {
                  case ',' =>
                    consume(',')
                    takeWhitespace()
                    takeKeyValue()
                    case '}' => consume('}')
                  case _ => fail("',' or '}'")
                }
              case _ => fail("':'")
            }
        }

        consume('{')
          takeWhitespace()
          cur match {
            case '"' | '\u0000' =>
              println(ctx.holes(n))
              takeKeyValue()
            case '}' => consume('}')
            case _ => fail("'\"' or '}'")
          }
      }

      def takeArray(): Unit = {
        def takeElement(): Unit = {
          takeValue()
            takeWhitespace()
            cur match {
              case ',' =>
                consume(',')
                takeWhitespace()
                takeElement()
                case ']' => consume(']')
              case _ => fail("',' or ']'")
            }
        }
        consume('[')
          takeWhitespace()
          cur match {
            case ']' => consume(']')
              case _ => takeElement()
          }
      }

      def takeString(): Unit = {
        consume('"')
          while(cur != '"') cur match {
          case '\\' =>
            consume('\\')
            cur match {
              case '"' | '\\' | '/' | 'b' | 'f' | 'n' | 'r' | 't' => next()
              case 'u' =>
                consume('u')
                1 to 4 foreach { j =>
                  if(cur.isDigit || cur >= 'a' && cur <= 'f' || cur >= 'A' && cur <= 'F') next()
                  else fail("hexadecimal digit")
                }
            }
          case '\u0000' =>
            println(ctx.holes(n))
            failPosition("'\"' or more string content")
          case _ => next()
        }
        consume('"')
      }

      takeWhitespace()
      takeValue()
      takeWhitespace()
      if(i != s.length) fail("end of data")
    
      ctx.Implementation("")
    }
  }

  implicit class JsonStringContext(sc: StringContext) {
    val json = Prefix(JsonParser, sc)
  }

}
