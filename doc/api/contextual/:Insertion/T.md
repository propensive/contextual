the type which may be inserted into an interpolated string

The `:Interpolator` does not need to be aware of this type. Inserted values of type `:T` will be converted using
the `.embed` method into instances of `:Input` in order for the interpolator to use them.