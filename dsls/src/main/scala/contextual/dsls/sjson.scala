package dsls.sjson

import contextual._

case class Sjson(str: String)

sealed trait SjsonContext extends Context
object Value extends SjsonContext
object ObjectStart extends SjsonContext
object InLabel extends SjsonContext
object AfterLabel extends SjsonContext
object InString extends SjsonContext
object AfterValue extends SjsonContext

object SjsonParser extends Parser {
  type Inputs = String
  type Ctx = SjsonContext
  
  def construct(tokens: Seq[RuntimeParseToken[String]]): Sjson = Sjson(tokens.mkString)

  def initialState = Value

  def next = {
    case (s, ' ' | '\r' | '\t' | '\n') => s
    case (Value,       '{')            => ObjectStart
    case (ObjectStart, '"')            => InLabel
    case (InLabel,     '"')            => AfterLabel
    case (AfterLabel,  ':')            => Value
    case (Value,       '"')            => InString
    case (InString,    '"')            => AfterValue
    case (AfterValue,  ',')            => ObjectStart
    case (AfterValue,  '}')            => AfterValue
    case (ObjectStart, '}')            => AfterValue
    case (s@(InLabel | InString), _)   => s
    case (state,       ch )            => throw ParseError(s"parsing failed: found character `$ch' in context `$state'")
  }
  
  override def endFailure(ctx: Ctx) = if(ctx != AfterValue) Some("content is incomplete") else None
}

object `package` {
  implicit val embedString = SjsonParser.embed[String](
    transition(Value, AfterValue) { s => '"'+s+'"' },
    transition(InString, InString)(_.toString),
    transition(InLabel, InLabel)(_.toString),
    transition(ObjectStart, AfterLabel) { s => '"'+s+'"' }
  )
  
  implicit class TestStringContext(stringContext: StringContext) {
    val sjson = Prefix.withContext[SjsonContext](SjsonParser, stringContext)
  }
}

