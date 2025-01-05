{%
laika.versioned = true
laika.title = "`parsley.generic`"
parsley.tabname = "Generic Bridges (parsley.generic)"
laika.site.metadata.description = "This page describes how to use generic bridges to factor code."
%}

```scala mdoc:invisible
import parsley.Parsley, Parsley._
import parsley.generic.ParserBridge1
```
# Generic Bridges (`parsley.generic`)

The *Parser Bridge* pattern is a technique for decoupling semantic actions from the parser itself.
The `parsley.generic` module contains 23 classes that allow
you to get started using the technique straight away if you wish.

@:callout(info)
*The Scaladoc for this page can be found at [`parsley.generic`](@:api(parsley.generic$)).*
@:@

## What are *Parser Bridges*?
Without making use of *Parser Bridges*, results of parsers are usually combined by using `lift`,
`map`, or `zipped`:

```scala mdoc:invisible
val px: Parsley[Int] = pure(5)
val py: Parsley[Int] = pure(6)
```

```scala mdoc
import parsley.syntax.zipped.zippedSyntax2
case class Foo(x: Int, y: Int)
// with px, py of type Parsley[Int]
val p = (px, py).zipped(Foo(_, _))
```

These work fine for the most part, however, there are couple of problems with this:

1. In Scala 3, `Foo(_, _)` actually needs to be written as `Foo.apply`, which introduces some (minor) noise;
   `zipped` itself is even contributing noise.
1. `Foo` itself is a simple constructor, if it gets more complex, readability rapidly decreases:
    - The result produced may require inspection of the data, including pattern matching (see [Normalising or Disambiguating Data]).
    - Additional information may need to be threaded in, like position information.
    - Data invariances may need to be enforced (see [Enforcing Invariances]).
1. Constructor application is on the right of the data, which people may find harder to read;
   this can be mitigated with `lift`, but that may run into type inference issues.

For some people (1) or (3) may not be an issue, or can be tolerated, but (2) can get out of hand
quickly. For larger parsers, properly decoupling these issues can make a huge difference to the
maintainability.

@:style(paragraph) How do bridges help? @:@
In short, a bridge is an object that provides an `apply` method that takes parsers as arguments,
as opposed to values. This means that they can be used directly in the parser with the logic kept
elsewhere. While you can just define a bridge manually with an `apply` method (or even just as a
function), it is more ergonomic to *synthesise* a bridge in terms of a function that does not interact
with `Parsley` values. If we assume that the companion object of `Foo` has been turned into such a
bridge (definition below), the above example can be written as:

```scala mdoc
val q = Foo(px, py)
```

In this version, the act of constructing the `Foo` value has been abstracted behind the bridge, `Foo`:
this means that the underlying implementation can vary without changing the parser.

@:style(paragraph) What are *Generic Bridges*? @:@
Generic bridges are the templating mechanism that allow for the synthesis of an `apply` method that
works on values of type `Parsley` from another that does not. While you can define your own bridge
templates (see [the associated tutorial](../tutorial/parser-bridge-pattern.md) for an explanation),
`parsley` provides some basic ones to get you started.

## How to use
The [`parsley.generic`](@:api(parsley.generic$)) module contains `ParserBridge1` through
`ParserBridge22` as well as `ParserBridge0`; they all extend `ParserBridgeSingleton`, which provides
some additional combinators as well as `ErrorBridge`, which allows labels and a reason to be attached
to a bridge.

### `ParserBridge1[-T1, +R]` through `ParserBridge22[-T1, .., -T22, +R]`
Each of these traits are designed to be implemented ideally by a companion object for a `case class`.
For example, the `Foo` class above can have its companion object turned into a bridge by extending
`ParserBridge2` (which is for two argument bridges):

```scala mdoc
import parsley.generic.ParserBridge2
object Foo extends ParserBridge2[Int, Int, Foo]
```

This defines `def apply(px: Parsley[Int], py: Parsley[Int]): Parsley[Foo]`, implementing it in
terms of `def apply(x: Int, y: Int): Foo`, which is included as part of Scala's automatic `case class`
implementation. By making use of a companion object, this is *all* the boilerplate required to
start using the bridge. Of course, it's possible to define standalone bridges as well, so long as
you provide an implementation of `apply`, as illustrated by this error:

```scala mdoc:fail
object Add extends ParserBridge2[Int, Int, Int]
```

```scala mdoc:invisible
object Add extends ParserBridge2[Int, Int, Int] {
    def apply(x: Int, y: Int) = x + y
}
```


