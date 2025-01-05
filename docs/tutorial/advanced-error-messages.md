{%
laika.versioned = true
laika.site.metadata.description = "How to employ more advanced techniques to make error messages context-dependent."
%}
# Advanced Error Messages
Previously, we saw the most basic approach to improving error messages: `.label` and `.explain`.
However, the other tools I listed in the post are valuable in their own right, but can be slightly
less common. However, most of the time, their use is abstracted by other higher-level combinators,
which will be the focus of this page. The API Guide page on [Error Message Combinators] covers the
core combinators more thoroughly.

## A Statement Language
In this section, we'll set the stage for the discussion of the error messages. We'll start by
defining an evaluator for a langauge (as opposed to an AST), and then write a parser that generates
partially evaluated expressions and collapse it to form an interpreter. The language
supports conditionals, assignment, arithmetic, and boolean expressions; variables must be integers.

### A Stateful Evaluator
The language will be evaluated by producing a value of type `Eval[Unit]`, a monad that threads a
variable environment through the program, handling any out-of-scope variables. Just for fun, I'll
use the `cats` functional programming library to do this, as it makes it very easy to express.
The environment will be carried around in a `StateT` state monad, and errors contains within an
`Either[String, _]` type. Don't worry if you don't understand how it works; our main focus is
obviously the **parser**:

```scala mdoc
import cats.syntax.all._
object eval {
    import cats.data.StateT
    import cats.Monad
    type Error[A] = Either[String, A]
    type Eval[A] = StateT[Error, Map[String, Int], A]

    def number(x: Int): Eval[Int] = Monad[Eval].pure(x)
    def bool(b: Boolean): Eval[Boolean] = Monad[Eval].pure(b)

    def negate(mx: Eval[Int]): Eval[Int] = mx.map(0 - _)
    def add(mx: Eval[Int], my: Eval[Int]): Eval[Int] = (mx, my).mapN(_ + _)
    def sub(mx: Eval[Int], my: Eval[Int]): Eval[Int] = (mx, my).mapN(_ - _)
    def mul(mx: Eval[Int], my: Eval[Int]): Eval[Int] = (mx, my).mapN(_ * _)

    def less(mx: Eval[Int], my: Eval[Int]): Eval[Boolean] = (mx, my).mapN(_ < _)
    def equal(mx: Eval[Int], my: Eval[Int]): Eval[Boolean] = (mx, my).mapN(_ == _)

    def and(mx: Eval[Boolean], my: Eval[Boolean]): Eval[Boolean] = cond(mx, my, bool(false))
    def or(mx: Eval[Boolean], my: Eval[Boolean]): Eval[Boolean] = cond(mx, bool(true), my)
    def not(mx: Eval[Boolean]): Eval[Boolean] = mx.map(!_)

    def ask(v: String): Eval[Int] =
        StateT.inspectF(_.get(v).toRight(s"variable $v out of scope"))
    def store(v: String, mx: Eval[Int]): Eval[Unit] =
        mx.flatMap(x => StateT.modify(_.updated(v, x)))

    def cond[A](b: Eval[Boolean], t: Eval[A], e: Eval[A]): Eval[A] =
        Monad[Eval].ifM(b)(t, e)
}
```

The `ask` and `store` operations above allow for interaction with the environment, and the other
operations are just "lifting" the relevant operations into our monad. Just so you can see how this
might be stitched together, here are some example programs:

```scala mdoc:to-string
import eval._
store("x", add(number(5), ask("v"))).runS(Map("v" -> 3))
store("x", add(number(5), ask("v"))).runS(Map.empty)

List(
    store("v", number(3)),                // v = 3;
    store("x", add(number(5), ask("v"))), // x = 5 + v;
    cond(less(ask("x"), ask("v")),        // if x < v {
        store("v", ask("x")),             //   v = x } else {
        store("v", number(6))),           //   v = 6 };
).sequence.runS(Map.empty)
```

If we start off with an initial variable assignment of `{v = 3}`, then the `Eval` value representing
`x = 5 + v` will finish with a final environment of `{v = 3, x = 8}`. If we don't provide an initial
value to `v`, the evaluation errors with an "out of scope" error. The `sequence` method can be used
to compose multiple statements in the langauge.

### A Parser
We'll be using the same `lexer` as we've been accustomed to recently (with some extra
keywords and operators), so let's see what the parser is like:

