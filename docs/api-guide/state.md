{%
laika.versioned = true
laika.title = "`parsley.state`"
parsley.tabname = "Context-Sensitive Parsing (parsley.state)"
laika.site.metadata.description = "This page describes how to thread custom state through a parser."
%}

```scala mdoc:invisible
import parsley.Parsley
```

# Context-Sensitive Parsing (`parsley.state`)
Normally, context-sensitive parsing can be done with monadic `flatMap`. However,
in `parsley`, `flatMap` is a very expensive operation and is best avoided.
Instead, `parsley` supports a form of arbitrary state threading called *references*. These can be used to perform context-sensitive parsing in a
more performant way at a cost to expressive power.

@:callout(info)
*The Scaladoc for this page can be found at [`parsley.state`](@:api(parsley.state$)).*
@:@

## References
A reference is a single piece of mutable state threaded through a parser.
They can be made in three different ways:

1.  Importing `parsley.state.RefMaker` allows the use of the `makeRef`
    method on any type `A`:

    ```scala
    def makeRef[B](body: Ref[A] => Parsley[B]): Parsley[B]
    ```

    This will construct a new reference filled with the receiver value, and this can be used within the scope of the given continuation. Everytime this is
    executed, it will be uniquely scoped (when the parser is recursive).
2.  Similarly, importing `parsley.state.StateCombinators` allows the use of
    the `fillRef` combinator on `Parsley[A]`, which has the same signature as
    `makeRef`.

    It behaves similarly to `makeRef`, but sources its result from a parser.
    Loosely: `p.fillRef(body)` is the same as `p.flatMap(_.makeRef(body))`,
    but is much more efficient.
3.  The `Ref.make[A]: Ref[A]` method allows for the creation of a reference
    *outside* of the parsing context. This is not recommended, as it will
    not guarantee uniqueness of scoping. The state will **not** be initialised.

    @:callout(warning)
    Using the same globally constructed reference in two different parsers (in terms of calling
    `.parse` on them) is undefined behaviour and should *not* be done.
    @:@

References themselves have two core operations, with several more built on top:

```scala
class Ref[A] {
    def get: Parsley[A]
    def set(p: Parsley[A]): Parsley[Unit]
}
```

The `get` method will read the reference at parse-time and return the value
contained within. The `set` method takes the result of a parser and stores
that into the reference. As examples:

```scala mdoc:to-string
import parsley.character.item
import parsley.state._

List.empty[Char].makeRef { r1 =>
    item.fillRef { r2 =>
        r1.set(r2.get <::> (r2.get <::> r1.get))
    } *> r1.get
}.parse("a")
```

The above example fills a reference `r1`, with the empty list, then fills
a second reference `r2` with the result of parsing any character. The value
in `r1` is updated with the list obtained by prepending the parsed character onto
the empty list stored in `r1` twice. After `r2` goes out of scope, the current
value of `r1` is returned.

### Persistence
In the above example, the reference `r2` is only used for `get`, but is used
multiple times. Normally, using the value of a parser more than once requires
a `flatMap`; this is not the case in `parsley` when using references. To make
this application more ergonomic, `parsley.state.StateCombinators` also
exposes the `persist` combinator:

```scala mdoc:to-string
List.empty[Char].makeRef { r1 =>
    item.persist { c =>
        r1.set(c <::> (c <::> r1.get))
    } *> r1.get
}.parse("a")
```

Persist can be thought of as a composition of `fillRef` and `get`, or
alternatively as a composition of `flatMap` and `pure`.

#### Using Persistence
One use of `persist` is to otherwise reduce the scope of an expensive `flatMap`:
the `flatMap` combinator is expensive because it has to process the body of the
function in full everytime it is executed, if the size of the body is reduced,
that will keep the parser faster. Currently, there is no primitive functionality
for parsing with respect to values inside references, like so:

```scala mdoc:silent
def string(r: Ref[String]): Parsley[String] = r.get.flatMap(parsley.character.string(_))
```

The scope of the `flatMap` in that combinator is small, however, so is likely
much more efficient than one that didn't use persistence. With this, the
context-sensitive parsing of XML tags can be done:

```scala mdoc:to-string
import parsley.Parsley.{atomic, notFollowedBy}
import parsley.character.{stringOfSome, letter}
import parsley.combinator.optional
import parsley.syntax.character.{charLift, stringLift}

val openTag = atomic('<' <~ notFollowedBy('/'))
val tagName = stringOfSome(letter)

lazy val content: Parsley[Unit] = optional(tag)
lazy val tag: Parsley[Unit] = (openTag ~> tagName <~ '>').fillRef { name =>
    content <~ ("</" ~> string(name) <~ ">")
}

tag.parse("<hello></hello>")
tag.parse("<hello></hi>")
tag.parse("<a><b></b></c>")
```

### Long-Term State
Persistence is an example of read-only state used to preserve a value for
later. Writable state can also be used for context-sensitive tasks, by tracking
a value over time. Examples include whitespace-sensitivity or tracking matching
brackets.

