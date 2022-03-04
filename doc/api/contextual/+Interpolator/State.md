a representation of the state of the interpolator while interpreting an interpolated string

At compiletime, while the interpolator is interpreting an interpolated string, it will evaluate a sequence of
methods (`.parse`, `.skip` and `.complete`), each of which takes a `:State` value as a parameter and returns an
updated `:State` value. The functionality of each method can be dependent on the `:State` value passed to it,

The `:State` type must carry enough information in order to be able to construct a `:Result` instance in the
`.complete` method, but must also be sufficiently permissive to allow the `.skip` method to modify its state
without a specific substituted value being provided. In practice, however, it is usually sufficient for `.skip`
to use dummy values; since the result constructed in the `.complete` method is discarded at compiletime, it does
not matter if its value is nonsensical due to these dummy values.