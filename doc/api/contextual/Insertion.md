a typeclass interface defining how different types may be interpreted by the interpolator

For a given `:Interpolator`, it may be possible to substitute certain types into the interpolated strings it
parses. Each type will need a corresponding `:Insertion` instance which defines how that type can be transformed
into a value that the interpolator can use.