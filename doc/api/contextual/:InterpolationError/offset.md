the offset of the error from the start of the current part of the interpolated string

This field is only relevant for `:InterpolationError`s thrown in the `.parse` method. It indicates the offset
of the error from the start of the current part of the interpolated string (i.e. from the start of the string,
if the error occurs in the first part, or from the end of the previous substitution, if the error occurs in a
later part).