{%
laika.versioned = true
laika.title = "`Lexer`"
parsley.tabname = "Lexer (parsley.token.Lexer)"
laika.site.metadata.description = "This page describes how Parsley's lexer works."
%}

```scala mdoc:invisible
import parsley.token.{Lexer, descriptions, CharPred, Basic, Unicode}, descriptions._
```

# Lexer (`parsley.token.Lexer`)

The `Lexer` class is the main-entry point to the combinator-based functionality of the `parsley.token`
package. It is given configuration in the form of a [`LexicalDesc`][Configuring the Lexer (`parsley.token.descriptions`)]
and an optional [`ErrorConfig`][`errors.ErrorConfig`]. The internal structure is then a collection
of objects that contain various forms of functionality: these are explored in more detail in this
page.

It is worth noting the highest-level structure:

* `lexeme` and `nonlexeme` are the top level categorisation of functionality, accounting for
  whitespace
* `fully` is a combinator designed to be used around the **outer-most** parser, ran **at most once**
   during a parse, to consume leading whitespace and ensure all input is consumed.
* `space` is an object that allows for explicit interaction with whitespace parsing: this is
  really only important for whitespace-sensitive languages, and `lexeme` should be used for
  almost all other applications.

@:callout(info)
*The Scaladoc for this page can be found at [`parsley.token.Lexer`](@:api(parsley.token.Lexer)).*
@:@

## Distinguishing Between "Lexeme" and "Non-Lexeme"
Broadly, the `Lexer` duplicates the vast majority of its functionality between two different
objects: `lexeme` and `nonlexeme`. Broadly speaking, everything within `nonlexeme` can be
found inside `lexeme`, but not the other way around. The name "lexeme" is not an amazing one
terminology wise, but there is a historical precedent set by `parsec`.

@:style(paragraph) Non-lexeme things @:@
A non-lexeme thing does not care about whitespace: these are raw tokens. It is highly likely that
you wouldn't want to use these in a regular parser, but they may be handy for **custom error handling**
or [**building composite tokens**](../errors/tokenextractors.md).

@:style(paragraph) Lexeme things @:@
These do account for whitespace that occurs *after* a token, consuming everything up until the
next token. This means there are some extra pieces of functionality available that don't make much
sense for non-lexeme handling. The `lexeme` object can also be used as a function
via its `apply` method, allowing it to make any parser into one that handles whitespace: this should
be done for any composite tokens made with `nonlexeme`.

@:callout(warning)
Whitespace handling should ideally be handled *uniformly* by `lexeme`: it establishes a convention
of only consuming **trailing** whitespace, which is **important** for avoiding ambiguity in a parser.
If you cannot use `lexeme.apply`, you *must* adhere to this same convention.

For handling initial whitespace in the parser (before the very first token), you should use `Lexer.fully`.
@:@

## Names and Symbols
These two categories of parser are closely linked, as described below.

### `Lexer.{lexeme, nonlexeme}.names`
This [object](@:api(parsley.token.names.Names)) contains the definitions of several different parsers for dealing with values that
represent names in a language, specifically identifiers and operators. These are configured
directly by [`LexicalDesc.nameDesc`](@:api(parsley.token.descriptions.NameDesc)), however
valid names are affected by the keywords and reserved operators as given in
[`LexicalDesc.symbolDesc`](@:api(parsley.token.descriptions.SymbolDesc)). Both are defined by
an initial letter, and then any subsequent letters.

@:callout(warning)
Note that **both** the start *and* end letters must be defined for an `identifier`/`userDefinedOperator`
to work properly. It is not the case that, say, if `identifierStart` is ommited, that
`identifierLetter` is used in its place.
@:@

In some cases, languages may have special descriptions of identifiers or operators that
work in specific scenarios or with specific properties. For instance: Haskell's distinction
between constructors, which start uppercase, and variables, which start lowercase; and
Scala's special treatment of operators that end in `:`. In these cases, the `identifier`
and `userDefinedOperator` parsers provided by `Names` allow you to refine the start letter (and
optionally the end letter for operators) to restrict them to a smaller subset. This
allows for these special cases to be handled directly.

