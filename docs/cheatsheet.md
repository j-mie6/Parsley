{%
laika.title = Cheatsheet
laika.versioned = true
laika.site.metadata.description = "Cheatsheet of common combinators and uses."
%}

# Parser Combinator Cheatsheet
Until you're more comfortable using parser combinators and have a sense for how to piece things
together, the sheer choice can be daunting. This page is designed to help bridge this gap by
bringing attention to some of the more common and useful combinators, as well as a few common idioms
for getting stuff done.

## Quick Documentation
This section is designed to give a quick reference for some of the most common combinators, their types
as well as their use.

### Basic Combinators
These are the basic combinators for combining smaller parsers into bigger parsers, or adjusting their
results. These combinators are sometimes lazy in their arguments, which is
denoted here by the regular _by-name_ (`=>A`) syntax. If an argument is
strict, it means that it will be parsed immediately on entry to the combinator
before any input can be consumed.

| Combinator          | Type                                                             | Use                                                                                             | Pronounciation    |
|---------------------|------------------------------------------------------------------|-------------------------------------------------------------------------------------------------|-------------------|
| `pure(_)` | `A => Parsley[A]`                                                | return a value of type `A` without parsing anything.                                            | "pure"            |
| `_ *> _` <br> `_ ~> _` | `( Parsley[A]`<br>`, =>Parsley[B]`<br>`) => Parsley[B]`                         | sequence two parsers, returning the result of the **second**.                                   | "then"            |
| `_ <* _` <br> `_ <~ _` | `( Parsley[A]`<br>`, =>Parsley[B]`<br>`) => Parsley[A]`                         | sequence two parsers, returning the result of the **first**.                                    | "then discard"    |
| `_.map(_)`          | `( Parsley[A]`<br>`, A => B`<br>`) => Parsley[B]`                             | use a function to change the result of a parser.                                              | "map"             |
| `_ <#> _`           | `( A => B`<br>`, Parsley[A]`<br>`) => Parsley[B]`                             | use a function to change the result of a parser. <br><br>(_Requires `import parsley.extension.HaskellStyleMap`_)                                                | "map"             |
| `_ #> _` <br> `_.as(_)`           | `( Parsley[A]`<br>`, B`<br>`) => Parsley[B]`                                  | replace the result of a parser with a fixed value.                                              | "as"              |
| `liftN(_, .., _)`   | `( (A1, A2, .., An) => B`<br>`, Parsley[A1]`<br>`, =>Parsley[A2]`<br>`, ..`<br>`, =>Parsley[An]`<br>`) => Parsley[B]`| use a function to combine the results of *n* parsers, sequencing them all together.             | "lift n" |
| `_ <\|> _` <br> `_  \|  _`         | `( Parsley[A]`<br>`, =>Parsley[A]`<br>`) => Parsley[A]`                         | try one parser, and if it fails *without consuming input* try the second                        | "or"              |
| `atomic(_)`        | `Parsley[A] => Parsley[A]`                                       | perform a parser, but roll-back any consumed input if it fails, use in conjunction with `<\|>`. | "atomic"         |
| `lookAhead(_)`      | `Parsley[A] => Parsley[A]`                                       | execute a parser, and roll-back any consumed input if it *succeeded*.                           | "look-ahead"      |
| `notFollowedBy(_)`  | `Parsley[A] => Parsley[Unit]`                                    | execute a parser, never consuming input: succeed only if the parser fails.                      | "not followed by" |
| `empty`             | `Parsley[Nothing]`                                               | fails when executed.                                                                            | "empty"           |

### Character Combinators

These combinators, found in `parsley.character` are useful for dealing with *actually* consuming input.

| Combinator   | Type                                 | Use                                                                                                               |
|--------------|--------------------------------------|-------------------------------------------------------------------------------------------------------------------|
| `char(_)`    | `Char => Parsley[Char]`              | Reading a single specific character. That character is returned.                                                  |
| `string(_)`  | `String => Parsley[String]`          | Reading a single specific string. That string is returned.                                                        |
| `satisfy(_)` | `(Char => Boolean) => Parsley[Char]` | Read any single character for which the provided function returns `true`. The character returned is the one read. |
| `oneOf(_*)`  | `Char* => Parsley[Char]`             | Read any *one* of the provided characters (which are varargs). The character returned is the one read.            |
| `noneOf(_*)` | `Char* => Parsley[Char]`             | Read any single character that is *not* one of the provided characters. The character returned is the one read.   |

