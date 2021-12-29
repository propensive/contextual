parses one part of an interpolated string and updates the interpretation state accordingly

The `.parse` method is invoked first at compiletime and again at runtime, once for each static part of the
string, i.e. each contiguous section of the interpolated string between its start, substitutions and end. Its
implementation should update the `;State` value appropriately to accommodate the relevant details in the
`:String` value passed to it.