invoked at compiletime when a given `:Substitute` instance is available instead of an `:Insertion` instance

The `.skip` method will normally be applied to the interpretation `:State` value at compiletime whenever a value
is substituted into an interpolated string. However, if a more precise `:Substitute` instance is available,
which defines an additional `String` singleton type in its signature, the `.substitute` method will be invoked
instead of `.skip`, with the reified `String` singleton value passed as an additional parameter.

This allows the `.substitute` method's implementation to behave differently for different types of insertion.

For example, an interpolator for JSON context may be inside an object definition when it sees a substitution,
i.e.:
```scala
json"""{ $sub ..."""
```

At this position, if the type of `sub` were `Text`, it would be sensible to interpret this as a string key
which should be followed by a colon (`:`), whereas if the type were `(Text, Text)` it would be sensible to
interpret it as a key/value pair, which should be followed by a comma (`,`).