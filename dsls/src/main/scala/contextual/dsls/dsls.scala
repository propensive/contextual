package dsls

import contextual._

object `package` {
 
  implicit val embedBoolean = SimpleParser.embed[Boolean](
    transition(Bare, InString)(_ => "!!!"),
    transition(InString, Bare)(_ => "!!!")
  )

  implicit val embedSymbol = SimpleParser.embed[Symbol](
    transition(Bare, Bare)("#"+_.name),
    transition(InString, InString)("@"+_.name)
  )

  implicit val embedString = SimpleParser.embed[String](
    transition(Bare, Bare) { s => "'"+s.toString+"'" },
    transition(InString, InString)(_.toString)
  )
  
  implicit class TestStringContext(stringContext: StringContext) {
    val simple = Prefix.withContext[SimpleContext](SimpleParser, stringContext)
  }
}

case class Simple(str: String)

sealed trait SimpleContext extends Context
object InString extends SimpleContext
object Bare extends SimpleContext

object SimpleParser extends Parser {
  type Inputs = String
  type Result = Simple
  type Ctx = SimpleContext
  
  def construct(tokens: Seq[RuntimeParseToken[String]]): Simple = Simple(tokens.mkString)

  def initialState = Bare

  def next = {
    case (InString, '"') => Bare
    case (Bare    , '"') => InString
    case (state, _  ) => state
  }


}


