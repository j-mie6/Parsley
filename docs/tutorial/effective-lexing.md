{%
laika.versioned = true
laika.site.metadata.description = "How to handle low-level tokenisation in a parser."
%}
# Effective Lexing

@:callout(info)
Parsley offers a user-friendly API for lexing, which is detailed at the end of this document. We recommend using this API for most purposes.

The initial sections of this document explain the fundamental principles behind lexing with parser combinators.
@:@

In the previous post, we saw the basic principles behind handling whitespace in a transparent manner.
To remind ourselves of what we ended up lets pick up where we left off:

```scala mdoc
import parsley.Parsley, Parsley.{atomic, eof, many}
import parsley.character.{digit, whitespace, string, item, endOfLine}
import parsley.combinator.manyTill
import parsley.expr.{precedence, Ops, InfixL}
import parsley.errors.combinator.ErrorMethods

object lexer {
    private def symbol(str: String): Parsley[String] = atomic(string(str))

    private val lineComment = symbol("//") ~> manyTill(item, endOfLine).void
    private val multiComment = symbol("/*") ~> manyTill(item, symbol("*/")).void
    private val comment = lineComment | multiComment
    private val skipWhitespace = many(whitespace.void | comment).void.hide

    private def lexeme[A](p: Parsley[A]): Parsley[A] = p <~ skipWhitespace
    private def token[A](p: Parsley[A]): Parsley[A] = lexeme(atomic(p))
    def fully[A](p: Parsley[A]): Parsley[A] = skipWhitespace ~> p <~ eof

    val number = token(digit.foldLeft1[BigInt](0)((n, d) => n * 10 + d.asDigit))

    object implicits {
        implicit def implicitSymbol(s: String): Parsley[String] = lexeme(symbol(s))
    }
}

object expressions {
    import lexer.implicits.implicitSymbol
    import lexer.{number, fully}

    private lazy val atom: Parsley[BigInt] = "(" ~> expr <~ ")" | number
    private lazy val expr = precedence[BigInt](atom)(
        Ops(InfixL)("*" as (_ * _)),
        Ops(InfixL)("+" as (_ + _), "-" as (_ - _)))

    val parser = fully(expr)
}
```

So far, we've broken the parser into two distinct chunks: the lexer and the main parser. Within
the lexer we need to be careful and _explicit_ about where we handle whitespace and where we
don't; within the parser we can assume that all the whitespace has been correctly dealt with and
can focus on the main content. To help motivate the changes we are going to make to the lexer
object later on in the post, I want to first extend our "language" to add in variables into the
language and a `negate` operator. In the process I'm going to swap the integer result for an
abstract syntax tree.

```scala mdoc:nest:invisible
import parsley.character.{stringOfSome, letter}
object lexer {
    def symbol(str: String): Parsley[String] = atomic(string(str))

    private val lineComment = symbol("//") ~> manyTill(item, endOfLine).void
    private val multiComment = symbol("/*") ~> manyTill(item, symbol("*/")).void
    private val comment = lineComment | multiComment
    private val skipWhitespace = many(whitespace.void | comment).void.hide

    def lexeme[A](p: Parsley[A]): Parsley[A] = p <~ skipWhitespace
    def token[A](p: Parsley[A]): Parsley[A] = lexeme(atomic(p))
    def fully[A](p: Parsley[A]): Parsley[A] = skipWhitespace ~> p <~ eof

    val number = token(digit.foldLeft1[BigInt](0)((n, d) => n * 10 + d.asDigit))
    val identifier = token(stringOfSome(letter))

    object implicits {
        implicit def implicitSymbol(s: String): Parsley[Unit] = lexeme(symbol(s)).void
    }
}
```

