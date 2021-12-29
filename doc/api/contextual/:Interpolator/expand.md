a macro method for binding an `:Interpolator` to a `:StringContext` in an extension method

This method should always be called as the right-hand side of a transparent inline extension method to a
`:StringContext`.

This method should always be called with the following boilerplate,
```scala
extension (ctx: StringContext)
  transparent inline def i(inline parts: Any*): R =
    ${I.expand('I, 'ctx, 'parts)}
```
where the identifiers `I`, `i` and `R` should be replaced with an `:Interpolator` object, the prefix to the
interpolated string, and the return type of the interpolated string, respectively.