{%
laika.versioned = true
laika.site.metadata.description = "How combinators work at a basic level."
%}

```scala mdoc:invisible
import scala.annotation.unused
```

# Basics of Combinators

Parsley is a _parser combinator_ library. In contrast to a parser generator library, like ANTLR,
this allows the users to build their parsers as part of the _host_ language: in this case Scala.
This is called being an _embedded Domain Specific Language_ or eDSL. Practically, this is useful
because it allows you to factor out repeated parts of your grammars and make them reusable, as well
as using all the language features normally at your disposal to create the grammars too. This page
will touch on both of those ideas. Another advantage is that there is less boiler-plate compared
with _some_ parser generators: you don't need to convert between the AST the generator produces and
your own, you can parse straight into the desired type.

## Basic Combinators and Sequencing
We'll start really basic: just reading a character or two and seeing how to combine the results
using _combinators_. For a first look, we will just parse one of any character. If you are familar
with regex, this would match the pattern `(.)`.

```scala mdoc:silent
import parsley.Parsley
import parsley.character.item

val p: Parsley[Char] = item
```
```scala mdoc:height=2
p.parse("a")
p.parse("1")
p.parse("")
```

The @:api(parsley.Parsley) type is the type of parsers. The type parameter `Char` here represents what type
the parser will return when it has been executed using `parse(input)`. Soon we will see an
example with a different type. Parsers, when executed, return a @:api(parsley.Result)`[Err, A]` for whatever `A` the
parser returned: this is one of @:api(parsley.Success) containing the value or @:api(parsley.Failure) containing an error
message of type `Err` (by default this is `String`). This is the basic workflow when using parsers. The `item` parser will read any single
character, no matter what (so long as there is one to read). It isn't particularly useful though,
so lets match specific characters instead and parse _two_ of them this time. The regex for this
would be `(ab)`.

```scala mdoc:silent
import parsley.Parsley
import parsley.character.char

val ab: Parsley[(Char, Char)] = char('a') <~> char('b')
```
```scala mdoc:height=2
ab.parse("ab")
ab.parse("a")
```

A few new things have appeared in this new example.
The `char` combinator is new: given a specific character it will parse that character only.
We'll see how you can define this and `item` in terms of another, more general, combinator soon.
Notice that the type of `ab` is no longer just a `Parsley[Char]`, but a `Parsley[(Char, Char)]`:
this is due to the `<~>` (pronounced "zip") combinator with the following type (in a pseudo-syntax, for simplicity).

```scala
(_ <~> _): (p: Parsley[A], q: Parsley[B]) => Parsley[(A, B)]
```

What this combinator does (pronounced "zip") is that it first parses `p`, then `q` afterwards
and then combines their results into a tuple. Suppose we had `char('a') <~> char('b') <~> char('c')`
then this would have type `Parsley[((Char, Char), Char)]`. This is the first example of a sequencing
combinator. There are two other combinators that look similar:

```scala
(_ ~> _): (p: Parsley[A], q: Parsley[B]) => Parsley[B]
(_ <~ _): (p: Parsley[A], q: Parsley[B]) => Parsley[A]
```

They are pronounced `then` and `then discard` respectively. Again, they both parse both `p` and then
`q`, but they only return the result they are "pointing" at. Notice that `<~>` points at _both_
results. These are more widely known as `*>` and `<*`, but they are otherwise identical, so use
whatever resonates more strongly with you. We'll see soon how we can define them in terms of `<~>`
to get a sense of how combinators can be built up in terms of more primitive ones.

### What ties `char` and `item` together
We've seen both `char` and `item` can be used to read characters, there is, however, a more
primitive one which can be used to implement them both. This combinator is called `satisfy` and
has the following type:

```scala
def satisfy(predicate: Char => Boolean): Parsley[Char]

def char(c: Char): Parsley[Char] = satisfy(_ == c)
val item: Parsley[Char] = satisfy(_ => true)
```

The combinator `satisfy` takes a function, and will read a character when the predicate returns
`true` on that character, and fails otherwise. This makes `satisfy` a bit more versatile and it can
be used to implement a wide range of functionality. For example, we can implement a parser that
reads digits using `satisfy`:

```scala mdoc:silent
import parsley.Parsley
import parsley.character.satisfy

val digit = satisfy(_.isDigit)
```
```scala mdoc:height=2
digit.parse("1")
digit.parse("2")
digit.parse("a")
```

This is, however, already implemented by `parsley.character.digit`; `parsley` is very rich in terms
of the combinators it supports out of the box, so do hunt around before reinventing the wheel!

