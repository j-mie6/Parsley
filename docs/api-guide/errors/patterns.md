{%
laika.versioned = true
laika.title = "`patterns`"
parsley.tabname = "Error Message Patterns (parsley.errors.patterns)"
laika.site.metadata.description = "This page describes how to generate informative errors."
%}

# Error Message Patterns
The combinators discussed in
[`parsley.errors.combinator`][Error Message Combinators] are useful
for changing the contents of an error message, but they do not account for any
contextual obligations a more carefully crafted error might have. Ensuring
that error messages are valid in the context in which they occurred is the job
of the *Verified Errors* and *Preventative Errors* parsing design patterns.
These are both encoded in `parsley` as combinators within `parsley.errors.patterns`.

@:callout(info)
*The Scaladoc for this page can be found at [`parsley.errors.patterns`](@:api(parsley.errors.patterns$)).*
@:@

## *Verified Errors*
When a hand-tailored reason for a syntax error relies on some future input
being present, a *verified* error can be used to ensure these obligations are met. These errors, called *error widgets*, will have the following properties:

* have type `Parsley[Nothing]`, ensuring that it is guaranteed to fail
* produce errors that are rooted at the start of the widget
* generate a caret as wide as the problematic input
* if the problematic parser succeeds having consumed input, the widget consumes
  input
* if the problematic parser fails, it does not consume input or influence the
  error message of the surrounding parser in any way

The "problematic parser" referred to above is what will be parsed to try and
verify that the requirements for the error messages are met.
Any widget that meets these five properties is likely already a *verified error*,
however, they can be satisfied by the combinators enabled by importing `parsley.errors.patterns.VerifiedErrors` -- this is the focus of this page.

Each of the `verifiedX` combinators try a parse something, and if it succeeds
will generate an error based on the result. If it could not be parsed, they will generate an empty error. Each different combinator will generate an error with
different content. As examples (in isolation of surrounding content):

```scala mdoc:invisible
import parsley.token.Lexer
import parsley.token.Basic
import parsley.token.descriptions.{LexicalDesc, NameDesc}

val lexer = new Lexer(LexicalDesc.plain.copy(
    nameDesc = NameDesc.plain.copy(
        identifierStart = Basic(_.isLetter),
        identifierLetter = Basic(_.isLetterOrDigit),
    ),
))
```

```scala mdoc:to-string
import parsley.errors.patterns.VerifiedErrors
import parsley.character.char

// assume that a `lexer` is available
val float = lexer.nonlexeme.floating.decimal
val _noFloat =
    float.verifiedExplain("floating-point values may not be used as array indices")

_noFloat.parse("hello")
_noFloat.parse("3.142")

val int = lexer.nonlexeme.unsigned.decimal
val _noPlus = (char('+') ~> int).verifiedFail { n =>
    Seq(s"the number $n may not be preceeded by \"+\"")
}
_noPlus.parse("+10")
```

Occasionally, there may need to be more fine-grained control over the errors.
In these cases, the most generic version of the combinator is `verifiedWith`,
which takes an `ErrorGen` object. For instance, perhaps the focus of the last
error above should only be the `+`. In which case, the caret must be adjusted by
an `ErrorGen` object -- this can either be `VanillaGen` or `SpecializedGen`.

```scala mdoc:to-string:nest
import parsley.errors.SpecializedGen
val _noPlus = (char('+') ~> int).verifiedWith {
    new SpecializedGen[BigInt] {
        def messages(n: BigInt): Seq[String] =
            Seq("a number may not be preceeded by \"+\"")
        override def adjustWidth(x: BigInt, width: Int) = 1
    }
}
_noPlus.parse("+10")
```

In the above, the `width` parameter would have been the original determined
size, which would have been `3` based on the other error message. A `VanillaGen`
would also have the ability to change the unexpected message:

```scala mdoc:to-string:nest
import parsley.errors.VanillaGen
val _noFloat = float.verifiedWith {
    new VanillaGen[BigDecimal] {
        override def reason(x: BigDecimal): Option[String] =
            Some("floats may not be array indices")
        override def unexpected(x: BigDecimal): VanillaGen.UnexpectedItem = {
            VanillaGen.NamedItem("floating-point number")
        }
    }
}

_noFloat.parse("3.142")
```

## *Preventative Errors*
The *verified error* pattern can only be applied as part of a chain of
alternatives. However, if the alternatives are spread far apart, this can make
the pattern cumbersome to use. Instead, a *preventative error* seeks to rule out
bad inputs not as a last resort, but eagerly as soon as it might become possible to do so. Widgets following this pattern have the following properties:

* succeed if the problematic parser does not succeed, returning `Unit`
* produce errors that are unconditionally rooted at the start of the widget
* generate a caret as wide as the problematic input
* if the problematic parser succeeds having consumed input, this widget must
  consume input
* if the problematic parser fails, it should not consume input nor influence
  any errors generated afterwards

Similarly to *verified errors*, *preventative errors* can have many forms but
the most common are embodied by the combinators available by importing
`parsley.errors.patterns.PreventativeErrors`.

Unlike, `verifiedX`, `preventativeX` will try and parse something, and succeed
if that fails. This makes it *similar* to `notFollowedBy`, but that alone does
not have all the desired properties. As an example:

```scala mdoc:to-string
import parsley.errors.patterns.PreventativeErrors

val ident = lexer.nonlexeme.names.identifier
val _noDot = (char('.') ~> ident).preventativeFail { v =>
    Seq(s"accessing field $v is not permitted here")
}
_noDot.parse("hi")
_noDot.parse(".foo")
```

There are also vanilla variants too (and one for `ErrorGen`). However, these also
allow for additional optional labels to be provided to describe *valid*
alternatives that would have been successful from this point. This is useful,
since parsers that follow from this point will not be parsed and cannot
contribute their own labels. It is for this reason, that the *verified error*
pattern is more effective if it possible to use.