```scala mdoc
import parsley.expr.{precedence, Ops, InfixL, Prefix}
object expressions {
    import lexer.implicits.implicitSymbol
    import lexer.{number, fully, identifier}
    // for now, assume that `identifier` is just 1 or more alphabetical characters

    sealed trait Expr
    case class Add(x: Expr, y: Expr) extends Expr
    case class Mul(x: Expr, y: Expr) extends Expr
    case class Sub(x: Expr, y: Expr) extends Expr
    case class Neg(x: Expr) extends Expr
    case class Num(x: BigInt) extends Expr
    case class Var(x: String) extends Expr

    private lazy val atom: Parsley[Expr] =
        "(" ~> expr <~ ")" | number.map(Num) | identifier.map(Var)
    private lazy val expr = precedence[Expr](atom)(
        Ops(Prefix)("negate" as Neg),
        Ops(InfixL)("*" as Mul),
        Ops(InfixL)("+" as Add, "-" as Sub))

    val parser = fully(expr)
}
```
```scala mdoc:invisible
import parsley.Success
import expressions.{Add, Num, Mul, Var}
assert(expressions.parser.parse("5 + 6 * x") == Success(Add(Num(5), Mul(Num(6), Var("x")))))
```

Now, we can assume that, since `identifier` comes from the lexer, this parser handles all the whitespace correctly. The question is, does it work?

```scala mdoc:to-string
expressions.parser.parse("x + y")

expressions.parser.parse("negate + z")

expressions.parser.parse("negate x + z")

expressions.parser.parse("negatex + z")
```

So, looking at these examples, the first one seems to work fine. The second one also works
fine, but given that we've said that identifiers are just alpha-numeric characters, you might
assume it was legal (indeed, it really _shouldn't_ be legal in most languages that don't have
"soft" keywords). The third example again works as intended, but the fourth is suspicious:
`negatex` is clearly an identifier but was parsed as `negate x`! This now gives us a set up for
refining our lexer for the rest of the page. We won't be touching the `expressions` object
again, so take a long hard look at it.

## Keywords, Identifiers and Operators
The crux of the problem we unearthed in the last section is that the implicit used to handle
strings has no awareness of keywords (or indeed operators) and identifiers that work for _any_
alpha-numeric sequence are an accident waiting to happen. Let's start by creating a couple of
sets to represent the valid keywords and operators in the language:

```scala mdoc:silent
val keywords = Set("negate")
val operators = Set("*", "+", "-")
```

Now we can use these to define some more specialist combinators for dealing with these lexemes:

* We want to ensure that identifiers are not valid keywords.
* We want to ensure reading a keyword does not have a valid identifier "letter" after it.
* We want to ensure that a specific operator does not end up being the prefix of another,
  parsable, operator. This satisfies the "maximal-munch" style of parsing.

We'll start with `identifier`: to check that an identifier we've just read is not itself a
valid keyword we can use the `filter` family of combinators. In particular, `filterOut`
provides an error message that explains why the parser has failed. Here is our new and improved
identifier:

```scala mdoc:invisible
import lexer._
```
```scala mdoc:silent:nest
import parsley.errors.combinator.ErrorMethods //for filterOut
import parsley.character.{letter, stringOfSome}

// `stringOfSome(letter)` is loosely equivalent to `some(letter).map(_.mkString)`
val identifier = token(stringOfSome(letter).filterOut {
    case v if keywords(v) => s"keyword $v may not be used as an identifier"
})
```

The `filterOut` combinator takes a `PartialFunction` from the parser's result to `String`. If
the partial function is defined for its input, that produces the error message that the parser
fails with. Notice that I've been very careful to make sure the filter happens _inside_ the
scope of the `token` combinator. If we do read an identifier and then rule it out because its
a keyword, we want the ability to backtrack. Filtering after the input has been irrevocably consumed will mean this parser fails more strongly than it should.

Next we'll tackle the keywords, using the handy `notFollowedBy` combinator that was briefly
referenced in the very first post:

```scala mdoc
import parsley.Parsley.notFollowedBy
def keyword(k: String): Parsley[Unit] = token(string(k) ~> notFollowedBy(letter))
```