### Lifty Combinators
These combinators can all be implemented in terms of `lift2` (see `liftN` above), but are considered useful enough to have
their own syntax and name. These combinators are lazy in their arguments (but not receivers),denoted here by the regular _by-name_ (`=>A`) syntax. As such,
the receiver is parsed first and may consume input, which means the argument
may contain a recursive position (and must therefore be lazy).

| Combinator          | Type                                                 | Use                                         | Pronounciation    |
|---------------------|------------------------------------------------------|---------------------------------------------|-------------------|
| `_ <~> _`           | `(Parsley[A], =>Parsley[B]) => Parsley[(A, B)]`        | combine the results using `(_, _)`          | "zip"             |
| `_ <*> _`           | `(Parsley[A => B], =>Parsley[A]) => Parsley[B]`        | combine the results using `(f, x) => f(x)`. | "ap"              |
| `_ <**> _`          | `(Parsley[A], =>Parsley[A => B]) => Parsley[B]`        | combine the results using `(x, f) => f(x)`. | "reverse ap"      |
| `_ <::> _`          | `(Parsley[A], =>Parsley[List[A]]) => Parsley[List[A]]` | combine the results using `_ :: _`.         | "cons"            |

### Composite Combinators

These combinators tackle more common complex tasks. In particular `many` and `some` are **very** important.
They are all found in `parsley.combinator`. These combinators are sometimes lazy in their arguments, which is
denoted here by the regular _by-name_ (`=>A`) syntax. Care should be taken with
the combinators with variadic arguments, as they are totally strict, even in
normally lazy positions: `LazyParsley.unary_~` can be used to restore laziness in these positions.

| Combinator        | Type                                           | Use                                                                                                                                       |
|-------------------|------------------------------------------------|-------------------------------------------------------------------------------------------------------------------------------------------|
| `many(_)`         | `Parsley[A] => Parsley[List[A]]`               | run one parser many times until it fails, collecting all the results in a list.                                                           |
| `some(_)`         | `Parsley[A] => Parsley[List[A]]`               | as above, but the parser must succeed at least once.                                                                                      |
| `eof`             | `Parsley[Unit]`                                | check if there is any remaining input: it will succeed if there is none.                                                                  |
| `choice(_*)`      | `Parsley[A]* => Parsley[A]`                    | try each of the given parsers in turn until one succeeds: uses `<\|>`.                                                                    |
| `option(_)`       | `Parsley[A] => Parsley[Option[A]]`             | try a parser, if it succeeds wrap the result in `Some`, and if it fails *without consuming input* return `None`.                          |
| `optional(_)`     | `Parsley[A] => Parsley[Unit]`                  | optionally parse something (but if it fails, it must not consume input).                                                                  |
| `sepBy1(_, _)`    | `(Parsley[A], =>Parsley[_]) => Parsley[List[A]]` | parse one thing separated by another, collecting all the results. Something like comma-separated arguments in a function call.            |
| `endBy1(_, _)`    | `(Parsley[A], =>Parsley[_]) => Parsley[List[A]]` | same as above, but the sequence must be ended by the separator again. Something like semi-colon separated statements in C-like languages. |
| `sepEndBy1(_, _)` | `(Parsley[A], =>Parsley[_]) => Parsley[List[A]]` | same as above, but the terminal separator is optional. Something like semi-colon separated statements in Scala.                           |

## Building Values and ASTs

This section covers the common ways you might build a result value or Abstract Syntax Tree (AST)
with your parsers.

The most primitive combinators for reading input all have a tendency to return the thing they parsed,
be it a single character or a string. For the most part, this is not the useful output you'd like your
parser to have.

### Transforming a single value with `map`

The quickest way to change the result of a parser is by using `.map` or the `#>` combinator (see
the above quick documentation). This is really useful for changing the result of a *single* parser,
but provides no way of combining multiple.