```scala mdoc:invisible
import parsley.Parsley
object lexer {
    import parsley.token.{Lexer, Basic}
    import parsley.token.descriptions.{LexicalDesc, NameDesc, SymbolDesc}
    import parsley.errors.combinator._

    private val desc = LexicalDesc.plain.copy(
        nameDesc = NameDesc.plain.copy(
            identifierStart = Basic(_.isLetter),
            identifierLetter = Basic(_.isLetter),
        ),
        symbolDesc = SymbolDesc.plain.copy(
            hardKeywords = Set("negate", "not", "if", "else", "true", "false"),
            hardOperators = Set("*", "+", "-", "&&", "||", "<", "=="),
        ),
    )

    private val lexer = new Lexer(desc)

    val identifier = lexer.lexeme.names.identifier
    val number = lexer.lexeme.natural.decimal32.label("number")

    def fully[A](p: Parsley[A]) = lexer.fully(p)
    val implicits = lexer.lexeme.symbol.implicits

    def nonLexemeSymbol(s: String): Parsley[Unit] = lexer.nonlexeme.symbol(s)
}
```

```scala mdoc:silent
import parsley.Parsley.atomic
import lexer.implicits.implicitSymbol
import lexer.{number, identifier, fully}
import parsley.combinator.sepEndBy
import parsley.syntax.zipped._
import parsley.expr.{Prefix, InfixR, InfixL, precedence, Ops}

def infixN[A, B](p: Parsley[A])(op: Parsley[(A, A) => B]): Parsley[B] =
    (p, op, p).zipped((x, f, y) => f(x, y))

lazy val atom: Parsley[Eval[Int]] =
    "(" ~> expr <~ ")" | number.map(eval.number) | identifier.map(ask)
lazy val expr = precedence[Eval[Int]](atom)(
    Ops(Prefix)("negate" as negate),
    Ops(InfixL)("*" as mul),
    Ops(InfixL)("+" as add, "-" as sub))

lazy val blit: Parsley[Eval[Boolean]] = "true".as(bool(true)) | "false".as(bool(false))
lazy val comp: Parsley[Eval[Boolean]] = infixN(expr)("<".as(less) | "==".as(equal))
lazy val btom: Parsley[Eval[Boolean]] = atomic("(" ~> pred) <~ ")" | blit | comp
lazy val pred = precedence[Eval[Boolean]](btom)(
    Ops(Prefix)("not" as not),
    Ops(InfixR)("&&" as and),
    Ops(InfixR)("||" as or))

def braces[A](p: =>Parsley[A]) = "{" ~> p <~ "}"

lazy val asgnStmt: Parsley[Eval[Unit]] = (identifier, "=" ~> expr).zipped(store)
lazy val ifStmt: Parsley[Eval[Unit]] =
    ("if" ~> pred, braces(stmts), "else" ~> braces(stmts)).zipped(cond[Unit])
lazy val stmt: Parsley[Eval[Unit]] = asgnStmt | ifStmt
lazy val stmts: Parsley[Eval[Unit]] = sepEndBy(stmt, ";").map(_.sequence.void)

val parser = fully(stmts)
```

I'm not going to say too much about this, since all of the ideas have been covered in previous pages
(and the Haskell example!). The evaluator and the parser can be stitched together to form an interpreter:

```scala mdoc
def interpret(input: String)(ctx: Map[String, Int]) = {
    parser.parse(input).toEither.flatMap(_.runS(ctx))
}
```

Let's run an example through it and get used to the syntax:

```scala mdoc:to-string
interpret(
    """x = 7;
      |if x < v && 5 < v {
      |  y = 1;
      |}
      |else {
      |  y = 0;
      |};
      |x = 0;
    """.stripMargin)(Map("v" -> 8))
```

The `|`s here are being used by Scala's `stripMargin` method to remove the leading whitespace on our
multi-line strings. We can see that the syntax of this language makes use of semi-colons for
delimiting, and `if` statements require a semi-colon after the `else`. In addition, no parentheses
are required for the `if`, but braces are mandated. This syntax is a little unorthodox, which is
great for us because it'll give us a lot of opportunities to test out our new tools! I've neglected
to add any `.label`s or `.explain`s here, but obviously there are plenty of opportunities.

### Motivation
Let's start by seeing what happens if we accidentally write a `;` before an `else`:

```scala mdoc:to-string
interpret("if true {}; else {};")(Map.empty)
```

This is what we'd expect, since at this point we'd expect an `else`. We could detail this issue for
the user with an `.explain`, and explain that semi-colons are not something that work in this
position:

```scala mdoc:nest:silent
import parsley.errors.combinator._
lazy val ifStmt: Parsley[Eval[Unit]] =
    ( "if" ~> pred
    , braces(stmts)
    , "else".explain("semi-colons cannot be written after ifs") ~> braces(stmts)
    ).zipped(cond[Unit])
```
```scala mdoc:invisible
lazy val stmt: Parsley[Eval[Unit]] = asgnStmt | ifStmt
lazy val stmts: Parsley[Eval[Unit]] = sepEndBy(stmt, ";").map(_.sequence.void)

val parser = fully(stmts)

def interpret(input: String)(ctx: Map[String, Int]) = {
    parser.parse(input).toEither.flatMap(_.runS(ctx))
}
```

What effect will this have?

```scala mdoc:to-string
interpret("if true {}; else {};")(Map.empty)
```

Ok, this is better! But what if I wrote something else there instead?

```scala mdoc:to-string
interpret("if true {}a else {};")(Map.empty)
```

Ah, right, not so good anymore. We could go back and make the `explain` a bit more general, of course,
but that means we've lost out on the helpful prompt to the user about our language's syntax. So, what
can we do here? This is where the *Verified Errors* pattern comes into play.

## *Verified Errors*
The problem with using `explain` to write about a specific instance of a problem is that there is
no guarantee that the problem actually occurred. This is what we observed above, where an `a` was
misreported as a semi-colon! The *Verified Errors* pattern tells us the following:

> Parse bad input to verify that contextual obligations are met before raising an error.

Basically, if we want to report an error about a spurious semi-colon, we better check that it is
*actually* there first! There are a [few properties](../api-guide/errors/patterns.md#verified-errors)
we'd expect of these errors and there are a
few ways of formulating them. One way is using `amend`, `hide`,
`empty`, and so on, but most of the time this pattern follows a regular enough structure that it
has been encoded into a family of combinators in `parsley.errors.patterns.VerifiedErrors`:

```scala mdoc:nest:silent
import parsley.character.char
import parsley.errors.patterns.VerifiedErrors

val _semiCheck =
    char(';').verifiedExplain("semi-colons cannot be written between `if` and `else`")

lazy val ifStmt: Parsley[Eval[Unit]] =
    ( "if" ~> pred
    , braces(stmts)
    , ("else" | _semiCheck) ~> braces(stmts)
    ).zipped(cond[Unit])
```
```scala mdoc:invisible
lazy val stmt: Parsley[Eval[Unit]] = asgnStmt | ifStmt
lazy val stmts: Parsley[Eval[Unit]] = sepEndBy(stmt, ";").map(_.sequence.void)

val parser = fully(stmts)

def interpret(input: String)(ctx: Map[String, Int]) = {
    parser.parse(input).toEither.flatMap(_.runS(ctx))
}
```

Here, if we can't read an `else`, we immediately try to parse `_semiCheck`, which reads a semi-colon
(and is careful to hide it from the errors, otherwise we might see `expected else or ";"`). If it succeeds,
then we fail generate a reason for the failure. If it didn't successfully parse, nothing happens, because
the *contextual obligation* for the error was not met. A parser like `_semiCheck` is known as an "error widget":
prefix them with `_` to make them more immediately identifiable.

With this we have:

```scala mdoc:to-string
interpret("if true {}; else {};")(Map.empty)

interpret("if true {}a else {};")(Map.empty)
```

This is exactly what we wanted! So, where else can we apply this technique? Let's see what happens
if we miss out a closing brace:

```scala mdoc:to-string
interpret("if true {} else {")(Map.empty)
```

We could, again, give the user a helping hand here, and point out that they have an unclosed
_something_ that they need to close. Again, we could start by using the `.explain` combinator
directly, on the `}`. Let's see what effect this will have:

```scala mdoc:nest
def braces[A](p: =>Parsley[A]) = "{" ~> p <~ "}".explain("unclosed `if` or `else`")
```
```scala mdoc:invisible
lazy val ifStmt: Parsley[Eval[Unit]] =
    ( "if" ~> pred
    , braces(stmts)
    , ("else" | _semiCheck) ~> braces(stmts)
    ).zipped(cond[Unit])
lazy val stmt: Parsley[Eval[Unit]] = asgnStmt | ifStmt
lazy val stmts: Parsley[Eval[Unit]] = sepEndBy(stmt, ";").map(_.sequence.void)

val parser = fully(stmts)

def interpret(input: String)(ctx: Map[String, Int]) = {
    parser.parse(input).toEither.flatMap(_.runS(ctx))
}
```

This will give us the more helpful error:

```scala mdoc:to-string
interpret("if true {} else {")(Map.empty)
```

This time, adding extra input won't cause a problem, so is this fine? Well, what about this input:

```scala mdoc:to-string
interpret(
    """if true {}
      |else {
      | x = 7a
      |}""".stripMargin)(Map.empty)
```

Argh! The `else` _is_ closed this time, but since `}` is a valid continuation character we've
triggered our `explain` message. Again, we can fix this by using a verified error (on `eof`)

```scala mdoc:nest:silent
import parsley.Parsley.eof
val _eofCheck = eof.verifiedExplain("unclosed `if` or `else`")
def braces[A](p: =>Parsley[A]) = "{" ~> p <~ ("}" | _eofCheck)
```
```scala mdoc:invisible
lazy val ifStmt: Parsley[Eval[Unit]] =
    ( "if" ~> pred
    , braces(stmts)
    , ("else" | _semiCheck) ~> braces(stmts)
    ).zipped(cond[Unit])
lazy val stmt: Parsley[Eval[Unit]] = asgnStmt | ifStmt
lazy val stmts: Parsley[Eval[Unit]] = sepEndBy(stmt, ";").map(_.sequence.void)

val parser = fully(stmts)

def interpret(input: String)(ctx: Map[String, Int]) = {
    parser.parse(input).toEither.flatMap(_.runS(ctx))
}
```

This time we've latched onto whether or not there is any input left at all. This will work fine!

```scala mdoc:to-string
interpret(
    """if true {}
      |else {
      | x = 7a
      |}""".stripMargin)(Map.empty)

interpret(
    """if true {}
      |else {
      | x = 7
      |""".stripMargin)(Map.empty)
```

Perfect 🙂. What now? Well, another area where the user might trip up is thinking that you can
assign booleans to variables! Let's see what the errors are:

```scala mdoc:to-string
interpret("x = true")(Map.empty)

interpret("x = 10 < 9")(Map.empty)

interpret("x = not true")(Map.empty)
```

Now, there is a cheap way of dealing with this and an expensive one. Let's start cheap and see what
needs to be done and how effective it is. The first thing we can recognise is that we can special
case `not`, `true`, and `false` using the same strategy as before. We can choose to attach a new
widget to the `expr` inside the `asgn`, let's start by defining the widget (using some more
generalised machinery):