Again, notice that I've been very careful to perform the negative-lookahead within the scope of
the `token` so that we can backtrack if necessary (indeed, it's likely that a valid
alternative was an identifier!). Additionally, we also need to ensure that whitespace isn't
read _before_ we try and check for the `letterOrDigit`, otherwise `negate x` would _also_ fail, so that's
another reason to keep it within `token`.

Finally, let's look at how operator is dealt with. It's a bit trickier, and in this case is
meaningless, because all of our operators are single character and don't form valid prefixes of
each other. But it will be useful to discuss anyway:

```scala mdoc
import parsley.character.strings

def operator(op: String): Parsley[Unit] = {
    val biggerOps = operators.collect {
        case biggerOp if biggerOp.startsWith(op)
                      && biggerOp > op => biggerOp.substring(op.length)
    }.toList
    biggerOps match {
        case Nil => lexeme(symbol(op)).void
        // strings requires one non-varargs argument
        case biggerOp :: biggerOps =>
            token(string(op) ~> notFollowedBy(strings(biggerOp, biggerOps: _*)))
    }
}
```
```scala mdoc:invisible
assert(operator("+").parse("+") == Success(()))
```

Let's unpack what's going on here: first we read the op as normal, then we ensure that it's
not followed by the _rest_ of any operators for which it forms a valid prefix. This is using the
regular `collect` method on Scala `Set`. As an example, if we have the operator set
`Set("*", "*+", "*-", "++")` and we call `operator("*")`, we would be checking
`notFollowedBy(strings("+", "-"))`, since `*+` and `*-` are both prefixed by `*`. This is,
again, a great example of how powerful access to regular Scala code in our parsers is! This
would be quite tricky to define in a parser generator!

So, the question is, what do we do with our new found combinators? We could just expose them to
the rest of the parser as they are, but that leaves room for error if we forget, or miss out,
any of the replacements. And, in addition, we lose the nice string literal syntax we've made
good use of until this point. So, a better solution would be to change our definition of
`implicitSymbol`:

```scala mdoc
object implicits {
    implicit def implicitSymbol(s: String): Parsley[Unit] = {
        if (keywords(s))       keyword(s)
        else if (operators(s)) operator(s)
        else                   lexeme(symbol(s)).void
    }
}
```
```scala mdoc:invisible
import parsley.Failure
assert(implicits.implicitSymbol("negate").parse("negatex").isInstanceOf[Failure[_]])
```

Now, when we use a string literal in our original parser, it will first check to see if that is
a valid keyword or an operator and, if so, it can use our specialised combinators: neat! With this done, let's see what the new lexer looks like and
relook at the problematic example:

```scala mdoc:invisible:reset
import parsley.Parsley, Parsley.{atomic, notFollowedBy, eof, many}
import parsley.character.{digit, letter, whitespace, string, item, endOfLine, strings, stringOfSome}
import parsley.combinator.manyTill
import parsley.expr.{precedence, Ops, InfixL, Prefix}
import parsley.errors.combinator.ErrorMethods
```
```scala mdoc
object lexer {
    private val keywords = Set("negate")
    private val operators = Set("*", "+", "-")

    private def symbol(str: String): Parsley[String] = atomic(string(str))

    private val lineComment = symbol("//") ~> manyTill(item, endOfLine).void
    private val multiComment = symbol("/*") ~> manyTill(item, symbol("*/")).void
    private val comment = lineComment | multiComment
    private val skipWhitespace = many(whitespace.void | comment).void.hide

    private def lexeme[A](p: Parsley[A]): Parsley[A] = p <~ skipWhitespace
    private def token[A](p: Parsley[A]): Parsley[A] = lexeme(atomic(p))
    def fully[A](p: Parsley[A]): Parsley[A] = skipWhitespace ~> p <~ eof

    val number = token(digit.foldLeft1[BigInt](0)((n, d) => n * 10 + d.asDigit))
    val identifier = token(stringOfSome(letter).filterOut {
        case v if keywords(v) => s"keyword $v may not be used as an identifier"
    })

    private def operator(op: String): Parsley[Unit] = {
        val biggerOps = operators.collect {
            case biggerOp if biggerOp.startsWith(op)
                        && biggerOp > op => biggerOp.substring(op.length)
        }.toList
        biggerOps match {
            case Nil => lexeme(symbol(op)).void
            // strings requires one non-varargs argument
            case biggerOp :: biggerOps =>
                token(string(op) ~> notFollowedBy(strings(biggerOp, biggerOps: _*)))
        }
    }

    private def keyword(k: String): Parsley[Unit] =
        token(string(k) ~> notFollowedBy(letter))


    object implicits {
        implicit def implicitSymbol(s: String): Parsley[Unit] = {
            if (keywords(s))       keyword(s)
            else if (operators(s)) operator(s)
            else                   lexeme(symbol(s)).void
        }
    }
}
```
```scala mdoc:invisible
object expressions {
    import lexer.implicits.implicitSymbol
    import lexer.{number, fully, identifier}
    // for now, assume that `identifier` is just 1 or more alphabetical characters

    sealed trait Expr
    case class Add(x: Expr, y: Expr) extends Expr
    case class Mul(x: Expr, y: Expr) extends Expr
    case class Sub(x: Expr, y: Expr) extends Expr
    case class Neg(x: Expr) extends Expr
    case class Num(x: BigInt) extends Expr
    case class Var(x: String) extends Expr

    private lazy val atom: Parsley[Expr] =
        "(" ~> expr <~ ")" | number.map(Num) | identifier.map(Var)
    private lazy val expr = precedence[Expr](atom)(
        Ops(Prefix)("negate" as Neg),
        Ops(InfixL)("*" as Mul),
        Ops(InfixL)("+" as Add, "-" as Sub))

    val parser = fully(expr)
}
```

And the original failing example:

```scala mdoc:to-string
expressions.parser.parse("negatex + z")
```

Exactly as desired!

## Using `token.descriptions.LexicalDesc` and `token.Lexer`
Whilst everything we have done above is nice and instructive, in practice all this work is
already done for us with `token.Lexer`. By providing a suitable `token.descriptions.LexicalDesc`,
we can get a whole bunch of combinators for dealing with tokens for free. There is a lot of
functionality found inside the `Lexer`, and most of it is highly configurable with the `LexicalDesc`
and its sub-components. Let's make use of this new found power and change up our `lexer` object one
last time:

```scala mdoc:nest
object lexer {
    import parsley.token.{Lexer, Basic}
    import parsley.token.descriptions.{LexicalDesc, NameDesc, SymbolDesc}

    private val desc = LexicalDesc.plain.copy(
        nameDesc = NameDesc.plain.copy(
            // Unicode is also possible instead of Basic
            identifierStart = Basic(_.isLetter),
            identifierLetter = Basic(_.isLetter),
        ),
        symbolDesc = SymbolDesc.plain.copy(
            hardKeywords = Set("negate"),
            hardOperators = Set("*", "+", "-"),
        ),
    )

    private val lexer = new Lexer(desc)

    val identifier = lexer.lexeme.names.identifier
    val number = lexer.lexeme.natural.decimal

    def fully[A](p: Parsley[A]) = lexer.fully(p)
    val implicits = lexer.lexeme.symbol.implicits
}
```
```scala mdoc:invisible
import parsley.Failure
assert(lexer.implicits.implicitSymbol("negate").parse("negatex").isInstanceOf[Failure[_]])
```

The `implicitSymbol` function we developed before, along with `operator` and
`keyword` are all implemented by `lexer.lexeme.symbol`. The `names.identifier` parser
accounts for the keyword problem for us. The basic `natural.decimal` parser
meets our needs without any additional configuration: it also returns `BigInt`, which
is arbitrary precision. By using `token.lexeme`, this will already handle the
whitespace and atomicity of the token for us. This is just the tip of the iceberg
when it comes to the lexer functionality within Parsley. It is well worth having a
play around with this functionality and getting used to it!

A more detailed description of this functionality can be found in the [API Guide](../api-guide/token/Lexer.md).