```scala mdoc:silent
import parsley.Parsley, Parsley.some
import parsley.character.digit

case class Num(n: Int)

// A preferred method is to use `digit.foldLeft1` to avoid creating a List.
val digits: Parsley[List[Char]] = some(digit)
// `map` here is using a function of type `List[Char] => Int`
val int: Parsley[Int] = digits.map(_.mkString.toInt) // equivalently `digits.map(_.mkString).map(_.toInt)
// `map` here is being used to wrap the `Int` in the `Num` class
val num: Parsley[Num] = int.map(Num)
```

But when you need to combine the results of two parsers more options open up.

### Combining multiple results with `lift`, `<::>`, and friends

Let's suppose we want to rule out leading zeros in the above parser. We'll need to read one non-zero
digit before we read zero or more digits. In this case, we want the first digit to be added to the
list of remaining digits. This task is quite common, so the `<::>` combinator is designed specially
for it:

```scala mdoc:reset:silent
import parsley.Parsley, Parsley.many
import parsley.character.{digit, oneOf, char}

case class Num(n: Int)

val nonzero = oneOf('1' to '9')

// <::> adds the leading non-zero char onto the other digits
val digits: Parsley[List[Char]] = nonzero <::> many(digit)
// Using #> here to handle the plain ol' zero case
val int: Parsley[Int] = char('0') #> 0 | digits.map(_.mkString.toInt)
val num: Parsley[Num] = int.map(Num)
```

But more generally, we could reach for the `lift` functions:

```scala mdoc:reset:silent
import parsley.Parsley, Parsley.many
import parsley.character.{digit, oneOf, char}
import parsley.lift.lift2

case class Num(n: Int)

val nonzero = oneOf('1' to '9')

val digits: Parsley[List[Char]] = lift2[Char, List[Char], List[Char]](_ :: _, nonzero, many(digit))
// Using #> here to handle the plain ol' zero case
val int: Parsley[Int] = char('0') #> 0 | digits.map(_.mkString.toInt)
val num: Parsley[Num] = int.map(Num)
```

Sadly, to do this, it's sometimes necessary to specify all the types, in particular for anonymous
functions that can have many possible type-instantiations, like `_ :: _`. The reason is that
Scala doesn't infer the types of arguments, only return values, so on its own `_ :: _` has no known
type. As such, the fix is to let other type-instantations help give the argument types (as above) or
to specify the types in the function manually:

```scala mdoc:silent
lift2((c: Char, cs: List[Char]) => c :: cs, nonzero, many(digit))
```

Notice that this didn't seem to be a problem with `map`. This is because the function is type-checked
after the receiver of the method: it gets given the right argument type straight away. Parsley has a syntax
for leveraging this property:

```scala mdoc:silent
import parsley.syntax.zipped.zippedSyntax2

(nonzero, many(digit)).zipped(_ :: _)
```

@:callout(warning)
The `zipped` syntax, unlike the `liftN` combinators or `lift` syntax, is not lazy in _any_ of its arguments, so care
may be needed to use `LazyParsley.unary_~` to restore laziness to those arguments that need it.
@:@

Use this form of lifting when type-inference fails you. Otherwise, for clarity, use a regular `liftN`, or the
syntactic sugar for it:

```scala mdoc:silent
import parsley.syntax.lift.{liftSyntax1, liftSyntax2}

val charCons = (c: Char, cs: List[Char]) => c :: cs

charCons.lift(nonzero, many(digit))
Num.lift(int)
```

The `lift` functions work all the way up to 22 arguments (which is the Scala 2 limit on function arguments).
The same goes for the `zipped` syntax and `lift` syntax. Don't forget about `<::>` as well as its friends `<~>`,
`<*>`, and `<**>`! They all provide a concise way of combining things in (common) special cases.

@:callout(info)
**A note for Haskellers**

In Scala, curried application is not as favoured as it is in Haskell for
performance reasons. The classic `f <$> p <*> .. <*> z` pattern that is common in Haskell
is unfavourable compared to the scala `liftN(f, p, .., z)`. For the latter, `f` is uncurried, which
is the norm, and so it is almost always more efficient. Both `<*>` and `<**>` should be, therefore,
used sparingly in idiomatic `parsley` code instead of liberally like in Haskell.

However, it goes without saying that `lift2[A => B, A, B]((f, x) => f(x), pf, px)` is no more
efficient than `pf <*> px` so the latter is favoured for that use case!
@:@

@:format(html)
<!--${cursor.currentDocument.fragments.liftN} -->
@:@
@:fragment(liftN)
```scala
( (A1, A2, .., An) => B
, Parsley[A1]
, =>Parsley[A2]
, ..
, =>Parsley[An]
) => Parsley[B]
```
@:@
