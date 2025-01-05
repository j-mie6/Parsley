{%
laika.site.metadata.description = "The parsley wiki."
%}
# Getting Started
Parsley can be added to your project by one of the following configuration commands.

```scala
// SBT
libraryDependencies += "com.github.j-mie6" %% "parsley" % "@STABLE_VERSION@"

// scala-cli
--dependency com.github.j-mie6::parsley:@STABLE_VERSION@
// or in file
//> using dep com.github.j-mie6::parsley:@STABLE_VERSION@

// mill
ivy"com.github.j-mie6::parsley:@STABLE_VERSION@"
```

Alternatively, the most up-to-date development pre-release is `@PRERELEASE_VERSION@` and the
most bleeding-edge snapshot is `@SNAPSHOT_VERSION@`.

## Examples
Parsley can leverage string and character literals in Scala by implicit conversions, keeping the
description of the parser uncluttered:

```scala mdoc:to-string
import parsley.Parsley
import parsley.syntax.character.{charLift, stringLift}

val hello: Parsley[Unit] = ('h' ~> ("ello" | "i") ~> " world!").void
hello.parse("hello world!")
hello.parse("hi world!")
hello.parse("hey world!")
```

Combinators exist to collapse results efficiently for iterative parsers, allowing for concise
definitions of simple numbers:

```scala mdoc:to-string
import parsley.character.digit
val natural: Parsley[Int] = digit.foldLeft1(0)((n, d) => n * 10 + d.asDigit)
natural.parse("0")
natural.parse("123")
```