### Changing the result type
It's all well and good being able to sequence together reading single characters, but this doesn't
exactly scale well to larger, more complex, parsers. Indeed, it's likely we aren't interested in an
increasing deeply nested tuple! A good starting point for this is the humble `map` combinator:

```scala
_.map(_): (p: Parsley[A], f: A => B) => Parsley[B]
```

This can be used to change the result of a parser `p` with the parser `f`, presumably into something
more useful. Let's see a couple of examples of this in action! Firstly, let's suppose we wanted
our `digit` combinator from before to return an `Int` instead of a `Char`...

```scala mdoc:silent:nest
import parsley.Parsley
import parsley.character.satisfy

val digit: Parsley[Int] = satisfy(_.isDigit).map(_.asDigit)
```
```scala mdoc:height=2
digit.parse("1")
```

Here we can see that the digit parser is no longer type `Parsley[Char]` but type `Parsley[Int]`.
This is because the `asDigit` method on `Char` returns an `Int`. To reinforce how this works,
let's see how `~>` and `<~` can be made out of a combination of `<~>` and `map`:

```scala
p ~> q == (p <~> q).map(_._2)
p <~ q == (p <~> q).map(_._1)
```

The first definition pairs `p` and `q` together, and then takes the second element of the pair with
`map`, and the second definition does the same but instead takes the _first_ element of the pair.
Now, using this tupling approach paired with `map`, we can do a lot of stuff! However, there is a
more general strategy to do this:

```scala
def lift2[A, B, C](f: (A, B) => C, p: Parsley[A], q: Parsley[B]): Parsley[C]

// pairs p and q's results together
p <~> q = lift2[A, B, (A, B)]((_, _), p, q)
// adds the result of p onto the list result of ps
p <::> ps = lift2[A, List[A], List[A]](_ :: _, p, ps)
// applies a function from pf onto the value from px
pf <*> px = lift2[A => B, A, B]((f, x) => f(x), pf, px)
...
```

The `lift` _family_ of combinators are great for combining `n` parsers with an arity `n` function.
For instance, `map` is actually the same as a `lift1`. And above we can see that `lift2` can
implement a bunch of useful combinators. In particular, let's see how we can use `<::>` to implement
a way of reading `String`s instead of just `Char`s!

### Putting the pieces together: Building `string`
Our new challenge is going to be making an implementation of the `string` combinator. Obviously,
this combinator already exists in the library, so we can play around with it first to see how it
works:

```scala mdoc:silent
import parsley.Parsley
import parsley.character.string

val abc = string("abc")
```
```scala mdoc:height=2
abc.parse("abc")
abc.parse("abcd")
abc.parse("ab")
abc.parse("a bc")
abc.parse("dabc")
```

Notice how the result of the parser is a string. The `string` combinator reads a specific string
exactly. Here are a couple more examples to help you get your head around everything we've seen so
far:

```scala mdoc:height=2
import parsley.character.{char, string}

(string("abc") <~ char('d')).parse("abcd")
(string("abc") ~> char('d')).parse("abcd")
(string("abc") <~> char('d')).parse("abcd")
```

Now let's start building the `string` combinator from scratch! Bear in mind, that unlike in Haskell,
a Scala string is not `List[Char]` but is the Java `String`. This makes it a little more annoying to
implement, since we'll have to convert a `List[Char]` into a `String` at the end, with `map`.

```scala mdoc:nest
import parsley.Parsley

def string(str: String): Parsley[String] = {
    def helper(cs: List[Char]): Parsley[List[Char]] = ???
    helper(str.toList).map(_.mkString)
}
```
```scala mdoc:invisible
lazy val _ = string(""): @unused
```

We've started here by defining the `string` function, and made the skeleton of an internal helper
function that will turn a list of characters into a parser that reads that list of characters and
returns them all. The main body of the function uses this, and afterwards maps the `_.mkString`
method on lists to convert the result back into a string. Now we need to focus on the helper.
The first step is to consider how to handle the empty string. For this we need another very handy
combinator called `pure`, which reads no input and returns what's given to it:

```scala mdoc
import parsley.Parsley, Parsley.pure

// def pure[A](x: A): Parsley[A]
pure(7).parse("")

def helper(cs: List[Char]): Parsley[List[Char]] = cs match {
    case Nil    => pure(Nil)
    case _ :: _ => ???
}
```
```scala mdoc:invisible
val _ = helper(Nil): @unused
```

Now the question is how to handle the recursive case? Well in the base case we transformed the
empty list into a parser that returns the empty list. We'll follow that same shape here and use
`<::>`!

