{%
laika.versioned = true
laika.title = "`ErrorBuilder`"
parsley.tabname = "Constructing Custom Errors (parsley.errors.ErrorBuilder)"
laika.site.metadata.description = "This page describes how to customise the error type."
%}

```scala mdoc:invisible
import parsley.errors.ErrorBuilder
```

# Constructing Custom Errors
By default, `parsley` returns errors that consist of `String`-based
content. However, it is possible to build error messages into a
datatype or format that is user-defined. This is done with the
`ErrorBuilder` typeclass.

The `ErrorBuilder` is pulled in implicitly by the `parse` method of
the `Parsley` type:

```scala
class Parsley[A] {
    def parse[Err: ErrorBuilder](input: String): Result[Err, A]
}
```

This is equivalent to having an implicit parameter of type
`ErrorBuilder[Err]`. As the `ErrorBuilder` companion object has an
implicit value of type `ErrorBuilder[String]` only, the type
`String` is chosen as the default instantiation of `Err` by Scala.
Providing another `ErrorBuilder` implicit object in a tighter
scope (or adding an explicit type ascription with another implicit
object available), you are able to hook in your own type instead.

This page describes how the `ErrorBuilder` is structured, and gives
an example of how to construct a lossy type suitable for unit
testing generated error messages.

@:callout(info)
*The Scaladoc for this page can be found at [`parsley.errors.ErrorBuilder`](@:api(parsley.errors.ErrorBuilder)).*
@:@

## Error Message Structure
Error messages within `parsley` take two different forms: *vanilla*
or *specialised*. The error chosen depends on the combinators used
to produce it: `empty`, `unexpected`, `char`, `string`, etc all produce vanilla errors; and `fail` and its derivatives produce
specialised errors. An `ErrorBuilder` must describe how to build
both kinds of error; their structure is explained below.

### Vanilla Errors
```
┌───────────────────────────────────────────────────────────────────────┐
│                          ┌────────────────┐◄──────── position         │
│                  source  │                │                           │
│                     │    │   line      col│                           │
│                     ▼    │     │         ││                           │
│                  ┌─────┐ │     ▼         ▼│   end of input            │
│               In foo.txt (line 1, column 5):       │                  │
│                 ┌─────────────────────┐            │                  │
│unexpected ─────►│                     │            │  ┌───── expected │
│                 │          ┌──────────┐ ◄──────────┘  │               │
│                 unexpected end of input               ▼               │
│                 ┌──────────────────────────────────────┐              │
│                 expected "(", "negate", digit, or letter              │
│                          │    └──────┘  └───┘     └────┘ ◄────── named│
│                          │       ▲        └──────────┘ │              │
│                          │       │                     │              │
│                          │      raw                    │              │
│                          └─────────────────┬───────────┘              │
│                 '-' is a binary operator   │                          │
│                 └──────────────────────┘   │                          │
│                ┌──────┐        ▲           │                          │
│                │>3+4- │        │           expected items             │
│                │     ^│        │                                      │
│                └──────┘        └───────────────── reason              │
│                   ▲                                                   │
│                   │                                                   │
│                   line info                                           │
└───────────────────────────────────────────────────────────────────────┘
```

A vanilla error consists of three unique components: the
`unexpected` component, which describes the problematic token;
the `expected` component, which describes the possible parses that
would have avoided the error; and the `reason` component, which
gives additional context for an error. These are in addition to
parts shared with *specialised* errors: the `source`, `position`,
and the `lineInfo`. Any of the three unique components may be
missing from the error, and the `ErrorBuilder` will need to be able
to handle this.

Within both `unexpected` and `expected`, items can have one of three forms: `named`, which indicates they came from labels;
`raw`, which means they came directly from the input itself; and
`endOfInput`, which means no more input was available. All three
of these states can be formatted independently.

### Specialised Errors
```
┌───────────────────────────────────────────────────────────────────────┐
│                          ┌────────────────┐◄──────── position         │
│                  source  │                │                           │
│                     │    │   line       col                           │
│                     ▼    │     │         │                            │
│                  ┌─────┐ │     ▼         ▼                            │
│               In foo.txt (line 1, column 5):                          │
│                                                                       │
│           ┌───► something went wrong                                  │
│           │                                                           │
│ message ──┼───► it looks like a binary operator has no argument       │
│           │                                                           │
│           └───► '-' is a binary operator                              │
│                ┌──────┐                                               │
│                │>3+4- │                                               │
│                │     ^│                                               │
│                └──────┘                                               │
│                   ▲                                                   │
│                   │                                                   │
│                   line info                                           │
└───────────────────────────────────────────────────────────────────────┘
```

In contrast to the *vanilla* error, specialised errors have one
unique component, `messages`, which is zero or more lines of
bespoke error messages generated by `fail` combinators.

## The `ErrorBuilder` Typeclass
Within the `ErrorBuilder` trait, there is a number of undefined
type aliases. Each of these must be implemented by an extender and
provide an internal type to represent different components within
the system. These are used to ensure maximal flexiblity of the
user to pick how each component should be represented without
exposing unnecessary information into the rest of the system.

After these types are specified, the methods of the typeclass
can be implemented. These put together the primtive-most components
and compose them into the larger whole. The documentation of the
typeclass details the role of these well enough, however. For example's sake, however, these
are the two shapes of call that will be made for the different types of error messages:

#### Vanilla
```
(line 1, column 5):
  unexpected end of input
  expected "(", "negate", digit, or letter
  '-' is a binary operator
  >3+4-
       ^
```
```scala mdoc:silent:nest
val builder = implicitly[ErrorBuilder[String]]
builder.build (
    builder.pos(1, 5),
    builder.source(None),
    builder.vanillaError (
        builder.unexpected(Some(builder.endOfInput)),
        builder.expected (
            builder.combineExpectedItems(Set (
                builder.raw("("),
                builder.raw("negate"),
                builder.named("digit"),
                builder.named("letter")
            ))
        ),
        builder.combineMessages(List(
            builder.reason("'-' is a binary operator")
        )),
        builder.lineInfo("3+4-", Nil, Nil, 1, 4, 4)
    )
)
```
One builder call not shown here, is a call to
`builder.unexpectedToken`. This is a bigger discussion and is
deferred to [Token Extraction in `ErrorBuilder`]

#### Specialised
```
In file 'foo.txt' (line 2, column 6):
  first message
  second message
  >first line of input
  >second line
        ^^^
  >third line
  >fourth line
```
```scala mdoc:silent:nest
val builder = implicitly[ErrorBuilder[String]]
builder.build (
    builder.pos(2, 6),
    builder.source(Some("foo.txt")),
    builder.specializedError (
        builder.combineMessages(List(
            builder.message("first message"),
            builder.message("second message"),
        )),
        builder.lineInfo("second line",
                         List("first line of input"),
                         List("third line", "fourth line"),
                         2,
                         5,
                         3)
    )
)
```

## Constructing Test Errors
As an example of how to construct an `ErrorBuilder` for a type,
consider the following representation of `TestError`:

```scala
case class TestError(pos: (Int, Int), lines: TestErrorLines)

sealed trait TestErrorLines
case class VanillaError(
    unexpected: Option[TestErrorItem],
    expecteds: Set[TestErrorItem],
    reasons: Set[String]) extends TestErrorLines
case class SpecializedError(msgs: Set[String]) extends TestErrorLines

sealed trait TestErrorItem
case class TestRaw(item: String) extends TestErrorItem
case class TestNamed(item: String) extends TestErrorItem
case object TestEndOfInput extends TestErrorItem
```

This type, as will become evident from the builder derived
from it, is lossy and does not perfectly encode all the
information available. Notice that `TestErrorItem` is a supertype
of `TestRaw`, `TestNamed`, and `TestEndOfInput`: this is
required, as the representation of each must all share a common
supertype.

To construct an `ErrorBuilder[TestError]`, the type aliases must
first be filled in:

```scala
class TestErrorBuilder extends ErrorBuilder[TestError] {
    type Position = (Int, Int)
    type Source = Unit
    type ErrorInfoLines = TestErrorLines
    type Item = TestErrorItem
    type Raw = TestRaw
    type Named = TestNamed
    type EndOfInput = TestEndOfInput.type
    type Message = String
    type Messages = Set[String]
    type ExpectedItems = Set[TestErrorItem]
    type ExpectedLine = Set[TestErrorItem]
    type UnexpectedLine = Option[TestErrorItem]
    type LineInfo = Unit
    //...
}
```

These types can be determined by examining the shape of `TestError`: for bits that it doesn't work, these are set to `Unit`. With these in place, the refined types of the typeclass
methods make it very easy to fill in the gaps:

```scala
class TestErrorBuilder extends ErrorBuilder[TestError] {
    //...
    def build(pos: (Int, Int), source: Unit,
              lines: TestErrorLines): TestError = TestError(pos, lines)
    def vanillaError(
        unexpected: Option[TestErrorItem],
        expected: Set[TestErrorItem],
        reasons: Set[String],
        line: Unit
      ): TestErrorLines = VanillaError(unexpected, expected, reasons)
    def specializedError(
        msgs: Set[String],
        line: Unit
      ): TestErrorLines = SpecializedError(msgs)
    def pos(line: Int, col: Int): (Int, Int) = (line, col)
    def source(sourceName: Option[String]): Unit = ()
    def combineExpectedItems(alts: Set[TestErrorItem]): Set[TestErrorItem] = alts
    def combineMessages(alts: Seq[String]): Set[String] = alts.toSet
    def unexpected(item: Option[TestErrorItem]): Option[TestErrorItem] = item
    def expected(alts: Set[TestErrorItem]): Set[TestErrorItem] = alts
    def message(msg: String): String = msg
    def reason(msg: String): String = msg
    def raw(item: String): TestRaw = TestRaw(item)
    def named(item: String): TestNamed = TestNamed(item)
    val endOfInput: TestEndOfInput.type = TestEndOfInput

    val numLinesAfter: Int = 0
    val numLinesBefore: Int = 0
    def lineInfo(
        line: String,
        linesBefore: Seq[String],
        linesAfter: Seq[String],
        lineNum: Int, errorPointsAt: Int, errorWidth: Int
      ): Unit = ()

    // The implementation of this is usually provided by a mixed-in
    // token extractor, discussed in `tokenextractors`
    def unexpectedToken(
        cs: Iterable[Char],
        amountOfInputParserWanted: Int,
        lexicalError: Boolean
      ): Token = ???
}
```

Each of the methods above do the bare minimum work to
satisfy the types. As noted in the comment, the implementation
of `unexpectedToken` is usually done by mixing in a
*token extractor*, which is explained [here][Token Extraction in `ErrorBuilder`].
