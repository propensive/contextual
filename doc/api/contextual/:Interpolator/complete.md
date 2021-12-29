checks the `:State` value and returns the final `:Result` instance

This method is executed once during compilation of an interpolated string, and again at runtime to construct the
value from the interpolated string.

It is passed the final `:State` value that results from the previous steps of interpreting the interpolated
string. Its implementation should check that the `:State` value represents a valid state from which a `:Result`
value may be constructed, and constructs the result.

At compiletime, this result value is discarded, while at runtime, the value it produces will be returned from
evaluating the interpolated string.