the singleton type which may be used to disambiguate between different substitutions at compiletime

When a value of some type is inserted into an interpolated string, the `.skip` method will be invoked at
compiletime to modify the interpretation state accordingly. But while the `:Input` type may be used to encode
details of the inserted value and its type at runtime, the value is not available at compiletime, so
substitutions of different types cannot be disambiguated, and using the `.skip` method, the `:State` value must
change in the same way regardless of the type of the substitution.

The `:S` type allows a singleton `:String` type to be included in a contextual `:Substitution` instance, in
order to disambiguate between differently-typed substitutions at compiletime. This singleton `:String` will be
reified to a `:String` instance, which will be passed to the `.substitute` method, which will be called in place
of `.skip`. Implementations of `.substitute` may have different behavior depending on this `:String` value.