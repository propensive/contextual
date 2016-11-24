package dsls

import contextual._

sealed trait SimpleContext extends Context
object InString extends SimpleContext
object Bare extends SimpleContext

object `package` {
  
  implicit val embedSymbol = SimpleParser.embed[Symbol](
    into(Bare)("#"+_.name)
    //into(InString)("@"+_.name)
  )

  implicit val embedString = SimpleParser.embed[String](
    into(Bare) { s => "'"+s.toString+"'" },
    into(InString)(_.toString)
  )
  
  implicit class TestStringContext(stringContext: StringContext) {
    val simple = Prefix.withContext[SimpleContext](SimpleParser, stringContext)
  }
}

object SimpleParser extends Parser {
  type Inputs = String
  type Result = String
  type Ctx = SimpleContext
  
  def verify(tokens: Seq[CompileParseToken[SimpleContext]]): Seq[InterpolationError] = {
    Nil
  }
  
  def construct(tokens: Seq[RuntimeParseToken[String]]): String = {
    tokens.map {
      case Literal(_, lit) => lit
      case Variable(v) => v
    }.mkString
  }

  def contexts(tokens: Seq[Literal]): Seq[Ctx] = tokens.foldLeft((Nil: List[SimpleContext], Bare: SimpleContext)) {
    case ((holes, state), Literal(_, lit)) =>
      val holeState = lit.to[Seq].foldLeft(state) {
        case (InString, '"') => Bare
        case (Bare    , '"') => InString
        case (state, _  ) => state
      }
      (holeState :: holes, holeState)
  }._1.tail.reverse
}



