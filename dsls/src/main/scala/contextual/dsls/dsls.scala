package dsls

import contextual._

object `package` {
  
  implicit val embedSymbol = SimpleParser.embed[Symbol](
    into(Bare)("#"+_.name),
    into(InString)("@"+_.name)
  )

  implicit val embedString = SimpleParser.embed[String](
    into(Bare) { s => "'"+s.toString+"'" },
    into(InString)(_.toString)
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

  def stateMachine = {
    case (InString, '"') => Bare
    case (Bare    , '"') => InString
    case (state, _  ) => state
  }


}



