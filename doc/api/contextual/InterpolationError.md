a failure that happens while interpreting an interpolated string

User code should throw this exception during `.parse`, `.substitute`, `.insert` or `.complete`. Since these
methods are called during macro expansion, it will be caught at compiletime and presented as a compile error.