Implement that `apply` method and it's good to go! Of course, if the traits are mixed into a regular `class`,
they can also be parametric:

```scala mdoc:fail
class Cons[A] extends ParserBridge2[A, List[A], List[A]]
```

### `ParserSingletonBridge[+T]`
All the generic bridges extend the `ParserSingletonBridge` trait instantiated to a function type. For
example, `trait ParserBridge2[-A, -B, +C] extends ParserSingletonBridge[(A, B) => C]`. This means that
every bridge uniformly gets access to a couple of extra combinators in addition to their `apply`:

```scala
trait ParserSingletonBridge[+T] {
    final def from(op: Parsley[_]): Parsley[T]
    final def <#(op: Parsley[_]): Parsley[T] = this.from(op)
}
```

The implementation of `from` is not important, it will be handled by the other `ParserBridgeN`s. What these
two combinators give you is the ability to write `Foo.from(parser): Parsley[(Int, Int) => Foo]`, for instance.
This can be useful when you want to use a bridge somewhere where the arguments cannot be directly applied,
like in [chain](expr/chain.md) or [precedence](expr/precedence.md) combinators:

```scala mdoc:silent
import parsley.expr.chain
import parsley.syntax.character.stringLift

val term = chain.left1(px)(Add.from("+")) // or `Add <# "+"`
```

They are analogous to the `as` and `#>` combinators respectively.

### `ParserBridge0[+T]`
This trait is a special case for objects that should return themselves.
As an example, here is an object which forms part of a larger AST, say:

```scala mdoc
import parsley.generic.ParserBridge0
trait Expr
// rest of AST
case object NullLit extends Expr with ParserBridge0[Expr]
```

The `NullLit` object is part of the `Expr` AST, and it has also mixed in `ParserBridge0[Expr]`,
giving it access to `from` and `<#` only (no `apply` for this one!). What this means is that
you can now write the following:

```scala mdoc:to-string
val nullLit = NullLit <# "null"
nullLit.parse("null")
```

Without any further configuration, notice that the result of parsing `"null"` is indeed `NullLit`,
and `nullLit: Parsley[Expr]`.

@:callout(error)
Be aware that the type passed to the generic parameter cannot be itself:

```scala mdoc:fail
case object Bad extends ParserBridge0[Bad.type]
```

Resolving this will require introducing an extra type, like `Expr` in the example with
`NullLit`, which breaks the cycle sufficiently.
@:@

### `ErrorBridge`
The `ParserSingletonBridge` extends the `ErrorBridge` trait, which is shaped as follows:

```scala
trait ErrorBridge {
    def labels: List[String] = Nil
    def reason: Option[String] = None

    // applies the above labels and reason if applicable
    final protected def error[T](p: Parsley[T]): Parsley[T]
}
```

This allows all derived bridges to specify labels to refer to the parser if it fails, as well as
a reason why it might be needed. This is useful because, anecdotally, I've found that ~70% of
instances of the `label` combinator occur attached directly to a bridge application; and this
therefore allows for error annotation to kept away from the main parser description.

## Additional Use Cases
Other than the natural decoupling that the bridges provide, there are some more specialised
uses that can come out of the generic bridges alone.

### Normalising or Disambiguating Data
Occasionally, the shape of an AST can change internally even though the syntax of the
language being parsed does not. Bridges are perfectly suited for handling these internal
changes while masking them from the parser itself. As an example, assume that a `Let`
AST node was *previously* defined as follows:

```scala mdoc:invisible
trait Binding
```
```scala mdoc
case class Let(bindings: List[Binding], body: Expr)
object Let extends ParserBridge2[List[Binding], Expr, Let]
```

The parser, therefore, can be expected to produce lists of bindings to feed in. However,
later it was decided that the ordering of the bindings doesn't matter, so a `Set` is being
used. The decoupling of the bridge will allow for this change to happen without changing the
parser, so long as the bridge performs the "patching":

```scala mdoc:nest
case class Let(bindings: Set[Binding], body: Expr)
object Let extends ParserBridge2[List[Binding], Expr, Let] {
    def apply(bindings: List[Binding], body: Expr): Let = Let(bindings.toSet, body)
}
```

By defining the appropriate "forwarding" in its own `apply`, the bridge has ensured the
parser will still work.

@:style(paragraph) Handling ambiguity @:@
Another use of this kind of bridge is to allow for the disambiguation of two syntactically
similar structures. As an example, consider Scala's tuple syntax:

