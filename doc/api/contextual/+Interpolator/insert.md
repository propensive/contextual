inserts a known value into the interpolator state at runtime

Complementary to the `.skip` method, which modifies the interpolation state at compiletime, when the value of a
substitution is not yet known, the `.insert` method should be implemented to modify the `:State` value to
include it, so that interpretation of the interpolated string can continue by calling `.parse` on the next part.