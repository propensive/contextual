the common type of values substituted into the interpolated string

Values of different types, including those that are unknown at the time the interpolator is designed, may be
substituted into an interpolated string. But the interpolator must implement a common method to interpret these,
which requires them to be converted to a common type, `:Input`

A typical choice for `:Input` is `:Text` (or `:String`), since substitutions into a string may often be fully
represented as other strings if only they were known statically. (And the entire interpolated string might thus
be represented as a single string, and parsed as such.)

Other times, `Text` values will be inadequate, as additional details of the substituted type may affect the
interpretation state in ways that can't be easily determined from a string, and for these use cases, a different
`:Input` type may be chosen.