{%
laika.versioned = true
laika.title = "`parsley.syntax`"
parsley.tabname = "Syntactic Extensions (parsley.syntax)"
laika.site.metadata.description = "This page describes Parsley's syntactic extensions."
%}
# Synactic Extensions (`parsley.syntax`)
The `parsley.syntax` package contains several modules that enable new "syntax"
on parsers or other values. There are currently four such modules:

* `parsley.syntax.character`: contains conversions that allow for character
   and string literals to serve as parsers.
* `parsley.syntax.lift`: enables the `lift` method on functions to allow them
   to work on parsers.
* `parsley.syntax.zipped`: enables the `zipped` method on tuples of parsers to
   sequence and combine their results with a single function.

## Implicit Conversions
The `charLift` and `stringLift` conversions in `parsley.syntax.character`
allow for Scala character and string literals to work directly as parsers for
those specific literals. For example:

```scala mdoc:to-string
import parsley.syntax.character._

val p = 'a' ~> "bc"
p.parse("abc")
p.parse("axy")
```

In the above, `'a': Parsley[Char]`, and `"bc": Parsley[String]`.

@:callout(error)
If you see an error like this, when you otherwise have the implicit imported:

```scala mdoc:nest:invisible
import parsley.token.Lexer
import parsley.token.descriptions.LexicalDesc
import scala.annotation.unused
val lexer = new Lexer(LexicalDesc.plain)

import lexer.lexeme.symbol.implicits._
val _ = implicitSymbol("a"): @unused
```

```scala mdoc:fail
val p = "cb" <~ 'a'
p.parse("cba")
```

Then this likely means that you have *another* conversion in scope and the
ambiguity is not resolved. If the arguments reversed, this will become more
evident:

```scala mdoc:fail
val p = 'a' ~> "bc"
p.parse("abc")
```

In this case, a `lexer.lexeme.symbol.implicits` is imported and is clashing.
@:@

## Improved Sequencing
Both the `lift` and `zipped` modules within `parsley.implicits` enable new
ways of sequencing parsers in an idiomatic way. The `lift` syntax is perhaps
more natural, where the function to apply appears to the left of the arguments:

```scala mdoc:to-string
import parsley.character.char
import parsley.syntax.lift._

val add = (x: Int, y: Int) => x + y
add.lift(char('a').as(5), char('b').as(6)).parse("ab")
```

However, while `lift` works well when the function has its type fully elaborated,
it does not infer well:

```scala mdoc:fail
(_ + _).lift(char('a').as(5), char('b').as(6)).parse("ab")
```

This is where `zipped` comes in: by placing the function to the right of its
arguments, it can infer the type of the function based on the arguments. This
may appear slightly less natural, however:

```scala mdoc:to-string
import parsley.syntax.zipped._
(char('a').as(5), char('b').as(6)).zipped(_ + _).parse("ab")
```

@:callout(warning)
The `zipped` syntax, unlike the `liftN` combinators or `lift` syntax, is not lazy in _any_ of its arguments, so care
may be needed to use `LazyParsley.unary_~` to restore laziness to those arguments that need it.
@:@

Both `lift` and `zipped` work for up to 22-argument functions.
