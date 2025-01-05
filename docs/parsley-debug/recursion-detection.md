# Detecting Left Recursion
Left recursion is an issue that plagues recursive-descent parsing. Whenever a parser
(eventually) performs itself as the first thing it does, this will infinite loop and
the parser will not terminate. While `parsley` has been designed in a way that aims to
statically rule out the more obvious left-recursive definitions, it is still possible to
indirectly write these parsers. The `parsley-debug` library provides functionality to
help track down the sources of such issues at runtime when provided with an input known
to loop.

@:callout(info)
Please see the [`@parsley.debuggable`](debuggable.md) page first, as this is used
to provide meaningful names for this functionality.
@:@

The combinator we are interested in is called `parsley.debug.combinator.detectDivergence`, which
wraps the top-level parser.

```scala mdoc:invisible
class debuggable extends scala.annotation.StaticAnnotation()
```

```scala mdoc
import parsley.quick.*
//import parsley.debuggable
import parsley.debug.combinator.detectDivergence

@debuggable
object parsers {
    lazy val top: Parsley[Unit] = p.void // simplifies the cycle, see #247
    lazy val p: Parsley[Unit] = char('a').void | s ~> q
    lazy val q: Parsley[Unit] = sepBy1(r, string(",")).void
    lazy val r: Parsley[Unit] = many(p).void
    lazy val s: Parsley[Unit] = unit
}
```
```scala mdoc:invisible
parsley.debug.util.Collector.registerNames(Map(parsers.p -> "p", parsers.q -> "q", parsers.r -> "r"))
```

While `parsers.top.parse("a")` would work fine, consider what happens if we pass the empty string:

```scala mdoc:crash
detectDivergence(parsers.top).parse("")
```

Thankfully, the `detectDivergence` combinator can detect that this will cause an issue, and provides
some insight into what that might be. If you follow that trace out carefully, you'll see that these
four calls form a cycle where no input is consumed before you get back to the start again: this is
what causes left-recursion.

## Looping vs Recursion
Divergence is a property that is more general than just left-recursion. In fact, some combinators
can more accurately iteratively "loop" forever. One such case is as follows:

```scala mdoc:crash
detectDivergence(many(unit)).parse("")
```

The `many` combinator reads zero-or-more things. When given something that doesn't consume input,
it will succeed reading that parser, then loop back round and try to read it again. This
is, as you might expect, exactly where it left off last iteration. As such, `detectDivergence` can
also detect this kind of looping as well. While `parsley` implements `many` and friends more
efficiently, in theory this is still left-recursion, it is just called *hidden left-recursion*:

```scala
def many(p: Parsley[A]): Parsley[List[A]] = {
    lazy val rec: Parsley[List[A]] = p <::> rec | pure(Nil)
    rec
}
```

When `p` does not succeed having consumed input, `rec` will perform itself again from the same
input point -- this is left-recursion, even if `p` is "in the way".

## Stateful Parsers
It is "easy" to detect that a parser is looping if the only state in the parser is the input --
if we get back to the same place with no input consumed we will most certainly repeat that forever.
However, `parsley.state` gives us access to mutable pieces of state threaded through a parser.
This adds an additional complication that makes *static analysis* of parser divergence more
intractible. However, the `detectDivergence` combinator is a *runtime analysis*, and so has access
to the state to track what's going on. This means it is able to detect more complex looping that
involves `Ref`:

```scala mdoc
import parsley.quick.*
//import parsley.debuggable
import parsley.debug.combinator.detectDivergence
import parsley.state.*

@debuggable
object stateful {
    val toggle = Ref.make[Boolean]
    val counter = Ref.make[Int]
    lazy val p: Parsley[Unit] = counter.update(_ + 1) ~> ifS(counter.gets(_ == 10), counter.set(0) ~> q, p)
    lazy val q: Parsley[Unit] = toggle.update(!_) ~> p
    lazy val top = toggle.set(false) ~> counter.set(0) ~> p
}
```
```scala mdoc:invisible
parsley.debug.util.Collector.registerNames(Map(stateful.p -> "p", stateful.q -> "q", stateful.top -> "top"))
```

This parser is a contrived example, admittedly, but illustrates what `detectDivergence` is capable of.
The parser `top` runs in the presence of two `Ref`s: `p` will loop until the counter reaches `10`, when
it will reset it and execute `q` one time. `q` flips the `toggle`, but then does `p`. With some thought,
you might be able to identify the cycle that will happen here:

```scala mdoc:crash
detectDivergence(stateful.top).parse("")
```

Unfortunately, at the moment there is no way to name the references, but this is future work. Regardless,
this time, the cycle shows the state snapshots that lead to the loop in the parser; namely, when
the counter hits `0` again and the toggle is reset to `false`, we enter our initial state, and the
parser is killed.

Obviously, if this were just an infinite loop of the form `lazy val p: Parsley[Unit] = counter.update(_ + 1) ~> p`, then `detectDivergence` cannot detect this until `counter` overflows! The system isn't fool proof,
obviously, since this would mean solving the general halting problem, but it works for any non-stateful
parser and stateful parsers which repeat the same stateful behaviour.

## Limitations
As you may have noticed in the above example with `many`, sometimes there may be a lack of information
about the naming of certain parsers (which can help pin-point where a problem arose from). Obviously,
the `@parsley.debuggable` annotation is meant to help with this, but it does not necessarily have
visibility over *all* parsers! Let's see where this might go wrong:

```scala mdoc
@debuggable
object nameless {
    val top = false.makeRef { r =>
        lazy val p: Parsley[Unit] = r.update(!_) ~> p
        p
    }
}
```
```scala mdoc:invisible
parsley.debug.util.Collector.registerNames(Map(nameless.top -> "top"))
```

While we might expect this to work out much like the `stateful` example above, in reality we see
this:

```scala mdoc:crash
detectDivergence(nameless.top).parse("")
```

Unfortunately, the annotation cannot see into nested definitions, only top-level ones. In this case,
we can use the `named` combinator from `parsley.debug.combinator` to explicitly name `p` (via `named(p, "p")`), or you may be able to get away with something like:

```scala mdoc
@debuggable
class Nested(r: Ref[Boolean]) {
    lazy val p: Parsley[Unit] = r.update(!_) ~> p
}

@debuggable
object nameful {
    val top = false.makeRef(r => new Nested(r).p)
}
```

However, this is remarkably clumsy to use in practice (please don't)!
