package contextual

enum Context:
  case NewParam, Unquoted, DoubleQuotes, SingleQuotes

case class State(current: Context, args: List[String])

object Sh extends Verifier[State]:
  import Context.*
  type Result = List[String]
  def complete(state: State): Result = state.args
  def initial = State(NewParam, Nil)
  def parse(state: State, next: String): State =
    next.foldLeft(state) {
      case (State(NewParam, params), ' ')             => State(NewParam, params)
      case (State(Unquoted, params), ' ')             => State(NewParam, params :+ "")
      case (State(SingleQuotes, params), '\'')        => State(Unquoted, params)
      case (State(DoubleQuotes, params), '"')         => State(Unquoted, params)
      case (State(Unquoted | NewParam, params), '"')  => State(DoubleQuotes, params)
      case (State(Unquoted | NewParam, params), '\'') => State(SingleQuotes, params)
      case (State(NewParam, params), ch)              => State(Unquoted, params :+ s"$ch")
      case (State(ctx, rest :+ cur), ch)              => State(ctx, rest :+ s"$cur$ch")
    }

given Substitution[State, String] = (before, value) =>
  import Context.*
  before.current match
    case NewParam     => State(Unquoted, before.args :+ value.replaceAll("\\\"", "\\\\\""))
    case Unquoted     => State(Unquoted, before.args :+ value.replaceAll("\\\"", "\\\\\""))
    case SingleQuotes => State(SingleQuotes, before.args :+ value.replaceAll("'", """'"'"'"""))
    case DoubleQuotes => State(DoubleQuotes, before.args :+ value.replaceAll("\\\"", "\\\\\""))

extension (inline ctx: StringContext)
  inline def sh(inline parts: Tuple): Any = ${Macros.expand('Sh, 'ctx, 'parts)}

@main
def run(): Unit =
  val a = "one"
  val b = "two"
  val c = "four"

  val x = sh"this $a is $b a $c message"
  println(x)