```scala mdoc:nest
import parsley.Parsley, Parsley.pure
import parsley.character.char

def helper(cs: List[Char]): Parsley[List[Char]] = cs match {
    case Nil     => pure(Nil)
    case c :: cs => char(c) <::> helper(cs)
}
```
```scala mdoc:invisible
val _ = helper(Nil): @unused
```

What happens here is that we take each character in the string, convert it to a parser that reads
that specific character, and then add that onto the front of reading the rest of the characters. In
full:

```scala mdoc
import parsley.Parsley
import parsley.character.char

def string(str: String): Parsley[String] = {
    def helper(cs: List[Char]): Parsley[List[Char]] = cs match {
        case Nil     => pure(Nil)
        case c :: cs => char(c) <::> helper(cs)
    }
    helper(str.toList).map(_.mkString)
}

// string "abc" == (char('a') <::> (char ('b') <::> (char 'c' <::> pure(Nil)))).map(_.mkString)
```
```scala mdoc:invisible
val _ = string("hi"): @unused
```

Hopefully, this gives some intuition about how we can start to sequence together larger and larger
building blocks out of smaller ones. It's also a lesson in how Scala can be used to help you build
your parsers up! Again, the `string` combinator is already provided to you (and optimised) so be
sure to check around in `parsley.character` and `parsley.combinator` for combinators that might
already fit your needs. That's about everything there is to say about sequencing and combining
results, so next up is looking at _choice_.

#### Takeaways
* Characters can be read using combinators found in `parsley.character`
* To sequence two things but only use the result of one, you'll want `*>/~>` or `<*/<~`
* The result of a parser can be changed using `map`
* `N` parser's results can be combined using the `liftN` combinators with an arity `N` function
* Larger combinators are built out of smaller ones using regular Scala functionality

## Choice and Handling Failure
Most practical parsers aren't just a straight line like `string` or reading a bunch of characters,
usually there are choices to be made along the way.

### Alternatives
When parsers fail to recognise some input, most of the time, there is an alternative branch that
could have been taken instead. Let's take a simple example again, say matching the regex `(a|b)`.
From now on, I'm going to use some syntactic sugar from `parsley.implicits` so I don't have to
write `char` or `string`.

```scala mdoc:silent
import parsley.Parsley
import parsley.syntax.character.charLift

val aOrB = 'a' <|> 'b'
```
```scala mdoc:height=2
aOrB.parse("a")
aOrB.parse("b")
aOrB.parse("c")
```

Here, the `<|>` combinator (pronounced "alt" or "or") allows the parser to try an alternative
branch when the first fails (and so on for a longer chain of them). To be well typed, the `<|>`
combinator requires both branches to return the same type (or at least a super-type of both). There is also a shorter way of writing this combinator,
called `|` - this doesn't work properly on the character literals though, for
obvious reasons.
For this specific usecase, `character.oneOf('a', 'b')` would probably have been more appropriate.

Let's carry on reinforcing the connections with what we've seen so far, and see how sequencing
and branching interact:

```scala mdoc:silent
import parsley.Parsley
import parsley.syntax.character.{charLift, stringLift}

val p = 'a' ~> ("a" | "bc") <~ 'd'

p.parse("bcd") // fails, there isn't an 'a'
p.parse("ad") // fails, why?
p.parse("aae") // fails, why?
p.parse("abcde") // what happens here, what does it return (and the type)?
p.parse("aad") // what happens here, what is the type it returns?
p.parse(" aad") // what about this
```

Think about the what results you expect from each of these, and then try them out in a REPL to
see if you're right: also check out the error messages from the failing ones! Recall that `string`
basically reads a bunch of characters in sequence, one after the other. Let's see what happens when
we put longer strings inside the branches:

```scala mdoc:silent:nest
import parsley.Parsley
import parsley.syntax.character.stringLift

val p = "abc" | "def" | "dead"
```
```scala mdoc:height=2
p.parse("abc")
p.parse("def")
p.parse("dead")
```

Ah, we have a problem! The first two alternatives parse fine, but the last one does not? The answer
to this is fairly simple, but I want to illustrate how we can make steps towards diagnosing this
problem ourselves using the combinators found in `parsley.debug`:

```scala mdoc:invisible
parsley.debug.disableColorRendering()
```
```scala mdoc:nest:silent
import parsley.Parsley
import parsley.syntax.character.stringLift
import parsley.debug._

val p = ("abc".debug("reading abc") |
            ("def".debug("reading def") | "dead".debug("reading dead")).debug("second branch")
        ).debug("first branch")
```

