Contextual makes it easy to write compile-time checks for interpolated strings.

```scala
val projectType = "library"
val version = 1.0
val project = j"""{ $projectType: "$name $version", "version": $version }"""
```

Writing your own simple string interpolators is as easy as,

1. writing a `Interpolator` object which implements
 a. a method which examines the literal parts of the interpolated string, and the types of the substituted expressions, at compile time,
 b. a method which reads the literal parts of the interpolated string, along with the substituted values at runtime, and constructs a return value,
2. defining how different types should be embedded into the interpolated string, and
3. defining an implicit class which attaches the `Interpolator` to a string prefix.

## Defining the Interpolator

An `Interpolator` defines how an interpolated string should be understood at
both compile-time, and runtime. These are similar operations, but differ in
what is known about the expressions being interpolated amongst the fixed parts of
the interpolated string: at runtime we have the evaluated values being
substituted, whereas at compile-time the values are unknown, but we instead
have access to certain meta-information about the substitutions.

### Typed holes

### Compile-time checking

### Runtime 

## Attaching the Interpolator to a String prefix

The simplest definition required to specify the `Interpolator` that should be
applied to a string prefix of our choosing looks like this:

```scala
implicit class ExampleStringContext(ctx: StringContext) {
  val example = Prefix.simple(MyParser, ctx)
}
```

where the name of the `val` is the identifier that will appear immediately
before the `"` at the use site. So with this implicit class in scope, we can
then use interpolated strings like

```scala
example"... ${expr} ..."
```

and have their content checked at compile time with `MyParser`, *provided the
use site is in a later compiler run*. This is because it is not possible to
define compile-time behavior *and* use it in the same compilation run.

## Advanced usage

The examples above are 
