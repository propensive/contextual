the length of the error from the `.offset` position within the current part of the interpolated string

This field is only relevant for `:InterpolationError`s thrown in the `.parse` method. It will determine the
length of the region of the interpolated string in the source code that is highlighted as erroneous.