The `debug` combinator can be attached to any operation (by default Parsley associates `<|>` to the
right, which is why I've bracketed them this way round). It will provide printouts when it enters
the debug and when it exits, along with information about the state of the parser. Let's see the
three printouts:

```scala mdoc:to-string
p.parse("abc")
p.parse("def")
p.parse("dead")
```

Crucially, in the last printout, we can see the trace of the parser as it went wrong. It started
by executing the outermost branch, and tried to read `"abc"` and failed, but the caret is still
pointing at the first character. Then the second branch is taken as the alternative to the first:
this time the parser tries to read `"def"` and gets _two characters_ of the way through it before
failing at the `'a'`. Notice though, that the second branch immediately exited without attempting
the third alternative! This is the key: when a parser fails but has _consumed input_, the `|`
combinator will not work. The reason for this is to improve the quality of error messages, as well
as keeping parsers efficient. The "best" solution to this problem is to change our parser slightly
to remove the common leading string of the last two alternatives like so:

```scala mdoc:silent:nest
import parsley.Parsley
import parsley.syntax.character.{charLift, stringLift}

val p = "abc" | ("de" ~> ('f'.as("def") | "ad".as("dead")))
```
```scala mdoc:height=2
p.parse("abc")
p.parse("def")
p.parse("dead")
```

In this version of the parser, the leading `"de"` has been factored out, leaving an alternative
of `"f" | "ad"` remaining. But the original parser returned the full string, and this wouldn't:
it would return "abc", "f", or "ad". The `as` combinator (which can be written as `#>`) will replace the result
of parser on the left with the value found on the right (e.g. `"123".as(123) | "456".as(456)` would
be `Parsley[Int]`). You can think of it as a `map` with the constant function or as `p ~> pure(x)`.
Using `as` we can replace the results of the last two parsers with the results we expected from
before. This is the most efficient way of dealing with our problem, because this parser is still
linear time in the worst case. But sometimes this transformation isn't so straight-forward,
especially in deeply nested grammars. In this case we can reach for another, easier to read, tool.

### Backtracking
In the last section, we saw that the `|` doesn't proceed with the second alternative if the
first consumed input before failing. That is to say it doesn't _backtrack_. There is, however, a
combinator that allows `|` to backtrack in these circumstances, called `atomic`. Let's see it in action:

```scala mdoc:nest:to-string
import parsley.Parsley, Parsley.atomic
import parsley.syntax.character.stringLift
import parsley.debug._

val p = "abc" | atomic("def") | "dead"
val q = "abc" | (atomic("def".debug("reading def")).debug("backtrack!") |
                   "dead".debug("reading dead")).debug("branch")

p.parse("dead")
q.parse("dead")
```

Here we can see `atomic` in action, as well as a debug trace so you can see what's going on.
When we wrap the left hand side of a branch with `atomic`, when it fails we will rollback any
input it consumed, which then allows the branch to accept the alternative. We can see that in the
debug trace. You only need to use `atomic` where you know that two branches share a common leading
edge. Knowing when to do this is just based on practice. Adding an `atomic` never makes a parser
_wrong_, but it can make the error messages worse, and also excessive backtracking can increase
the time complexity of the parser significantly. If you know that if a branch consumes input and
fails then its alternatives wouldn't succeed either, then you shouldn't be using `atomic`. It is
also useful to make a specific sub-parser behave as if it were indivisible: think reading
keywords, which are all or nothing.

### Lookahead
Usually, a good ordering of your alternatives is most of what you need to write a functioning parser
for just about anything. However, every now and then it's convenient to look-ahead at what is to
come (in either a positive or a negative way): for instance checking if there is no remaining input
with the `eof` combinator is an example of negative look-ahead. There are two combinators for doing
this, which we'll explore now:

```scala mdoc:nest:to-string
import parsley.Parsley, Parsley.{notFollowedBy, lookAhead}
import parsley.character.item
import parsley.syntax.character.stringLift
import parsley.debug._

// def lookAhead[A](p: Parsley[A]): Parsley[A]
// def notFollowedBy(p: Parsley[_]): Parsley[Unit]

val eof = notFollowedBy(item)
val abcOnly = "abc" <~ eof

abcOnly.parse("abc")
abcOnly.parse("abcd")

val p = "abc".debug("abc") <~ lookAhead("!!".debug("!!")).debug("lookahead")

p.parse("abc!!")

p.parse("abc!")
```

Some key things to note here: the result of backtracking is always `()`. This is because the parser
has to fail for `notFollowedBy` to succeed, so it can't return the result of the look-ahead! On the
other hand, `lookAhead` can return the result of the parser that was ran. You can see from the
debug traces that when it succeeds, `lookAhead` does _not_ consume input, but if it fails having
consumed input, that input _remains consumed_. However, `notFollowedBy` never consumes input.

#### Takeaways
* Alternative branches of a grammar are encoded by `|` (also known as `<|>` or `orElse`)
* Within a branch, you are free to do whatever you want, but you must ensure both branches' types
  match
* When a branch fails having consumed input it won't take the second branch.
* The `atomic` combinator can be used to enable backtracking so that consumed input is undone when
  passing back through (it doesn't affect any `|`s that execute inside it, however)
* When parsers go wrong, `debug` is a fantastic tool to investigate it with: use it early and often!
* Negative and positive look-ahead can be done with `lookAhead` and `notFollowedBy`

## Interlude: Regex Parser Examples
Before we move on to slightly more realistic parsing problems that can't just be captured by
regex, we'll consolidate what we've seen so far by writing a few parsers for some regular
expressions. For all of these examples, I'll simplify it by using `void`, which ignores the result
of a parser and replaces it with `(): Unit`. This turns our parsers into _recognisers_. I'll
also be introducing a couple of new ideas so we can complete the functionality we need to properly
capture regex (namely the equivalents of optional `?`, zero-or-more `*` and one-or-more `+`). Let's
start there:

```scala mdoc:silent
// This is the regex *
// it will perform `p` zero or more times (greedily) and collect all its results into a list
def many[A](p: Parsley[A]): Parsley[List[A]] = ???
def skipMany(p: Parsley[_]): Parsley[Unit] =
    many(p).void // ideally, it wouldn't build the list

// This is the regex +
// similar to many, except it requires at least 1 `p` to succeed
def some[A](p: Parsley[A]): Parsley[List[A]] = p <::> many(p)
def skipSome(p: Parsley[_]): Parsley[Unit] = p ~> skipMany(p)

// This is the regex ?
// it will either parse `p` or will return `x` otherwise
def optionally[A](p: Parsley[_], x: A): Parsley[A] = p.as(x) <|> pure(x)
def optional(p: Parsley[_]): Parsley[Unit] = optionally(p, ())
def option[A](p: Parsley[A]): Parsley[Option[A]] =
    p.map(Some(_)) | pure(None)

// This is the regex [^ .. ]
// it will parse any character _not_ passed to it
def noneOf[A](cs: Char*): Parsley[Char] = satisfy(!cs.contains(_))
```
```scala mdoc:invisible
lazy val _ = some(skipSome(optional(option(noneOf())))): @unused
```

With the exception of `many`, which we can't define just yet, all of these handy combinators are
implemented with everything we've seen so far. You can find them all, and many more, in
`parsley.combinator`.

```scala mdoc:silent:reset
import parsley.Parsley, Parsley.{atomic, eof, many, some}
import parsley.combinator.optional
import parsley.syntax.character.{charLift, stringLift}
import parsley.character.{noneOf, oneOf, item}

// regex .at
val r1: Parsley[Unit] = item ~> "at".void

// regex [hc]at
val r2: Parsley[Unit] = oneOf('h', 'c') ~> "at".void

// regex [^hc]at
val r3: Parsley[Unit] = noneOf('h', 'c') ~> "at".void

// regex [hc]at$
val r4: Parsley[Unit] = oneOf('h', 'c') ~> "at" ~> eof

// regex s.*
val r5: Parsley[Unit] = 's' ~> many(item).void

// regex [hc]?at
val r6: Parsley[Unit] = optional(oneOf('h', 'c')) ~> "at".void

// regex [hc]+at
val r7: Parsley[Unit] = some(oneOf('h', 'c')) ~> "at".void

// regex h(i|ello|ey)( world)?(\!|\.)?
val r8: Parsley[Unit] = 'h' ~> ("i" | atomic("ello") | "ey") ~>
                        optional(" world") ~>
                        optional('!' <|> '.').void
```

Have a play around with those in a REPL and make sure you understand how they work and what inputs
they will succeed on. The `void` combinator discards the result of a
parser and makes it `Unit`.

## Recursive Context-Free Parsers
Everything we've seen so far has been as powerful as a regular expression. While regular expressions
are certainly useful for writing lexers they are normally not powerful enough to parse the syntax
of a programming language. It's worth nothing here that, _usually_, parser combinators don't make
a distinction between lexing and parsing: you build your lexers out of the combinators and then use
them in the parser. That has some advantages, but it does mean that backtracking is more expensive
than it would otherwise be in a parser with a dedicated lexing phase. The reason this is considered
good style is because of the higher-order nature of parser combinators: parsers are a first-class
value that can be manipulated freely by the combinators, as opposed to more rigid grammar rules and
terminals.

In this section, we'll explore how to extend the knowledge we've already built to start writing more
complex parsers with recursive control-flow: this is sufficient to parse context-free grammars. It
just takes the addition of the `flatMap` combinator to recover context-sensitive grammars from this,
but grammars that require `flatMap` are rare in practice, so I won't touch on it here.

### Recursion via Laziness
Writing recursive parsers is, fortunately, quite straight-forward but it does rely on Scala's _lazy
value_ feature. Let's start with the classic matching brackets parser (which cannot be parsed
with regex:
[here](https://stackoverflow.com/questions/1732348/regex-match-open-tags-except-xhtml-self-contained-tags)
comes to mind...):

```scala mdoc:silent
import parsley.Parsley, Parsley.{eof, many}
import parsley.syntax.character.charLift

lazy val matching: Parsley[Unit] = many('(' *> matching <* ')').void
val onlyMatching = matching <* eof

onlyMatching.parse("") // succeeds
onlyMatching.parse("(") // fails
onlyMatching.parse("()") // succeeds
onlyMatching.parse("()()()") // succeeds
onlyMatching.parse("(((())))") // succeeds
onlyMatching.parse("(((()))()()(()))") // succeeds
onlyMatching.parse("(((()))(()()(()))") // fails
```

There's a bit to unpack here! Firstly, the type ascription here on `matching` isn't optional: when
we write recursive parsers, at least one thing in the recursive _group_ needs to have a type, since
Scala cannot infer the types of recursive functions (or in this case values). The `lazy` keyword
here makes the `matching` parser only evaluated on demand. In this case that will be in the
`onlyMatching` parser, which is only a `val`. The reason this is important is that it means that
the reference to `matching` _inside_ the parser isn't forced immediately, causing an infinite loop.
That being said, every combinator in Parsley is defined using lazy function arguments (including a
lazy `this` for methods), so sometimes it isn't actually necessary to get the right behaviour.

My advice is to use `lazy val` whenever you do write a parser that references itself, even
indirectly. If your parser infinite loops before running it, you'll know you've missed one: but
there are other, more obscure symptoms. Laziness is also important when you need to forward reference
another parser. Here is an example:

```scala
lazy val q = ??? ~> p
lazy val p = ???

// vs

val p = ???
val q = ??? ~> p
```

Usually, the parsers can be re-ordered so that their definitions do not "cross over" each other
in the words of scalac. But when writing recursive parsers with multiple parts you may need to use
`lazy val` to get scalac to accept the ordering. This can also depend on whether or not they are
defined locally in a function or as attributes of a class. Indeed, the above example is fine if
`p` and `q` are both attributes of a class, even without `lazy val`!

**Much** more importantly, however, is noticing that when parsers _are_ recursive, they absolutely
**must** be references (i.e. `val`). A recursive parser using `def` _will_, without question,
infinite loop. This is because Parsley will need the recursive point to be the same physical object
as the original definition. When using a `def`, each time recursion is encountered it will create
a new object and generate a truly infinite parser, instead of a cyclic one. We'll see an example of
where we need to be careful about this later.

Before we move on with a more fleshed out example, I want to annotate the `matching` parser with
`debug` to give you a sense of how recursive parsing works out (I marked both parentheses and the
`matching` parser itself):

@:format(html)
<details>
<summary>Trace of <code>onlyMatchingDebug.parse("(()(()))")</code> </summary>
<p>
@:@

```scala mdoc:invisible
import parsley.debug._
parsley.debug.disableColorRendering()
lazy val matchingDebug: Parsley[Unit] = many('('.debug("left") ~> matchingDebug <~ ')'.debug("right")).void.debug("matching")
val onlyMatchingDebug = matchingDebug <~ eof
```
```scala mdoc:to-string
onlyMatchingDebug.parse("(()(()))")
```
@:format(html)
</p>
</details>
@:@

Take a moment to just absorb that and be comfortable with how recursive parsing works out.

### Example: Parsing Boolean Expressions
The matching bracket parser was a simple example of a recursive parser. For our second to last
example on this page (phew), we are going to parse (and evaluate!) boolean expressions with
right associative operators. I'm going to start by giving the EBNF for this grammar to give a sense
of what we are working with (and for you to be able to compare the approaches).

```ebnf
<expr> ::= <term> '||' <expr> | <term>
<term> ::= <not> '&&' <term> | <not>
<not> ::= '!' <not> | <atom>
<atom> ::= 'true' | 'false' | '(' <expr> ')'
```

We can see from this already it is a very recursive grammar, with almost every rule being
recursive, as well as a recursive call to `<expr>` in `<atom>`. Now, it's perfectly possible to
translate this grammar almost directly, but notice in `<expr>` and `<term>` that both alternatives
in the grammar share a common leading prefix: as we identified earlier, this would require us to
enable backtracking with `atomic` and will affect the time-complexity of the parse (here it would
be exponential!). So, as a quick refactor, we will extract the common edge and represent the
grammar as follows (where square brackets indicate optional component):

```ebnf
<expr> ::= <term> ['||' <expr>]
<term> ::= <not> ['&&' <term>]
<not> ::= '!' <not> | <atom>
<atom> ::= 'true' | 'false' | '(' <expr> ')'
```

Now, this grammar can be parsed in linear time, even when translated directly. This is much better!
However, I'll make the inefficient parser first, as it has the simpler translation (even if it's
less efficient) and will give a sense of how the solution works out.

```scala mdoc:silent
import parsley.Parsley, Parsley.atomic
import parsley.syntax.character.stringLift
import parsley.syntax.lift.liftSyntax2
import parsley.syntax.zipped._

val or = (x: Boolean, y: Boolean) => x || y

// <expr> ::=        <term>  '||' <expr>  | <term>
lazy val expr: Parsley[Boolean] =
      atomic(or.lift(term <* "||", expr)) |  term

// <term> ::= <not> '&&'   <term>                 | <not>
lazy val term: Parsley[Boolean] =
       atomic((not, "&&" ~> term).zipped(_ && _)) |  not

// <not> ::=                     '!'   <not>        | <atom>
lazy val not: Parsley[Boolean] = "!" *> not.map(!_) |  atom

// <atom> ::= 'true'          | 'false'           |  '('   <expr>   ')'
val atom    = "true".as(true) | "false".as(false) | ("(" ~> expr <~ ")")
```
```scala mdoc:to-string
expr.parse("!false")
expr.parse("true&&!(false||true)")
```

Here I've introduced a tiny bit of sugar: by importing `implicits.lift.Lift2`, I've enabled the `lift`
method on two argument functions: essentially the same as `lift2` itself:
`(or.lift _): (Parsley[Boolean], Parsley[Boolean]) => Parsley[Boolean]`. The `lift` is used to
actually perform our desired actions: when we read ` || ` we want to actually combine the results
with `||`! However, you'll notice I had to define it as a function with an explicit type signature:
this is because Scala is reluctant to perform inference on the lambda when the argument types aren't
known. To mitigate this, `implicits.zipped.Zipped2` provides the same functionality, but where `.zipped` is called
on a tuple, notice how this means that a raw unannotated lambda can be used: this is because Scala
has already learnt information about what types the arguments should have! Try to use the tupled `zipped`
notation sparingly, however: the backwards application makes it a little trickier to notice the `,`s
in the brackets. Try to only use it when you only have _small_ parsers as arguments and `lift` when
it works fine.

The parser itself has a close resemblance to the original grammar, just with the extra
processing of the result. Notice, of course, that because `expr`, `term` and `not` are
self-recursive, they all need explicit type signatures, and have been marked as `lazy`. This also
allows me to use `atom` before it's defined in the lazy `not`. However, as I mentioned before, this
is not ideal because of the heavy backtracking implied by the use of `atomic`. The solution, as I've
said, is to implement the second grammar. This is, as we'll see, a little tricker:

```scala mdoc:silent:reset
import parsley.Parsley
import parsley.syntax.character.stringLift
import parsley.syntax.lift.liftSyntax2
import parsley.combinator.option

val and = (y: Boolean) => (x: Boolean) => x && y

// This is possible here, because false is the "zero" of ||
// but more generally we'd want the other definition
// val or = (x: Boolean, y: Option[Boolean]) => x || y.getOrElse(false)
val or = (x: Boolean, y: Option[Boolean]) => y.foldLeft(x)(_ || _)

// <expr> ::=                            <term>       ['||'   <expr> ]
lazy val expr: Parsley[Boolean] = or.lift(term, option("||" ~> expr  ))

// <term> ::= <not>     ('&&'   <term>          |      epsilon      )
lazy val term: Parsley[Boolean] =
               not <**> ("&&" ~> term.map(and) </> identity[Boolean])

// <not> ::=                     '!'   <not>        | <atom>
lazy val not: Parsley[Boolean] = "!" ~> not.map(!_) | atom

// <atom> ::= 'true'          | 'false'           |  '('   <expr>   ')'
val atom    = "true".as(true) | "false".as(false) | ("(" ~> expr <~ ")")
```
```scala mdoc:to-string
expr.parse("!false")
expr.parse("true&&!(false||true)")
```

The new example is the more efficient, linear time, form of the parser. Here I've employed two
different approaches of compiling the first `term`/`not` with the _possible_ result after a _possible_
operator. In the `expr` case, I've used a form very similar to our original parser, except by using
the `option` combinator, I can try and parse what's inside and return `None` if it's not there.
The `or` function will then have to handle both the case where it was only a `term` _and_ the case
where it was a `term` with another `expr`. Here I've used `Option.foldLeft` to do this, but there are
many other ways of writing the function.

In the second case, with `term`, I've adopted an approach using the `<**>` combinator, which has
the following type:

```scala
(_ <**> _): (Parsley[A], Parsley[A => B]) => Parsley[B]
```

It is essentially `<*>` but with the function and the argument reversed. So inside the brackets,
I have to read a `term`, and if I'm successful I can apply the `and` function to it (notice the order
of the arguments in the `and` function has been deliberately reversed and it has been curried). If
I'm not successful I should return the identity function on `Boolean`, `identity[Boolean]`. The
`p </> x` combinator is the same as saying `p <|> pure(x)`. This means our initial `not` result
will either be applied to the identity function or the partially applied `and` function.

