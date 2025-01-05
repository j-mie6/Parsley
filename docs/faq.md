{%
laika.title = FAQ
laika.versioned = true
laika.site.metadata.description = "This page covers frequently asked questions."
%}

# Frequently Asked Questions

### What is the deal with "lazy" vs "strict" positions?
In `parsley-4.x.y` and up, combinators are no longer totally lazy and adopt the
idea of lazy and strict arguments. In general, all combinators have strict
receivers.

* A strict argument/receiver indicates that the combinator intends for that
  argument to be parsed "immediately" without a possibility of consuming input
  before-hand
* A lazy argument/receiver indicates that the combinator acknowledges that
  input may be consumed by another argument (or the combinator itself) before
  the lazy argument will be executed.

Why is this important? Recursive parsers are implemented in Parsley with
"knot-tying" using `lazy val`. When a parser appears recursively in its own
definition, it needs to be kept lazy otherwise it will be evaluated during its
own definition, leading to an infinite loop. Lazy positions in combinators
indicate probably safe places for recursion to occur. On the other hand, the
absence of input-consumption before a strict position _probably_ indicates that
recursion in that position will result in a _left-recursive_ parser: the
necessary condition for left-recursion is that a recursive call is made without
having consumed input since the last recursive call. Technically, this is known as _unguarded_ (by input consumption) recursion, with correct parsers making use of _guarded recursion_. This is not bullet-proof:

* Strict positions sometimes occur in combinators where they _should_ be lazy
  but cannot be due to language limitations: `choice`, the `zipped` syntax, etc
  all fall victim to this.
* Lazy positions suggest the previously executed parsers _could_ have consumed
  input, but it is not guaranteed that they actually _do_. As an example,
  `LazyParsley.unary_~` can be used to make a parser lazy in an otherwise
  strict position, but doesn't itself consume input, so care must still be taken to ensure there is input consumed by previous arguments!

Note that a strict parser in a lazy position is still lazy!

For an example, `lazy val q = (p <~ ':', q).zipped(f)` would be problematic
because the recursive call is used naked in a totally strict combinator
(`zipped`). There are two ways to fix this problem:

1. Reshuffle the surrounding context to move it into a lazy position:
   `lazy val q = (p, ':' ~> q).zipped(f)` would be fine, since the right-hand
   side of `~>` is a lazy position guarded by the input consumption of `':'`.
2. Use `LazyParsley.unary_~` to introduce laziness:
   ```scala
   import parsley.Parsley.LazyParsley
   lazy val q = (p <~ ':', ~q).zipped(f)
   ```
   Here, the `unary_~` is used to just make `q` lazy: this is simply defined
   as `unit ~> q`, which places `q` into a _unguarded_ lazy position, since
   `unit` does not consume input. The guardedness in this case comes from `p <~ ':'`, which is guaranteed to consume a `:` on success.

## Frequently Encountered Problems

### My parser seems to infinite loop and doesn't run, even with the `debug` combinator
This sounds like you've run into the above issue with _left-recursion_. This
means that a parser appears as the first possible route of exploration from
itself:

```scala
lazy val bad = bad ~> ...
lazy val badExpr = Add.lift(badExpr, '+' ~> badExpr) | number
lazy val goodExpr = atomic((number <~ '+', goodExpr).zipped(Add)) | number
```

The first two parsers are both examples of left-recursive parsers, which are
not allowed in Parsley. The third is caused by the above problem of a genuine
recursive call in a strict position, and can be fixed with `unary_~` or by
rearranging the `'+'` parser as above:

```scala
lazy val goodExpr = atomic((number, '+' ~> goodExpr).zipped(Add)) | number
lazy val goodExpr = atomic((number <~ '+', ~goodExpr).zipped(Add)) | number
```

### My parser seems to infinite loop and the `debug` combinator shows it spinning forever
As above you've probably made a _left-recursive_ parser, but this time hidden
the bad recursion inside an _unguarded_ lazy position:

```scala
import parsley.Parsley.LazyParsley
lazy val badExpr = Add.lift(~badExpr, '+' ~> badExpr) | number
```

Perhaps you tried to fix the above bad parser using `unary_~` even though its
an unsafe recursive position?

### My parser throws a `BadLazinessException`, what gives!
This error is thrown by the Parsley compiler when Scala would otherwise have thrown a
`NullPointerException` during what's known as "let-finding". This is the very first phase of the
pipeline, and, since Parsley does not use `null` _anywhere_ in the front-end, its a symptom of a
parser having been demanded before its actually been defined. So what does that mean for you, the user?

Well, unfortunately, Scala doesn't give us any indication about which parser is at fault (and the
Java 14 Helpful NPEs don't help much either). But here are the possible causes:

1. A parser references one that has been defined below it and it hasn't been marked as `lazy val`
2. A combinator doesn't have the right laziness characteristics (conventionally, Parsley defines
   all its combinators arguments using by-name parameters and `lazy val`s for those that appear
   multiple times in the body)
3. Be careful: builder combinators to abstract position tracking (as per the Parsley guide) _also_
   will require the same care as (2)

Now, the solution to (1) is simple, just either add a `lazy val`, or reorder the grammar clauses so
that there is no forward referencing. Similarly, (2) is simple to fix by adding in the right
laziness to the parameter. However, because Parsley is careful to make as much as possible lazy (be
careful of `parsley.syntax.zipped.{Zipped2, Zipped3}`, however, neither of them are lazy!) you
may find that you can define an entire parser without ever running into this problem, even if nothing
is marked `lazy`: lucky you! My advice is to try and keep things ordered nicely, or mark everything
as lazy; of course, laziness will have a slight runtime penalty, so its worth seeing how much of the
laziness you can eliminate by just reordering.

You may be helped out by Scala here, either by an error about "crossed-initialisation" or a warning
about "Reference to uninitialized value". If you see either of these, check the order and laziness
of the parsers concerned!
