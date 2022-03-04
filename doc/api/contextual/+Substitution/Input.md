the resultant input type that this `:Substitution` produces for insertion into an interpolated string

In general, the `:Interpolator` instance will not be aware of all the types that may be inserted into an
interpolated string, so `:Insertion` instances must be provided to convert downstream types into values that the
interpolator is able to use. These have the type `:Input`, which forms part of the `:Interpolator`'s signature.