The reason I've shown both styles is so that you can decide for yourself which you prefer: `Option`
or curried. This really isn't the best we could have done though! The page on building expression
parsers will show you how you can write this parser without having to fiddle with the `and` and `or`
functions at all! (spoiler: it involves a combinator that builds the expression parsers for you).

### Higher-Order Example: Defining `many`
As a final exercise, I want to show how we can implement the `many` combinator: recall that it will
execute its argument zero or more times and collect all the results. It's a nice exercise in how
the concepts we've already seen apply to an example where the parser we are constructing has a
parameter of its own: in other words, a higher-order parser. It will also highlight a gotcha when
writing your own combinators, just in case you become comfortable enough to do so.

The first step will be to think about what `many(p)` should do: if the parser `p` fails _without_
consuming input, then we shouldn't fail but instead stop and return the results we've collected so
far. If no `p`s were parsed, then the empty list should be returned. This gives us a hint about a
use of `</>`, where we want to handle a failure by returning a known value. If a `p` is parsed, we
need to try reading yet more `p`s, so this is an indication of recursion creeping in. So, with this
in mind, let's see the definition:

```scala mdoc:silent
import parsley.Parsley

// many p = p <:> many p <|> pure []
def many[A](p: =>Parsley[A]): Parsley[List[A]] = {
    lazy val go: Parsley[List[A]] = (p <::> go) </> Nil
    go
}
```