```scala mdoc:silent
import parsley.combinator.choice
val _boolCheck = choice(
        lexer.nonLexemeSymbol("true"),
        lexer.nonLexemeSymbol("false"),
        lexer.nonLexemeSymbol("not"),
    ).verifiedExplain("booleans cannot be assigned to variables")
```

The `lexer.nonLexemeSymbol` combinator here is allowing for the parsing of keywords *without*
reading trailing whitespace, which would make our error messages wider. Now, we can add this to the `asgn`:

```scala mdoc:nest:silent
lazy val asgnStmt: Parsley[Eval[Unit]] =
    (identifier, "=" ~> (expr | _boolCheck)).zipped(store)
```

```scala mdoc:invisible
lazy val stmt: Parsley[Eval[Unit]] = asgnStmt | ifStmt
lazy val stmts: Parsley[Eval[Unit]] = sepEndBy(stmt, ";").map(_.sequence.void)

val parser = fully(stmts)

def interpret(input: String)(ctx: Map[String, Int]) = {
    parser.parse(input).toEither.flatMap(_.runS(ctx))
}
```

And now for the errors:

```scala mdoc:to-string
interpret("x = true")(Map.empty)

interpret("x = 10 < 9")(Map.empty)

interpret("x = not true")(Map.empty)
```

So, we've cracked the _leading_ edge
of the booleans, but we are still no closer to managing to deal with `<`, `==`. These
are significantly trickier to handle with our current approach, because they occur _after_ some input
has already been read. We would need to insert them as alternatives at every point where they could be
"valid" predictions. This is far from ideal. Note that we don't really need to worry about `&&` and
`||`, since we are going to have to have already found one of `<`, `==`, `not`, `true`, or `false`
before we reach it _anyway_. Instead, we need to look towards a different technique, the *Preventative
Error*.

