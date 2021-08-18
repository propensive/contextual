<img src="/doc/images/github.png" valign="middle">

# Contextual

__Contextual__ makes it simple to write typesafe, statically-checked interpolated strings.

Contextual is a Scala library which allows you to define your own string interpolators—prefixes for
interpolated string literals like `url"https://propensive.com/"`—which determine how they should be checked
at compiletime and interpreted at runtime, writing very ordinary user code with no user-defined macros.

## Features

- user-defined string interpolators
- introduce compile-time failures on invalid values, such as `url"htpt://example.com"`
- compile-time behavior can be defined on _literal_ parts of a string
- runtime behavior can be defined on literal and interpolated parts of a string
- types of interpolated values can be context-dependent
- simple type-based parsing for interpolated values
- shorthand `Verifier` class for defining runtime and compiletime behavior together


## Getting Started

## About Interpolators

An interpolated string is any string literal prefixed with an alphanumeric string, such as
`s"Hello World"` or `date"15 April, 2016"`. Unlike ordinary string literals, interpolated strings
may also include variable substitutions: expressions written inline, prefixed with a `$` symbol,
and—if the expression is anything more complicated than an alphanumeric identifier—requiring braces
(`{`, `}`) around it. For example,
```scala
s"Hello, $name"
```
or,
```scala
s"Tomorrow will be Day ${day + 1}."
```

Anyone can write an interpolated string using an extension method on `StringContext`, and it will be
called, like an ordinary method, at runtime.

But it's also possible to write an interpolator which is called at compiletime, and which can
identify coding errors _before_ runtime.

Contextual makes it easy to write such interpolators.

## Contextual's `Interpolator` type

An interpolated string may have no substitutions, or it may include many substitutions, with a
string of zero or more characters between at the start, end, and between each adjacent pair.

So in general, any interpolated string can be represented as _n_ string literals, whose values are
known at compiletime, and _n - 1_ variables (of various types), whose values are not known until
runtime.

Contextual's `Interpolator` interface provides a set of four abstract methods—`initial`, `parse`,
`insert` and `complete`—which are invoked, in a particular order, once at compiletime, _without_ the
substituted values, and again at runtime, _with_ the substituted values.

The methods are always invoked in the same order: first `initial`; then alternately `parse` and
`insert`, some number of times, for each string literal and each substitution (respectively); and
finally `complete` to produce a result. `insert` may never be invoked if there are no substitutions,
but `parse` will always be invoked once more than `insert`.

For example, for a string with two substitutions, the invocation order would be:
```
initial -> parse -> insert -> parse -> insert -> parse -> complete
```

An object encoding the interpolator's state is returned by each of these method calls, and is passed
as input to the next—with the exception of `complete`, which should return the final value that the
interpolated string will evaluate to.

In other words, each segment of an interpolated string is read in turn, to incrementally build up
a working representation of the incomplete information in the interpolated string. And at the end,
it is converted into the return value.

The separation into `parse` and `insert` calls means that the static parts of the interpolated
string can be parsed the same way at both compiletime or runtime, while the dynamic parts can be
interpreted at runtime when they're known, and their absence handled at compiletime when they're not
known.

Here are the signatures for each method in the `Interpolator` type:
```scala
trait Interpolator[Input, State, Result]:
  def initial: State
  def parse(state: State, next: String): State
  def insert(state: State, value: Option[Input]): State
  def complete(value: State): Return
```

Three abstract types are used in their definitions: `State` represents the information passed from
one method to the next, and could be as simple as `Unit` or `String`, or could be some complex
document structure. `Input` is a type chosen to represent the types of all substitutions. `String`
would be a common choice for this, but there may be utility in using richer types, too. And `Return`
is the type that the interpolated string will ultimately evaluate to.

In addition to `parse`, `insert` and `complete` taking `State` instances as input, note that `parse`
always takes a `String`, and `insert` takes an `Option[Input]`: `Some(input)` at runtime, but `None`
at compiletime.

Any of the methods may throw an `InterpolationError` exception, with a message. At compiletime,
these will be caught, and turned into compile errors. Additionally, a range of characters may be
specified to highlight precisely where the error occurs in an interpolated string.

Any interpolator needs to choose these three types, and implement these four methods.

For example, the interpolated string,
```
url"https://example.com/$dir/images/$img"
```
could be interpreted by a Contextual interpolator, in which case it would be checked at
compiletime with the composed invocation,
```scala
complete(parse(insert(parse(insert(parse(initial, "https://example.com/"), None),
    "/images/"), None), ""))
```
and at runtime with something which is essentially this:
```scala
complete(parse(insert(parse(insert(parse(initial, "https://example.com/"), Some(dir)),
    "/images/"), Some(img)), ""))
```

## Compile Errors

Throwing exceptions provides the flexibility to raise a compilation error just by examining the
`state` value and/or the other inputs.

For example, insertions could be permitted only in appropriate positions, i.e. where the `state`
value passed to the `insert` method indicates that the insertion can be made. That is knowable at
compiletime, without even knowing the inserted value, and can be generated as a compile error by
throwing an `InterpolationError` in the implementation of `insert`.