#### Matching Brackets
The `setDuring` and `updateDuring` combinators can be used to reset state after a specific context is
executed. They can be built primitively out of `get`, `set`, and `persist`. The
following parser reports the position of the last unclosed bracket that is
well-interleaved with the other kinds of brackets.

```scala mdoc:to-string
import parsley.Parsley.{eof, many}
import parsley.character.char
import parsley.errors.patterns.VerifiedErrors
import parsley.position.pos

case class Brackets(open: Char, position: (Int, Int)) {
    def enter(c: Char, p: (Int, Int)) = {
        val (line, col) = p
        // adjust the column, because it was parsed post-bracket
        this.copy(open = c, position = (line, col-1))
    }
    def missingClose = s"unclosed $open originating at $position"
}
object Brackets {
    def empty = Brackets(0, null) // this will never be matched on
    def toOpen(c: Char) = c match {
        case ')' => '('
        case ']' => '['
        case '}' => '{'
    }
}

def brackets = Brackets.empty.makeRef { bs =>
    def open(c: Char): Parsley[Unit] =
        char(c) ~> bs.update(pos.map[Brackets => Brackets](p => _.enter(c, p)))
    def close(c: Char): Parsley[Unit] =
        char(c).void | bs.get.verifiedExplain(_.missingClose)

    // ensure it is reset
    def scope[A](p: Parsley[A]): Parsley[A] = bs.updateDuring(identity[Brackets])(p)

    lazy val matching: Parsley[Unit] = scope {
        many(
              open('(') ~> matching <~ close(')')
            | open('[') ~> matching <~ close(']')
            | open('{') ~> matching <~ close('}')
        ).void
    }
    matching <~ eof
}
```

The above parser is designed to report where the last unclosed bracket
was. It creates a reference `bs` that stores a `Brackets`, which tracks
the last open character and its position. Then, whenever a bracket is
entered, `matching` will save the existing information using the `updateDuring`
combinator: giving it the `identity` function will mean it will simply restore
the existing state after it returns. Whenever an open bracket is parsed, it
will write its position into the state (lagging by one character), and then
if the corresponding closing bracket cannot be parsed, it will use an
unconditional [*Verified Error*][*Verified Errors*] to report a message based on the last opened bracket. The results are below:

```scala mdoc:to-string
val p = brackets

p.parse("()()()")
p.parse("[][][]")
p.parse("{}[]()")
p.parse("[[[[[]]]]]")

p.parse("([)]")
p.parse("({(")
p.parse("()[]{[(){}}")
```

Given the relatively simple construction, it works
quite well, and efficiently too: no `flatMap` necessary!

#### Tail-Recursive Combinators
When combinators can be implemented tail recursively instead of
recursively, they can be more efficient. In the context of `parsley`,
tail-recursive combinators are ones which only return the result
of the last recursive call they make:

```scala
lazy val tailRec: Parsley[Unit] = 'a' ~> tailRec | unit
```

The above is tail recursive, for instance. Combinators like `skipMany`
are implemented tail recursively, with additional optimisations to make
them more efficient: implementing new combinators in terms of `skipMany`
with references to carry state is likely to be efficient. For example:

```scala mdoc:silent
def setOf[A](p: Parsley[A]): Parsley[Set[A]] = {
    Set.empty[A].makeRef { set =>
        many(set.update(p.map[Set[A] => Set[A]](x => _ + x))) ~> set.get
    }
}
```

In the above code, a set is carried around in a reference, and a new element
is added into this set every iteration. When the loop completes (successfully), the set in the reference is returned. A more efficient implementation, however,
would use `persist` and a mutable set (along with `impure` and `fresh`): this, of course, still uses a reference.

#### Whitespace-Sensitive Languages
Another application of long-term state is to track indentation levels in
a whitespace-sensitive language: here the start of a new cause, statement
or block will record the current column number, and further statements
must verify that the column number is correct. Leaving a block will restore
the identation level back to how it was before. This is similar to how
matching brackets worked.

## Stateful Combinators
For some applications, a more structured strategy for tracking state can be
useful. The `forP` combinators allow for looping where the a stateful variable
is used to control the control flow. For instance, the classic context-sensitive
grammar of `a^n b^n c^n` can be matched effectively using a reference and a `forP`:

```scala mdoc:to-string
import parsley.Parsley.pure
import parsley.state.forP
val abcs = 0.makeRef { i =>
    many('a' ~> i.update(_ + 1)) ~>
    forP[Int](i.get, pure(_ > 0), pure(_ - 1)) {
        'b'
    } ~>
    forP[Int](i.get, pure(_ > 0), pure(_ - 1)) {
        'c'
    } ~>
    i.get <~ eof
}

abcs.parse("aabbcc")
abcs.parse("aaaaabbbbbccccc")
abcs.parse("aaaabbbbbbcccc")
```

First, as many `a`s as possible are read and each one will increment the counter
`i`. Then, run the equivalent of a C-style: `for (int j = i, j > 0, j -= 1)` reading `b` and `c`. Internally, `forP` will use a reference to track its state.