## *Preventative Errors*
The problem with *verified errors* is that they have to follow the valid alternatives of a parse,
which may be disparate and scattered. Not to mention that moving the widget to a shared parser may
actually invalidate its context anyway! Instead, a *preventative error* allows us to proactively
rule out bad input. This means trying to parse it *before* trying the "working" alternatives.
Compared with *verified errors*, which are a last resort, *preventative errors* are by nature more
costly. Again, there are a [few properties](../api-guide/errors/patterns.md#preventative-errors)
we'd expect of these errors and there are a
few ways of formulating them and `parsley.errors.patterns.PreventativeErrors` contains a family
of combinators for the most common formulation:

```scala mdoc:silent
import parsley.errors.patterns.PreventativeErrors

val _noCompCheck =
    choice(lexer.nonLexemeSymbol("<"), lexer.nonLexemeSymbol("==")).preventativeExplain(
        reason = "booleans cannot be assigned to variables",
        labels = "end of arithmetic expression"
    )
```

The idea here is to try and parse one of `<` or `==` and fail immediately if this is possible,
otherwise, the combinator succeeds, and we can start parsing something else. Because raising the
error happens more eagerly, it is important to provide error labels to describe what valid things
can come next since these parsers won't actually get executed and contribute themselves! It should
be placed at a point where it is suspected the `<` and `==` could be read, namely after an expression
in the `asgn`. So, let's see what the effect of the error will be:

```scala mdoc:nest:silent
lazy val asgnStmt: Parsley[Eval[Unit]] =
    (identifier, "=" ~> ((expr <~ _noCompCheck) | _boolCheck)).zipped(store)
```

```scala mdoc:invisible
lazy val stmt: Parsley[Eval[Unit]] = asgnStmt | ifStmt
lazy val stmts: Parsley[Eval[Unit]] = sepEndBy(stmt, ";").map(_.sequence.void)

val parser = fully(stmts)

def interpret(input: String)(ctx: Map[String, Int]) = {
    parser.parse(input).toEither.flatMap(_.runS(ctx))
}
```

```scala mdoc:to-string
interpret("x = 10 < 9")(Map.empty)
```

This looks pretty good! However, it's now looking a little messier, and could do with some
abstraction:

```scala mdoc:nest:silent
def _noBool[A](p: Parsley[A]): Parsley[A] = (p <~ _noCompCheck) | _boolCheck

lazy val asgnStmt: Parsley[Eval[Unit]] = (identifier, "=" ~> _noBool(expr)).zipped(store)
```
```scala mdoc:invisible
import scala.annotation.unused
val _ = asgnStmt: @unused
```

This is a bit tidier! When I said that there was a cheap way and an expensive way of implementing
these improved errors earlier, the expensive way would have been using a preventative error from the
outset, trying to parse an *entire* boolean expression up-front. This may be much more expensive than
the reasonably small <7 character checks we've been doing. However, it is instructive to see what
this might have looked like, now just by changing `_noBool`:

```scala mdoc:nest:silent
import parsley.errors.VanillaGen

val _noBoolCheck = pred.void.preventWith(
    err = new VanillaGen[Unit] {
        override def reason(x: Unit) = Some("booleans cannot be assigned to variables")
        override def unexpected(x: Unit) = VanillaGen.NamedItem("boolean expression")
    },
    labels = "arithmetic expression"
)


def _noBool[A](p: Parsley[A]): Parsley[A] = _noBoolCheck ~> p
```

This version makes use of the `VanillaGen`, which can allow for a bit more fine-grained configuration,
including changing the unexpected message within the error. By parsing a full `pred`, we are certain
to rule out all problematic inputs immediately, but this could be much more costly - there is a
trade-off to be had! The errors are all as follows:

```scala mdoc:invisible
lazy val asgnStmt: Parsley[Eval[Unit]] = (identifier, "=" ~> _noBool(expr)).zipped(store)
lazy val stmt: Parsley[Eval[Unit]] = asgnStmt | ifStmt
lazy val stmts: Parsley[Eval[Unit]] = sepEndBy(stmt, ";").map(_.sequence.void)

val parser = fully(stmts)

def interpret(input: String)(ctx: Map[String, Int]) = {
    parser.parse(input).toEither.flatMap(_.runS(ctx))
}
```

```scala mdoc:to-string
interpret("x = true")(Map.empty)

interpret("x = 10 < 9")(Map.empty)

interpret("x = not true")(Map.empty)
```

The flip side is that these errors are just about as good as it gets for this particular problem:
notice that the error caret covers the *entire* bad expression, and reports it under a concise
naming. It can also catch errors with bracketed expressions, which may be missed by the previous
implementations (though it is still possible to catch these with some work).

## Conclusion
The main point of this page is to demonstrate how much of an important technique "parsing bad inputs"
can be to generating informative and precise error messages. Of course, there are other formulations
of these patterns, and they can address some of the shortcomings of the above implementations.
In future, I may add a discussion of *how* these patterns are implemented in practice, to show how
more bespoke ones can be useful.