The compile error will point at the substituted expression.

Likewise, throwing an `InterpolationError` in `parse` will generate a compile error. The optional
second parameter of `InterpolationError` allows an offset to be specified, relative to the start of
the literal part currently being parsed, and a third parameter allows its length to be specified.

For example, if we were parsing `url"https://example.ocm/$dir/images/$img"`, and wanted to highlight
the mistake in the invalid TLD `ocm`, we would throw,
```scala
InterpolationError("not a valid TLD", 15, 4)
```
during the first invocation of `parse`, and the Scala compiler would highlight `.ocm` as the error
location.

## Binding an interpolator

A small amount of boilerplate is needed to bind an `Interpolator` object, for example `Abc`, to a
prefix, i.e. the letters `abc` in the interpolated string, `abc""`:
```scala
extension (ctx: StringContext)
  transparent inline def abc(inline parts: Any*): Return =
    ${Abc.expand('Abc, 'ctx, 'parts)}
```

This boilerplate should be modified as follows:
 - the method name, `abc`, should change to the desired prefix,
 - the method's return type, `Return`, should be changed to the return type of the `complete` method, and,
 - the interpolator object, `Abc`, should be specified (twice).

In particular, the type of `parts`, `Any*`, should be left unchanged. This does not mean that `Any`
type may be substituted into an interpolated string; Contextual provides another way to constrain
the set of acceptable types for insertions.

## Insertions

Contextual uses a typeclass interface to support insertions of different types. An insertion of a
particular type, `T`, into an interpolator taking a value of type `I` requires a corresponding
given `Insertion[I, T]` instance in scope.

This means that the set of types which may be inserted into an interpolated string can be defined
ad-hoc. There is only the requirement that any inserted type, `T`, may be converted to an `I`, since
`I` is a type known to the `Interpolator` implementation.

So, if an interpolator's general `Input` type is `List[String]`, and we wanted to permit insertions
of `List[String]`, `String` and `Int`, then three given instances would be necessary:

```scala
given Insertion[List[String], String] = List(_)
given Insertion[List[String], List[String]] = identity(_)
given Insertion[List[String], Int] = int => List(int.toString)
```

## A First Interpolator

Here is a trivial interpolator which can parse, for example, `hex"a948b0${x}710bff"`, and return an
`IArray[Byte]`:
```scala
object Hex extends Interpolator[Long, String, IArray[Byte]]:
  def initial: String = ""

  def parse(state: String, next: String): String =
    if next.forall(hexChar(_)) then state+next
    else throw InterpolationError("not a valid hexadecimal character")
  
  def insert(state: String, value: Option[Long]): String =
    value match
      case None       => state+"0"
      case Some(long) => state+long.toHexString
  
  def complete(state: String): IArray[Byte] =
    IArray.from(convertStringToByteArray(state))
  
  private def hexChar(ch: Char): Boolean =
    ch.isDigit || ch >= 'a' && ch <= 'f' || ch >= 'A' && ch <: 'F'
```

Having defined this interpolator, we can bind it to the prefix, `hex` with:
```scala
extension (ctx: StringContext)
  transparent inline def hex(inline parts: Any*): IArray[Byte] =
    ${Hex.expand('Hex, 'ctx, 'parts)}
```

Note that this should be defined in a different source file from the object `Hex`.



## Status

Contextual is classified as __maturescent__. Propensive defines the following five stability levels for open-source projects:

- _embryonic_: for experimental or demonstrative purposes only, without any guarantees of longevity
- _fledgling_: of proven utility, seeking contributions, but liable to significant redesigns
- _maturescent_: major design decisions broady settled, seeking probatory adoption and refinement
- _dependable_: production-ready, subject to controlled ongoing maintenance and enhancement; tagged as version `1.0` or later
- _adamantine_: proven, reliable and production-ready, with no further breaking changes ever anticipated

## Availability

Contextual&rsquo;s source is available on GitHub, and may be built with [Fury](https://github.com/propensive/fury) by
cloning the layer `propensive/contextual`.
```
fury layer clone -i propensive/contextual
```
or imported into an existing layer with,
```
fury layer import -i propensive/contextual
```

## Contributing

Contributors to Contextual are welcome and encouraged. New contributors may like to look for issues marked
<a href="https://github.com/propensive/contextual/labels/good%20first%20issue"><img alt="label: good first issue"
src="https://img.shields.io/badge/-good%20first%20issue-67b6d0.svg" valign="middle"></a>.

We suggest that all contributors read the [Contributing Guide](/contributing.md) to make the process of
contributing to Contextual easier.

Please __do not__ contact project maintainers privately with questions, as other users cannot then benefit from
answers given in private.

## Author

Contextual was designed and developed by Jon Pretty, and commercial support and training is available from
[Propensive O&Uuml;](https://propensive.com/).



## Name

Contextual takes its name from its ability to provide context-aware substitutions in interpolated
strings.

## License

Contextual is copyright &copy; 2016-21 Jon Pretty & Propensive O&Uuml;, and is made available under the
[Apache 2.0 License](/license.md).
