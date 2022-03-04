modifies the interpolation state at compiletime to interpret an insertion whose value is not yet known

An interpolated string being interpreted at compiletime may include several substituted expressions whose values
will not be known until runtime. Their presence, even without knowing their values, will usually affect the
compilation state in some way, and the `.skip` method should modify the `:State` value appropriately.

A common implementation for a `.skip` method is to delegate to the `.parse` method, passing in a dummy value as
a `:String`.

User code may throw an `:InterpolationError` in this method if an insertion is made in an invalid state.