```scala mdoc:silent
val x  = (6)
val xy = (5, 6)
```

It is clear to us that `x` is not a "singleton pair", whatever that would be, but a parenthesised
expression; on the other hand, `xy` is clearly a pair. The problem is that the syntax for these
overlap, requiring backtracking to resolve (you can only know if you're parsing a tuple when
you find your first `','`).

In practice, arbitrary backtracking in a parser can impact performance
and the quality of error messages. Instead of dealing with this ambiguity by backtracking, it
is possible to exploit the shared structure in a *Disambiguator Bridge*: this is just a bridge
that looks at the provided arguments to decide what to make. For example:

```scala mdoc
import cats.data.NonEmptyList

case class Tuple(exprs: NonEmptyList[Expr]) extends Expr

object TupleOrParens extends ParserBridge1[NonEmptyList[Expr], Expr] {
    def apply(exprs: NonEmptyList[Expr]): Expr = exprs match {
        case NonEmptyList(expr, Nil) => expr
        case exprs                   => Tuple(exprs)
    }
}
```

In the above example, the parser will parse one or more expressions (signified by the `NonEmptyList`
from `cats`); the bridge will then inspect these expressions, returning a single expression if
only one was parsed, and construct the `Tuple` node otherwise. In the parser, this would just look
something like:

```scala mdoc:to-string
// from `parsley-cats`, produces `NonEmptyList` instead of `List`
import parsley.cats.combinator.sepBy1
val tupleOrParens = TupleOrParens("(" ~> sepBy1(nullLit, ",") <~ ")")
tupleOrParens.parse("(null)")
tupleOrParens.parse("(null,null)")
```

### Enforcing Invariances
So far, the bridges we've seen have been altering the way that the data itself should
be constructed from the results. However, it may also be desirable to override the
*templated* `apply` to perform additional checks. This basically means that you
can add in a `filter`-like combinator after the data has been constructed to validate
that the thing you've constructed is actually correct. As an example, it turns out that
Scala only allows tuples with a maximum of 22 elements:

```scala mdoc:fail
val oops = (1, 2, 3, 4, 5, 6, 7, 8, 9, 0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 0, 1, 2, 3)
```

How to adjust the parser to handle this? One possible approach is to use the `range` combinator:

```scala
def nonEmptyList[A](px: Parsley[A], pxs: Parsley[List[A]]) =
    lift2(NonEmptyList(_, _), px, pxs)
val tupleOrParensObtuse =
    TupleOrParens("(" ~> nonEmptyList(nullLit, range(0, 21)("," ~> nullLit)) <~ ")")
```

This works, but it's very obtuse. Not to mention that the error message generated isn't particularly
good. @:todo(TODO: add error message?) Instead, we can hook some extra behaviour into the generated `apply`:

```scala mdoc:nest
import parsley.errors.combinator._

object TupleOrParens extends ParserBridge1[NonEmptyList[Expr], Expr] {
    def apply(exprs: NonEmptyList[Expr]): Expr = exprs match {
        case NonEmptyList(expr, Nil) => expr
        case exprs                   => Tuple(exprs)
    }

    override def apply(exprs: Parsley[NonEmptyList[Expr]]): Parsley[Expr] =
        super.apply(exprs).guardAgainst {
            case Tuple(exprs) if exprs.size > 22 =>
                Seq(s"tuples may not have more than 22 elements, but ${exprs.size} given")
        }
}
```

This bridge invokes the templated `apply` with `super.apply` first, then after processes it
with the `guardAgainst` combinator to generate a bespoke message:

```scala mdoc:invisible
val tupleOrParens = TupleOrParens("(" ~> sepBy1(nullLit, ",") <~ ")")
```
```scala mdoc:to-string
tupleOrParens.parse("(null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null)")
```

To be clear, this is using the original definition of `tupleOrParens` and *not* `tupleOrParensObtuse`.

@:callout(warning)
While this use of bridges does retain the unsaturated application from `ParserSingletonBridge`, using the
`from` combinator will not perform the additional validation: be careful!
@:@

@:todo(Talk about the error messages when that's added in 5.0?)

## When *not* to use

Simply put, generic bridges have one major limitation: they cannot interact with additional metadata
that might be required in the parser. One excellent example of this is position information. While
`parsley` could take a stance on how this should be done, I'd prefer if the users can make that
decision for themselves. The previously linked tutorial demonstrates how to *make* templating bridges
from scratch, which you would need to do to support something like position tracking.
