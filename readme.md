[<img alt="GitHub Workflow" src="https://img.shields.io/github/workflow/status/propensive/contextual/Build/master?style=for-the-badge" height="24">](https://github.com/propensive/contextual/actions)
[<img src="https://img.shields.io/badge/gitter-discuss-f00762?style=for-the-badge" height="24">](https://gitter.im/propensive/contextual)
[<img src="https://img.shields.io/discord/633198088311537684?color=8899f7&label=DISCORD&style=for-the-badge" height="24">](https://discord.gg/CHCPjERybv)
[<img src="https://img.shields.io/matrix/propensive.contextual:matrix.org?label=MATRIX&color=0dbd8b&style=for-the-badge" height="24">](https://app.element.io/#/room/#propensive.contextual:matrix.org)
[<img src="https://img.shields.io/twitter/follow/propensive?color=%2300acee&label=TWITTER&style=for-the-badge" height="24">](https://twitter.com/propensive)
[<img src="https://img.shields.io/maven-central/v/com.propensive/contextual_2.12?color=2465cd&style=for-the-badge" height="24">](https://search.maven.org/artifact/com.propensive/contextual_2.12)
[<img src="https://img.shields.io/badge/vent-propensive%2Fcontextual-f05662?style=for-the-badge" height="24">](https://vent.dev)

<img src="/doc/images/github.png" valign="middle">

# Contextual

__Contextual__ makes it simple to write typesafe, statically-checked interpolated strings.  Contextual is a Scala library which allows you to define your own string interpolators—prefixes for interpolated string literals like `url"https://propensive.com/"`—which determine how they should be checked at compiletime and interpreted at runtime, writing very ordinary user code with no user-defined macros.

## Features

- user-defined string interpolators
- introduce compile-time failures on invalid values, such as `url"htpt://example.com"`
- compile-time behavior can be defined on _literal_ parts of a string
- runtime behavior can be defined on literal and interpolated parts of a string
- types of interpolated values can be context-dependent
- simple type-based parsing for interpolated values
- shorthand `Verifier` class for defining runtime and compiletime behavior together


## Getting Started

## A simple example

We can define a simple interpolator for URLs like this:
```
import contextual._

case class Url(url: String)

object UrlInterpolator extends Interpolator {
  
  type Out = Url

  def contextualize(interpolation: StaticInterpolation) = {
    val lit@Literal(_, urlString) = interpolation.parts.head
    if(!checkValidUrl(urlString))
      interpolation.abort(lit, 0, "not a valid URL")

    Nil
  }

  def evaluate(interpolation: RuntimeInterpolation): Url =
    Url(interpolation.literals.head)
}

implicit class UrlStringContext(sc: StringContext) {
  val url = Prefix(UrlInterpolator, sc)
```

and at the use site, it makes this possible:

```
scala> url"http://www.propensive.com/"
res: Url = Url(http://www.propensive.com/)

scala> url"foobar"
<console>: error: not a valid URL
       url"foobar"
           ^
```
## How it works
          
Scala offers the facility to implement custom string interpolators, and while
these may be implemented with a simple method definition, the compiler imposes
no restrictions on using macros. This allows the constant parts of an
interpolated string to be inspected at compile-time, along with the types of
the expressions substituted into it.

Note: Scala also allows the definition of string interpolators which make use
of generics (i.e. accepting type parameters). Unfortunatly it's not possible to
define a generic string interpolator using Contextual, and the macro would need
to be defined manually in order to achieve that.

Contextual provides a generalized macro for interpolating strings (with a
prefix of your choice) that calls into a simple API for defining the
compile-time checks and runtime implementation of the interpolated string.

This can be done without *you* writing any macro code.

## Concepts

### `Interpolator`s

An `Interpolator` defines how an interpolated string should be understood, both
at compile-time, and runtime. Often, these are similar operations, as both will
work on the same sequence of constant literal parts to the interpolated string,
but will differ in how much is known about the holes; that is, the expressions
being interpolated amongst the constant parts of the interpolated string. At
runtime we have the evaluated substituted values available, whereas at
compile-time the values are unknown, though we do have access to certain
meta-information about the substitutions, which allows some useful constraints
to be placed on substitutions.

### The `contextualize` method

`Interpolator`s have one abstract method which needs implementing to provide any compile-time checking or parsing functionality:
```
def contextualize(interpolation: StaticInterpolation): Seq[Context]
```

The `contextualize` method requires an implementation which inspects the
literal parts and holes of the interpolated string. These are provided by the
`parts` member of the `interpolation` parameter. `interpolation` is an instance
of `StaticInterpolation`, and also provides methods for reporting errors and
warnings at compile-time.

### The `evaluate` method

The runtime implementation of the interpolator would typically be provided by
defining an implementation of `evaluate`. This method is not part of the
subtyping API, so does not have to conform to an exact shape; it will be called
with a single `Contextual[RuntimePart]` parameter whenever an interpolator is
expanded, but may take type parameters or implicit parameters (as long as these
can be inferred), and may return a value of any type.

### The `StaticInterpolation` and `RuntimeInterpolation` types

We represent the information about the interpolated string known at
compile-time and runtime with the `StaticInterpolation` and
`RuntimeInterpolation` types, respectively. These provide access to the
constant literal parts of the interpolated string, metadata about the holes and
the means to report errors and warnings at compile-time; and at runtime, the
values substituted into the interpolated string, converted into a common
"input" type. Normally `String` would be chosen for the input type, but it's
not required.

Perhaps the most useful method of the interpolation types is the `parts` method
which gives the sequence of parts representing each section of the interpolated
string: alternating `Literal` values with either `Hole`s (at compile-time) or
`Substitution`s at runtime.

### Contexts

When checking an interpolated string containing some DSL, holes may appear in
different contexts within the string. For example, in a XML interpolated
string, a substitution may be inside a pair of (matching) tags, or as a
parameter to an attribute, for example, `xml"<tag attribute=$att>$content</tag>"`.
In order for the XML to be valid, the string
`att` must be delimited by quotes, whereas the string `code` does not require
the quotes; both will require escaping. This difference is modeled with the
concept of `Context`s: user-defined objects which represent the position within
a parsed interpolated string where a hole is, and which may be used to
distinguish between alternative ways of making a substitution.

This idea is fundamental to any advanced implementation of the `contextualize`
method: besides performing compile-time checks, the method should return a
sequence of `Context`s corresponding to each hole in the interpolated string.
In the XML example above, this might be the sequence, `Seq(Attribute, Inline)`,
referencing objects (defined at the same time as the `Interpolator`) which
provide context to the substitutions of the `att` and `content` values.

### Generalizing Substitutions

A typical interpolator will allow only certain types to be used as
substitutions. This may include a few common types like `Int`s, `Boolean`s and
`String`s, but Contextual supports ad-hoc extension with typeclasses, making it
possible for user-defined types to be supported as substitutions, too. However,
in order for the interpolator to understand how to work with arbitrary types,
which may not yet have been defined, the interpolator must agree on a common
interface for all substitutions. This is the `Input` type, defined on the
`Interpolator`, and every typeclass instance representing how a particular type
should be embedded in an interpolated string must define how that type is
converted to the common `Input` type.

Often, it is easy and sufficient to use `String` as the `Input` type.

### Embedding types

Different types are embedded by defining an implicit `Embedder` typeclass
instance, which specifies with a number of `Case` instances how the type should
be converted to the interpolator's `Input` type. For example, given a
hypothetical XML interpolator, `Symbol`s could be embedded using,
```
implicit val embedSymbolsInXml = XmlInterpolator.embed[Symbol](
  Case(AttributeKey, AfterAtt)(_.name),
  Case(AttributeVal, InTag) { s => '"'+s.name+'"' },
  Case(Content, Content)(_.name)
)
```
where the conversion to `String`s are defined for three different contexts,
`AttributeKey`, `AttributeVal`, and `Content`. Whilst in the first two cases,
the context changes, in the final case, the context is unchanged by making the
substitution.

### Attaching the interpolator to a prefix

Finally, in order to make a new string interpolator available through a prefix
on a string, the Scala compiler needs to be able to "see" that prefix on
Scala's built-in `StringContext` object. This is very easily done by specifying
a new `Prefix` value with the desired name on an implicit class that wraps
`StringContext`, as in the example above,
```
implicit class UrlStringContext(sc: StringContext) {
  val url = Prefix(UrlInterpolator, sc)
}
```

The `Prefix` constructor takes only two parameters: the `Interpolator` object
(and it must be an *object*, otherwise the macro will not be able to invoke it
at compile time), and the `StringContext` instance that we are extending.


## Status

Contextual is classified as __maturescent__. Propensive defines the following five stability levels for open-source projects:

- _embryonic_: for experimental or demonstrative purposes only, without guarantee of longevity
- _fledgling_: of proven utility, seeking contributions, but liable to significant redesigns
- _maturescent_: major design decisions broady settled, seeking probatory adoption and refinement of designs
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
A binary is available on Maven Central as `com.propensive:contextual_<scala-version>:1.3.0`. This may be added
to an [sbt](https://www.scala-sbt.org/) build with:
```
libraryDependencies += "com.propensive" %% "contextual" % "1.3.0"
```

## Contributing

Contributors to Contextual are welcome and encouraged. New contributors may like to look for issues marked
<a href="https://github.com/propensive/contextual/labels/good%20first%20issue"><img alt="label: good first issue"
src="https://img.shields.io/badge/-good%20first%20issue-67b6d0.svg" valign="middle"></a>.

We suggest that all contributors read the [Contributing Guide](/contributing.md) to make the process of
contributing to Contextual easier.

Please __do not__ contact project maintainers privately with questions, as other users cannot then benefit from
the answers.

## Author

Contextual was designed and developed by [Jon Pretty](https://twitter.com/propensive), and commercial support and
training is available from [Propensive O&Uuml;](https://propensive.com/).



## License

Contextual is copyright &copy; 2016-20 Jon Pretty & Propensive O&Uuml;, and is made available under the
[Apache 2.0 License](/license.md).