### `Lexer.{lexeme, nonlexeme}.symbol`
Compared with `names`, which deals with user-defined identifiers and operators, [`symbol`](@:api(parsley.token.symbol.Symbol)) is
responsible for hard-coded or reserved elements of a language. This includes keywords and
built-in operators, as well as specific symbols like `{`, or `;`. The description for symbols,
found in [`LexicalDesc.symbolDesc`](@:api(parsley.token.descriptions.SymbolDesc)), describes
what the "hard" keywords and operators are for the language: these are *always* regarded as
reserved, and identifiers and user-defined operators may not take these names. However, the
`symbol` object also defines the `softKeyword` and `softOperator` combinators: these are
for keywords that are *contextually* reserved. For example, in Scala 3, the soft keyword
`opaque` is only considered a keyword if it appears before `type`; this means it is possible
to define a variable `val opaque = 4` without issue. In `parsley`, this could be performed
by writing `atomic(symbol.softKeyword("opaque") ~> symbol("type"))`. Keywords and reserved operators are
only legal if they are not followed by something that would turn them into part of a wider
identifier or user-defined operator: even if `if` is a keyword, `iffy` should not be parsed
as `if` then `fy`!

@:callout(warning)
Both soft and hard keywords cannot form part of a wider identifer. However, for this to
work it is important that `NameDesc.identifierLetter` (and/or `NameDesc.operatorLetter`) is
defined. If not, then `parsley` will not know what constitutes an illegal continutation of the symbol!
@:@

To make things easier, `symbol.apply(String)` can be used to take any literal symbol and
handle it properly with respect to the configuration (except soft keywords need to go
via `softKeyword`). If `if` is part of the `hardKeywords` set, then `symbol("if")` will
properly parse it, disallowing `iffy`, and so on. If the provided string is not reserved
in any way, it will be parsed literally, as if `string` had been used.

The `symbol` object also defines a bunch of pre-made helper parsers for some common symbols
like `;`, `,`, and so on. They are just defined in terms of `symbol.apply(String)` or `symbol.apply(Char)`.

@:style(paragraph) Implicits @:@ `symbol.implicits` contains the function `implicitSymbol`,
which does the same job as `symbol.apply`, but is defined as an implicit conversion. By
importing this, string literals can themselves serve as parsers of type `Parsley[Unit]`, and
parse symbols correctly. With this, it instead of `symbol("if")` you can simply write `"if"`.

## `Lexer.{lexeme, nonlexeme}` Numeric Parsers
This [object](@:api(parsley.token.Lexer$lexeme$)) contains the definitions of several different parsers for handling *numeric* data:
this includes both integers and floating point numbers. The configuration for all of these parsers
is managed by [`LexicalDesc.numericDesc`](@:api(parsley.token.descriptions.numeric.NumericDesc)).
The members are split into three kinds:

* [`parsley.token.numeric.IntegerParsers`](@:api(parsley.token.numeric.IntegerParsers)): values with this type
  deal with whole numbers, and this interface in particular has support for different bases of
  number as well as various bit-widths of parsed data. When the bit-width of the parser is restricted,
  the generated result can be any numeric type that is wide enough to accommodate those values. If
  the parsed int does not fit in the required bounds, a parse error will be generated. If no bit-width
  is specified, an arbitrary `BigInt` is used.

  The supported bit-widths within `parsley` are 8, 16, 32, and 64. When one of these widths is chosen,
  the Scala compiler will automatically pick a result type that matches the same width (so `Int` for 32).
  If the generic parameter is filled manually, the given type will be used instead as long as it is
  wide enough.

  Currently, there is no way of adding new bit-widths or defining custom numeric container types.

* [`parsley.token.numeric.RealParsers`](@:api(parsley.token.numeric.RealParsers)): values with this type
  deal with floating-point numbers **only**: values without a point or an exponent (if allowed)
  will not be parsed by these parsers. Like `Integer`, different bases can be specified: in this
  case the meaning of exponents can be controlled within the configuration, for instance, a
  hexadecimal floating-point literal like `0xAp4` classically would represent `10 * 2^4`, or `160`,
  because `p` represents an exponent delimiter and hexadecimal exponents are normally base 2 (but
  this is fully configurable in `parsley`).

  Compared to `IntegerParsers`, different precisions can be chosen for `RealParsers`, allowing for
  arbitrary-precision floats, `Float`, and `Double` results. For the stricter representations,
  there is a `doubleRounded`/`floatRounded` that just gives the nearest valid value (with no parse
  errors), and a `double`/`float` which demands that the parsed literal must at least be
  between the smallest and largest numbers of the type.