The definition isn't so complex, but comparing it with the Haskell equivalent in the comments does
shed light on what extra things we need to be careful of here. The first is noticing that the argument
`p` has been marked as `=>Parsley[A]`. This is because, like all of Parsley's combinators, we ideally
want the argument to be lazy: this is what `=>A` means (except unlike Haskell, the argument isn't
memoised). Then we can see the familar `lazy val` with explicit type signature that we expect from
recursive parsers by now. What might seem a bit strange, however, is why I created the `go` value in
the first place. You may be tempted to write something like this instead:

```scala mdoc:silent:nest
import parsley.Parsley

def many[A](p: =>Parsley[A]): Parsley[List[A]] = (p <::> many(p)) </> Nil
```

And the answer ties back to what I mentioned earlier: there is a difference in quite how recursive
each of the two are. In the first example, `go` physically refers back to itself, and so that is a
morally _finite_ parser. The second example creates a new parser at each recursive step, so it is
morally _infinite_. In Parsley, we must be careful to only work with _finite_ parsers, as they are
actually represented by Abstract Syntax Trees. So the solution here is to create a value that can
reference the parameter `p`, without needing to pass it around itself. You might wonder if it's
possible to make parsers that, say, have a value they pass around. The answer is yes, but it's quite
uncommon to _need_ to do that. For these circumstances, the functionality in `parsley.state` is
useful, but this is certainly out of scope for this page!

#### Takeaways
* Recursive parsers are where the real work happens
* Using `lazy val` with any parser that is recursive is the safest way of writing them
* Recursive parsers need explicit types, as Scala can't infer them
* Parameterised recursion must be avoided: if the argument doesn't change then hoist it out!
* With expression grammars in particular, we should be mindful about the adverse effects of
  backtracking

## What Next?
The next logical step once you've digested this page, is to go and have a play around yourself! When
you feel ready, you should take a look at the
[Building Expression Parsers]
page to start seeing how recursive parsers can go wrong, and what the typical strategies are of
addressing this.
