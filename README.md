# clens

Yet another validation library... but this one associates validations with lens traversals (a la Traversy (github.com/ctford/traversy) )

## FAQ

### Why is this one any better than the other ones?

Maybe it's not, but I thought the idea of applying a validation to a lens, which potential has multiple foci, would be quite neat.  Also I want the output of the validation to be simpler, so it's easier for the user to make decisions about how to render the messages.  The approach of returning errors in the same structure as the model actually makes things more fiddly and less flexible (IMHO).

### Can you show me an example of how to use it?

Not yet.

### How can I wire in messages for my errors?

I'll tell you when I've written it up.  I'll try and do that soon.

## License

Copyright © 2014 John Cowie

Distributed under the Eclipse Public License, the same as Clojure.
