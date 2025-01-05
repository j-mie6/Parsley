{%
laika.versioned = true
laika.title = "`tokenextractors`"
parsley.tabname = "Token Extraction in ErrorBuilder (parsley.errors.tokenextractors)"
laika.site.metadata.description = "This page describes how to customise the unexpected token of an error."
%}

# Token Extraction in `ErrorBuilder`
When *vanilla* error messages are generated internally to
`parsley`, the unexpected component is usually derived from
the raw input, or a name explicitly given to an `unexpected`
combinator. However, that does not necessarily provide the
most informative or precise error messages.

Instead, the `ErrorBuilder` typeclass has an `unexpectedToken`
method that can be used to determine how the token should
be formulated in the event that it would have otherwise
come raw from the input. Its signature is as follows:

```scala
def unexpectedToken(
    cs: Iterable[Char],
    amountOfInputParserWanted: Int,
    lexicalError: Boolean
  ): Token
```

The first argument, `cs`, is the input from the point that
the bad input was found; the second is the amount of input
the parser tried to read when it failed; and `lexicalError`
denotes whether or not the failure happened whilst trying
to parse a token from `Lexer`, or not. The return value,
of type `Token`, is one of the following classes:

```scala
case class Named(name: String, span: Int) extends Token
case class Raw(tok: String) extends Token
```

A `Raw` token indicates no further processing of the input could
occur to get a better token, and some is returned verbatim.
Otherwise, a `Named` token can replace a raw token with something
derived from the input -- the `span` here denotes how wide that
token had been determined to be.

The idea is that `unexpectedToken` should examine the provided
arguments and determine if a more specific token can be extracted
from the residual input, or, if not, produce a final `Raw` token
of the desired width.
In practice, while a user could implement the `unexpectedToken`
method by hand, `parsley` provides a collection of
*token extractors* that can be mixed-in to an `ErrorBuilder` to
implement it instead.

## Basic Extractors
There are three basic extractors available in `parsley`:
`SingleChar`, `MatchParserDemand`, and `TillNextWhitespace`.
Each is discussed below. Each of them have special handling for
whitespace characters and ones that are unprintable, which are
given names.

### `SingleChar`
This extractor simply takes the first *codepoint* of the input
stream `cs` and returns it. A *codepoint* is a single unicode
character, which may consist of one or two bytes. As an example,
the default formatting may be instantiated with this extractor
by writing:

```scala mdoc:silent:nest
import parsley.errors.DefaultErrorBuilder
import parsley.errors.tokenextractors.SingleChar

val builder = new DefaultErrorBuilder with SingleChar
```

### `MatchParserDemand`
This extractor, as its name suggests, takes more than a single
codepoint from the input, instead taking as many as the parser
has requested via the `amountOfInputParserWanted` argument.
As an example, the default formatting may be instantiated with
this extractor by writing:

```scala mdoc:silent:nest
import parsley.errors.DefaultErrorBuilder
import parsley.errors.tokenextractors.MatchParserDemand

val builder = new DefaultErrorBuilder with MatchParserDemand
```

### `TillNextWhitespace`
Unlike the other extractors, this one has additional
configuration. It generally aims to take as much input
as necessary to find the next the next whitespace character,
which can be changed by overriding the `isWhitespace` method.
However, this can be capped as the minimum of the input the
parser demanded or until the next whitespace.
As an example, the default formatting may be instantiated with
this extractor by writing:

```scala mdoc:silent:nest
import parsley.errors.DefaultErrorBuilder
import parsley.errors.tokenextractors.TillNextWhitespace

val builder = new DefaultErrorBuilder with TillNextWhitespace {
    def trimToParserDemand = true
}
```

This extractor, with `trimToParserDemand = true` is the default
currented used by `parsley` for all error messages. By default,
`isWhitespace` matches any character `c` for which `c.isWhitespace` is true.

## `Lexer`-backed Extraction
The default strategies outlined above all ignore the
`lexicalError` flag passed to `unexpectedToken`. To provide a
more language-directed token extraction, however, the `LexToken`
extractor is also provided.

It has one compulsory configuration and two more that have defaults:

```scala
trait LexToken {
    def tokens: Seq[Parsley[String]]
    def extractItem(cs: Iterable[Char], amountOfInputParserWanted: Int): Token = {
        SingleChar.unexpectedToken(cs)
    }
    def selectToken(matchedToks: List[(String, Int)]): (String, Int) = {
        matchedToks.maxBy(_._2)
    }
}
```

Here, the `tokens` are parsers for valid tokens within the
language being parsed: each returns the name of that token as it
would be displayed in the error message. The extractor will
try to parse all of these tokens, and should at least one
succeed the **non-empty** list of parsed tokens will be passed
to `selectToken` for one to be picked to be used in the error:
by default, the one which is the widest is chosen. If no tokens
could be parsed, or the error occured *during* the parsing of
a token/within the `markAsToken` combinator (as denoted by
`lexicalError` normally), then `extractItem` is used instead.
This usually should defer to another kind of token extractor,
which, for convience, all expose their functionality in their companion objects.

@:callout(warning)
The intention of the `tokens` sequence is that they should *not*
consume whitespace: were they to do so, this whitespace would
form part of the generated token! When using `Lexer` to fill
this sequence, be sure to use `lexer.nonlexeme` to source the
tokens.
@:@

As an example, a language which already has an available `lexer`
built with lexical description `desc` can implement a `LexToken` as follows:

```scala mdoc:invisible
import parsley.token.Lexer
import parsley.token.descriptions.LexicalDesc

val desc = LexicalDesc.plain
val lexer = new Lexer(desc)
```

```scala mdoc:silent:nest
import parsley.errors.DefaultErrorBuilder
import parsley.errors.tokenextractors.LexToken

val builder = new DefaultErrorBuilder with LexToken {
    def tokens = Seq(
        lexer.nonlexeme.integer.decimal.map(n => s"integer $n"),
        lexer.nonlexeme.names.identifier.map(v => s"identifier $v")
    ) ++ desc.symbolDesc.hardKeywords.map { k =>
        lexer.nonlexeme.symbol(k).as(s"keyword $k")
    }
}
```

Obviously, this may not be an exhaustive list of tokens, but is
illustrative of how to set things up.