* [`parsley.token.numeric.CombinedParsers`](@:api(parsley.token.numeric.CombinedParsers)): values with this type
  can deal with both integers and floating-point numbers. This is done by returning one or the other
  as part of an `Either`. A *slightly* limited selection of bit-widths and precisions are available
  for both parts. The draw of these combinators is that they may remove the ambiguity between the
  two kinds of literal so that no backtracking is required within the parser.

@:callout(info)
The configuration which specifies which of the numeric bases are legal for a number literal applies
only to the `number` parsers within `Integer`, `Real`, and `Combined`. A parser for a specific base
can always just be used directly, even when otherwise disabled in configuration.
@:@

### Examples of Configuration and Valid Literals
The [`plain`](@:api(parsley.token.descriptions.numeric.NumericDesc$#plain))
definition of `NumericDesc` provides a variety of different configurations for the
numeric literals depending on the literal base, so it mostly suffices to look at the effects of
these on the different bases to get a sense of what does what.

```scala mdoc:silent
val lexer = new Lexer(LexicalDesc.plain)
```

The basic configuration allows `number` to work with hexadecimal and octal literals, as well
as decimal. These have their standard prefixes of `0x` and `0o`, respectively (or uppercase
variants). This means that `unsigned.number` will allow literals like `0`, `0xff`, `0o45`
and `345`. For `signed`, each of these may be preceded by a `+` sign, but this is not *required*; if `positiveSign` is set to `PlusSignPresence.Compulsory`, positive literals would always require a `+`; and if it is set to `PlusSignPrecense.Illegal`, the `+` prefix can never be used (but `-` is fine regardless). By default, `023` is legal, but this can be
disabled by setting `leadingZerosAllowed` to `false`.

```scala mdoc
val num = lexer.lexeme.signed.number
num.parse("0")
num.parse("0xff")
num.parse("+0o45")
num.parse("-345")
```

In the basic configuration, break characters are not supported. However, by setting
`literalBreakChar` to `BreakCharDesc.Supported('_', allowedAfterNonDecimalPrefix = true)`,
say, will allow for `1_000` or `0x_400`. Setting the second parameter to `false` will forbid
the latter example, as the break characters may then only appear between digits.

```scala mdoc:height=2
val lexerWithBreak = new Lexer(LexicalDesc.plain.copy(
        numericDesc = NumericDesc.plain.copy(
            literalBreakChar = BreakCharDesc.Supported('_', allowedAfterNonDecimalPrefix = true))
    ))
val withBreak = lexerWithBreak.lexeme.signed.number
withBreak.parse("1_000")
withBreak.parse("1_")
withBreak.parse("2__0") // no double break
```

Real numbers in the default configuration do not support literals like `.0` or `1.`, this
behaviour must be explicitly enabled with `trailingDotAllowed` and `leadingDotAllowed`: note
that `.` is not a valid literal, even with both flags enabled! By default, all four different
bases support exponents on their literals for floating-point numbers. This could be turned
off for each by using `ExponentDesc.NoExponents`. However, with exponents enabled, it is
configured that the non-decimal bases all *require* exponents for valid literals. Whilst
`3.142` is valid decimal literal, `0x3.142` is not a legal hexadecimal literal: to make
it work, the exponent must be added, i.e. `0x3.142p0`, where `p0` is performing `* 2^0`.
For each of the non-decimal literals, the base of the exponent is configured to be `2`, hence
`2^0` in the previous example; for decimal it is set to the usual `10`, so that `2e3` is `2*10^3`,
or `2000`. Notice that literals do not *require* a point, so long as they do have
an exponent.

```scala mdoc:height=2
val real = lexer.lexeme.real
real.hexadecimalDouble.parse("0x3.142")
real.hexadecimalDouble.parse("0x3.142p0")
real.binary.parse("0b0.1011p0")
real.decimal.parse("3.142")
real.decimal.parse("4")
real.decimal.parse("2e3")
```

@:callout(info)
When a floating point literal is parsed in a non-decimal base, the meaning of each digit past
the point is to be a fraction of that base. The example `0x3.142p0`, for instance is not equal
to the decimal `3.142`. Instead, it is equal to `(3 + 1/16 + 4/(16^2) + 2/(16^3)) * 2^0 = 3.07861328125`. Handily, hexadecimal floats are still equal to the 4-bit bunching up of binary
floats: `0x0.Bp0` is the same as `0b0.1011p0`, both of which are `0.6875` in decimal.
@:@

## `Lexer.{lexeme, nonlexeme}` Text Parsers
This [object](@:api(parsley.token.Lexer$lexeme$$text$)) deals with the parsing of both string
literals and character literals, configured broadly by [`LexicalDesc.textDesc`](@:api(parsley.token.descriptions.text.TextDesc)):

* [`parsley.token.text.StringParsers`](@:api(parsley.token.text.StringParsers)): values with this type
  deal with multi-character/codepoint strings. Specifically, the interface provides ways
  of dealing with different levels of character encodings.

  In practice, there are four values with this type: `text.string`, `text.rawString`,
  `text.multiString`, and `text.rawMultiString` covering three different kinds of string:

    * `text.string`: used to handle regular string literals where escape characters are
      present. They are single-line.

    * `text.multiString`/`text.rawMultiString`: handles string literals using the
      `multiStringEnds` configuration within `LexicalDesc.textDesc`. These string literals
      can span multiple lines.

    * `text.rawString`/`text.rawMultiString`: handles string literals that do not have
      escape characters.

  @:callout(info)
  Note that currently, whatever a string literal is started with, it must end with the
  exact same sequence.
  @:@

* [`parsley.token.text.CharacterParsers`](@:api(parsley.token.text.CharacterParsers)): like `StringParsers`,
  the `text.character` object can handle different kinds of text encoding. However, unlike
  `String`, there is only one kind of character available, which has escape codes and can only
  contain a single graphic character (or, if applicable, single unicode codepoint).

  @:callout(info)
  A single codepoint here refers to having at most two 16-bit UTF-16 characters in a surrogate
  pair, allowing for any character in the range `0x00000` to `0x10ffff`. Some unicode
  characters are composed of multiple codepoints. As an example, national flags are composed
  of two "regional indicator characters", for instance 🇬 and 🇧, making 🇬🇧. Such emoji
  cannot appear within a parsed character in `parsley`, and can instead only be written in a
  string.
  @:@

### Examples of Configuration and Valid Literals
The majority of configuration for strings and characters is focused around the escape
sequences. Outside of that, it is mostly just what the valid start and end sequences
are valid for different flavours of literal. However, the `graphicCharacter` predicate
is used to denote what the valid characters are that can appear in a string verbatim.
This can be restricted to a smaller set than might otherwise have been checked by
`ascii` or `latin1` parsers. In these instances, a different error message would be
generated:

```scala mdoc:to-string
val aboveSpace = Unicode(_ >= 0x20)
def stringParsers(graphicChar: CharPred = aboveSpace,
                  escapeDesc: EscapeDesc = EscapeDesc.plain) =
    new Lexer(LexicalDesc.plain.copy(
        textDesc = TextDesc.plain.copy(
            escapeSequences = escapeDesc,
            graphicCharacter = graphicChar
        )
    )).nonlexeme.string

val fullUnicode = stringParsers(aboveSpace)
val latin1Limited = stringParsers(Basic(c => c >= 0x20 && c <= 0xcf))

fullUnicode.latin1.parse("\"hello α\"")
latin1Limited.fullUtf16.parse("\"hello α\"")
```

When it comes to escape characters, the [configuration](@:api(parsley.token.descriptions.text.EscapeDesc)) distinguishes between four kinds of escape sequence, which are further sub-divided:

@:style(paragraph) Denotative escapes @:@
These are a family of escape sequences that are names or symbols for the escape characters
they represent. Parsley supports three different kinds of denotative escape characters:

* `EscapeDesc.literals`: these are a set of characters that plainly represent themselves, but
  for whatever reason must be escaped to appear within the string. Some of the most common
  examples would be `\"` or `\\`, which are an escaped double quote and backslash,
  respectively. The literal set for these would be `Set('"', '\\')`

* `EscapeDesc.mapping`: these are a mapping of specific sequences of characters to the underlying
  characters they represent. Commonly, this might be something like `\n`, which would be
  represented in the map by an entry `"n" -> 0xa`, or `"NULL" -> 0x0` for `'\NULL'`.

Of course, all denotative escape sequences can be represented by the `mapping` on its own,
and all the above examples could be represented by
`Map("\"" -> '"', "\\" -> '\\', "n" -> 0xa, "NULL" -> 0x0)`. For `literals` in particular, the
`Set` is more ergonomic than the `Map`.

@:callout(error)
Note that the `literals` set, along with the keys of `mapping`, must all be
distinct from each other. Furthermore, no empty sequences may be placed in `mapping`.
Violating any of these requirements will result in an error.
@:@

@:style(paragraph) Numeric escapes @:@
These are escapes that represent the numeric code of a specific character. There are four different bases for numeric escapes: binary, octal, hexadecimal, and decimal. Each of these
can have their own unique prefix (or lack there of), maximum allowed value, and specific
number of digits:

* `NumberOfDigits.Unbounded`: simply, this allows the numeric escape to have any number of
  digits, so long as the end result is within the specified maximum value of the escape.

* `NumberOfDigits.AtMost(n: Int)`: this denotes that there is an upper-limit to the number
  of digits allowed for the escape sequence, but it can take any number of digits below
  this limit. Again, the end result must still be within the specified maximum value of the
  escape.

* `NumerOfDigits.Exactly(ns: Int*)`: this denotes that the number of digits can be one of
  the specified totals in `ns` (there must be at least one provided number). For example
  `Exactly(1, 2, 4, 6)` would allow escapes like `\0`, `\20`, `\0400`, `\10fffe` are legal,
  but `\400` would not be.

@:style(paragraph) String gaps @:@
Supported for string literals only, string gaps allow for prunable whitespace
within a string literal. These take the form of a backslash, followed by whitespace,
terminated by another backslash (this can include newlines, even in otherwise
single-line strings). As an example:

```scala mdoc:to-string
val withGaps = stringParsers(escapeDesc = EscapeDesc.plain.copy(gapsSupported = true))
withGaps.ascii.parse(""""Hello \

      \World!" """)
```

@:style(paragraph) Empty escapes @:@
These are also only supported by string literals. These characters have no effect on the
string literal, but otherwise allow for disambiguation with multi-character escape
sequences. For example, if `EscapeDesc.emptyEscape` is set to `Some('&')`, then
`"\x20\&7"` would be interpreted as the string `" 7"`, however, without the `\&`
character, it would try and render character `0x207`.

## `Lexer.lexeme.{enclosing, separators}`
These two objects just contain various shortcuts for doing things such as semi-colon separated
things, or braces enclosed things, etc. There is nothing special about them: with `lexer.lexeme.symbol.implicits.implicitSymbol` imported, `"(" ~> p <~ ")"` is the same as `lexer.lexeme.enclosing.parens(p)`. The choice of one style over the other is purely up to taste.

## Whitespace-Sensitive Languages and `Lexer.space`
Normally, the whitespace definitions used by `lexeme` are fixed as described by the
[`LexicalDesc.spaceDesc`](@:api(parsley.token.descriptions.SpaceDesc)); accounting
for the comments and spaces themselves. However, some languages, like Python and Haskell
do not have constant definitions of whitespace: for instance, inside a pair of parentheses,
newline characters are no longer considered for the current indentation. To support this,
`parsley` allows for the space definition to be locally altered during parsing if
`LexicalDesc.spaceDesc.whitespaceIsContextDependent` is set to `true`: this *may* impact
the performance of the parser.

@:callout(error)
If the `LexicalDesc.spaceDesc.whitespaceIsContextDependent` flag is turned on it is **crucial** that
either the `Lexer.fully` combinator is used, *or* `Lexer.space.init` is ran as the very first thing the
top-level parser does. Without this, the context-dependent whitespace will not be set-up correctly!
@:@

In this mode, it is possible to use the `lexer.space.alter` combinator to *temporarily*
change the definition of whitespace (but not comments) within the scope of a given parser.
As an example:

```scala
val withNewline = Basic(_.isSpace)
val expr = ... | "(" ~> lexer.space.alter(withNewline)(expr) <~ ")"
```

For the duration of that nested `expr` call, newlines are considered regular whitespace. This,
of course, is assuming that newlines were *not* considered whitespace under normal conditions.
