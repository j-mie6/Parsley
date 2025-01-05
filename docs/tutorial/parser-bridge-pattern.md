{%
laika.versioned = true
laika.site.metadata.description = "How to abstract away result construction from parsing."
%}

```scala mdoc:invisible
import parsley.Parsley
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
            hardKeywords = Set("negate", "let", "in"),
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

# The Parser Bridge Pattern

@:callout(info)
The first part of this page helps to motivate the *Parser Bridge* pattern, and
the second part shows how to implement it from scratch. This is useful to know,
but the API Guide [Generic Bridges](../api-guide/generic.md) page can get you
started with the technique faster. The latter parts of this page can be helpful
when the generic bridges no longer suffice.
@:@

By this point, we've seen how to effectively build expression parsers, lexers, and how to handle
whitespace in a clean way. However, something we've not touched on yet is how to encode the position
information into any data-types produced by our parsers. In fact, the way we can build our results
from our parsers can be greatly improved. We'll focus on expanding the same parser from the previous
 pages, since in its current form it has a variety of different types of constructors. What I will
 do, however it expand it with some basic `let`-binding expressions. We'll use the same `lexer`
 object as before, but I will add the keywords `let` and `in` to the keyword set. Previously, the
 grammar we were working with would have been:

```ebnf
<number>     ::= ...
<identifier> ::= ...
<atom>       ::= '(' <expr> ')' | <number> | <identifier>
<negated>    ::= 'negate' <negated> | <atom>
<term>       ::= <term> '*' <atom> | <atom>
<expr>       ::= <expr> ('+' | '-') <term> | <term>
```

We'll extend this to include a let syntax as follows:

```ebnf
<let-binding> ::= 'let' <bindings> 'in' <expr> | <expr>
<bindings>    ::= <binding> [';' [<bindings>]]
<binding>     ::= <identifier> '=' <let-binding>
```

This will allow us to write programs like:

```
let x = 10;
    y = let z = x + 4 in z * z;
in x * y
```

Now let's see how this changes the parser:

```scala mdoc
import parsley.Parsley

object ast {
    sealed trait LetExpr
    case class Let(bindings: List[Binding], x: Expr) extends LetExpr
    case class Binding(v: String, x: LetExpr)

    sealed trait Expr extends LetExpr
    case class Add(x: Expr, y: Expr) extends Expr
    case class Mul(x: Expr, y: Expr) extends Expr
    case class Sub(x: Expr, y: Expr) extends Expr
    case class Neg(x: Expr) extends Expr
    case class Num(x: BigInt) extends Expr
    case class Var(x: String) extends Expr
}

object expressions {
    import parsley.expr.{precedence, Ops, InfixL, Prefix}
    import parsley.combinator.sepEndBy1
    import parsley.syntax.lift.liftSyntax2

    import lexer.implicits.implicitSymbol
    import lexer.{number, fully, identifier}
    import ast._

    lazy val atom: Parsley[Expr] =
        "(" ~> expr <~ ")" | number.map(Num) | identifier.map(Var)
    lazy val expr = precedence[Expr](atom)(
        Ops(Prefix)("negate" as Neg),
        Ops(InfixL)("*" as Mul),
        Ops(InfixL)("+" as Add, "-" as Sub))

    lazy val binding = Binding.lift(identifier, "=" ~> letExpr)
    lazy val bindings = sepEndBy1(binding, ";")
    lazy val letExpr: Parsley[LetExpr] =
      Let.lift("let" ~> bindings, "in" ~> expr) | expr

    val parser = fully(letExpr)
}
```

So far, so good. I've added a couple of now nodes to the AST, and three extra parser
definitions. The only new thing here is the helpful `sepEndBy1` combinator, which is
particularly good (along with its cousins, `sepBy1` and `endBy1`) at dealing with things like
commas and semi-colons. However, if I now said that we need to encode position information into
our language's AST then things are going to need to change.

Let's start by adding the information into the AST. There is a trick to this depending on
whether or not we want the information to be visible during pattern matches or not. Essentially,
in Scala, if a second (or third etc) set of arguments is added to a `case class`, these
arguments will not appear in the pattern match, but _are_ required to build an instance. So
we're going to add an extra argument to each constructor containing the position information
like so:

```scala mdoc:nest
object ast {
    sealed trait LetExpr
    case class Let(bindings: List[Binding], x: Expr)(val pos: (Int, Int)) extends LetExpr
    case class Binding(v: String, x: LetExpr)(val pos: (Int, Int))

    sealed trait Expr extends LetExpr
    // We will add the position information to these nodes later
    case class Add(x: Expr, y: Expr) extends Expr
    case class Mul(x: Expr, y: Expr) extends Expr
    case class Sub(x: Expr, y: Expr) extends Expr
    case class Neg(x: Expr) extends Expr
    // But we can do these ones now
    case class Num(x: BigInt)(val pos: (Int, Int)) extends Expr
    case class Var(x: String)(val pos: (Int, Int)) extends Expr
}
```

Urgh. This isn't ideal, but realistically it's the best Scala has got. The real wart here is how
this affects our parsers. Let's just take a look at a single parser and see what damage this
will do:

```scala
val binding: Parsley[Binding] = Binding.lift(identifier, "=" ~> letExpr)
```

This no longer compiles for _several_ reasons. The first is that `Binding.lift` doesn't work
anymore, because `Binding` does not have the shape `(A, B) => C`. Instead it has the shape
`(A, B) => C => D`, and Scala will be reluctant to make the translation. The second is that,
even if we suppose that wasn't a problem, the type is going to be
`Parsley[(Int, Int) => Binding]` instead of the desired `Parsley[Binding]`. If that wasn't already
enough, there is the issue of having not dealt with the position anywhere either: what a mess!

We'll keep pretending that the `Binding.lift` notation works for a second, and consider how to
get that position information in and get it "working" again. The combinators for extracting
position information are:

```scala
import parsley.position._
val line: Parsley[Int]
val col: Parsley[Int]
val pos: Parsley[(Int, Int)] = line.zip(col)
```

So in this case, `pos` is what we are after, our first instinct might be to just add it as an
extra parameter to the lift: `Binding.lift(identifier, "=" ~> letExpr, pos)`, but `Binding` is
curried, and lift takes an uncurried function. Instead, we can use `<*>` to apply a parser
returning a function to its next argument:

```scala
val binding: Parsley[Binding] = Binding.lift(identifier, "=" ~> letExpr) <*> pos
```

Again, assuming that `Binding.lift` compiles with this snippet, this would compile fine.
However, it's faulty, because the position of the binding will point _after_ the binding itself
is finished! Instead we need to swap it round so that the position is read _before_ we start
reading anything to do with the binding. This is a great reason why we always read trailing
whitespace and not leading whitespace, as it keeps the position as close to the token as
possible. To do the position first and binding second, we can use `<**>`:

```scala
val binding: Parsley[Binding] = pos <**> Binding.lift(identifier, "=" *> letExpr)
```

Now, to get it properly compiling again, we'll need to lean on the `zipped` notation instead, to
help Scala's type inference figure out what we want.

```scala mdoc:invisible
import ast.{Binding, LetExpr}
import parsley.Parsley.empty
import parsley.position._
import lexer.identifier
import lexer.implicits._
val letExpr: Parsley[LetExpr] = empty
```
```scala mdoc:silent
import parsley.syntax.zipped._
val binding: Parsley[Binding] =
    pos <**> (identifier, "=" ~> letExpr).zipped(Binding(_, _) _)
```

This _finally_ compiles and works as intended. The `Binding(_, _) _` is desugared as follows:

```scala
Binding(_, _) _ = ((x, y) => Binding(x, y)(_))
                = ((x, y) => z => Binding(x, y)(z))
```
This isn't particularly intuitive, but it might help to recognise that the similar `Binding(_, _)(_)`
is actually equivalent to `(x, y, z) => Binding(x, y)(z)`, which is not what we want. At this
point though, we can see what a pain this would be if we put this into the parser in all the
places, especially in a bigger parser, it's very noisy and the `.zipped` notation is (in my
opinion) slightly harder to read than the `.lift` notation: it is, however, required to get Scala
to correctly annotate the types of our anonymous function for us, which would otherwise make the
size of the code even _worse_.

## The _Parser Bridge_ Pattern
The work we've done is unavoidable, but that doesn't mean we can't move it somewhere more
sensible and, at the same time, get a nice new syntax to abstract the way that AST nodes are
constructed. I call this technique the _Parser Bridge_ pattern, and it takes many shapes depending on
how the AST nodes are made.

The _Bridge_ pattern is one of the classic "Gang of Four" structural design patterns. Its
description is as follows:

> Decouple an abstraction from its implementation so that the two can vary independently.

This is roughly the intent of the _Parser Bridge_ pattern, which is defined as:

> Separate the construction of an AST node and metadata collection by using bridge constructors
  in the parser.

In practice though, this can be used for more general decoupling of the AST from the parser, which
we will also see examples of (especially in the Haskell interlude!). We'll start exploring this
pattern, and the associated terminology, with the let binding, `Num` and `Var` cases to get a
feel for it, before figuring out how to adapt it for the operators.

The general idea behind the pattern is to leverage Scala's syntactic sugar for `apply` methods.
If you're unaware, `apply` methods get sugared into "function call" syntax. It is, in fact, how
`case class`es don't require you to write a `new` keyword to build them: instead, the compiler
has generated an `apply` method on each class' _companion object_ (more on this later!). Basically,
we are going to follow suit, but tailor our `apply` method to work on parsers instead of values!
These `apply` methods are referred to as **_bridge constructors_**. Let's get working within the
`ast` object:

```scala mdoc:reset:invisible
import parsley.Parsley
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
            hardKeywords = Set("negate", "let", "in"),
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
```scala mdoc
object ast {
    import parsley.position.pos
    import parsley.syntax.zipped._

    sealed trait LetExpr
    case class Let(bindings: List[Binding], x: Expr)(val pos: (Int, Int)) extends LetExpr
    case class Binding(v: String, x: LetExpr)(val pos: (Int, Int))

    // New code here!
    object Let {
        def apply(bindings: Parsley[List[Binding]], x: Parsley[Expr]): Parsley[Let] =
            pos <**> (bindings, x).zipped(Let(_, _) _)
    }
    object Binding {
        def apply(v: Parsley[String], x: Parsley[LetExpr]): Parsley[Binding] =
            pos <**> (v, x).zipped(Binding(_, _) _)
    }

    sealed trait Expr extends LetExpr
    case class Add(x: Expr, y: Expr) extends Expr
    case class Mul(x: Expr, y: Expr) extends Expr
    case class Sub(x: Expr, y: Expr) extends Expr
    case class Neg(x: Expr) extends Expr
    case class Num(x: BigInt)(val pos: (Int, Int)) extends Expr
    case class Var(x: String)(val pos: (Int, Int)) extends Expr

    // New code here!
    object Num {
        def apply(x: Parsley[BigInt]): Parsley[Num] = pos <**> x.map(Num(_) _)
    }
    object Var {
        def apply(x: Parsley[String]): Parsley[Var] = pos <**> x.map(Var(_) _)
    }
}
```

Notice how the structure of the new `apply` bridge constructors mirror the shape and type of the
companion class' constructor: where `Binding` requires a `String`, a `LetExpr` and a position,
`Binding.apply` requires a `Parsley[String]` and a `Parsley[LetExpr]`. Notice that the position is
absent from the builder: this is the entire point! If we need to remove the position (or add a
position to an existing node), we only need to make the change in the bridge constructor:

```scala
pos <**> (v, x).zipped(Binding(_, _) _) ===> (v, x).zipped(Binding(_, _))
```

This makes it really easy to change!

Now we can just use the bridge constructors in the main parser, and leave the
work of building the data to the `apply` itself. The advantage, as I alluded to above, is that
whether or not a position is required for a given node is not _at all_ visible to the parser that
uses its bridge: the bridge is the only place where this needs to be handled. The main parser
itself now looks like this:

```scala mdoc
object expressions {
    import parsley.expr.{precedence, Ops, InfixL, Prefix}
    import parsley.combinator.sepEndBy1

    import lexer.implicits.implicitSymbol
    import lexer.{number, fully, identifier}
    import ast._

    private lazy val atom: Parsley[Expr] =
        "(" ~> expr <~ ")" | Num(number) | Var(identifier)
    private lazy val expr = precedence[Expr](atom)(
        Ops(Prefix)("negate" as Neg),
        Ops(InfixL)("*" as Mul),
        Ops(InfixL)("+" as Add, "-" as Sub))

    private lazy val binding = Binding(identifier, "=" ~> letExpr)
    private lazy val bindings = sepEndBy1(binding, ";")
    private lazy val letExpr: Parsley[LetExpr] =
      Let("let" ~> bindings, "in" ~> expr) | expr

    val parser = fully(letExpr)
}
```

As you can see, _very_ little has changed. In fact, it's actually gotten slightly _nicer_. We no
longer need to worry about `map` or `lift` inside this parser, and can focus more on the
structure itself. Just to make it **very** clear: if we change our requirements for which nodes do
and do not require positions, this parser will **not change** in the slightest. There is still some
work left to do however: first it would be nice if the boilerplate introduced by each bridge
could be reduced; and position information needs to be added to `Add`, `Mul`, `Sub`, and `Neg`.

## Reducing Boilerplate with _**Generic Bridge Traits**_
So far, we've constructed four bridge constructors:

```scala
object Let {
    def apply(bindings: Parsley[List[Binding]], x: Parsley[Expr]): Parsley[Let] =
        pos <**> (bindings, x).zipped(Let(_, _) _)
}
object Binding {
    def apply(v: Parsley[String], x: Parsley[LetExpr]): Parsley[Binding] =
        pos <**> (v, x).zipped(Binding(_, _) _)
}
object Num {
    def apply(x: Parsley[BigInt]): Parsley[Num] = pos <**> x.map(Num(_) _)
}
object Var {
    def apply(x: Parsley[String]): Parsley[Var] = pos <**> x.map(Var(_) _)
}
```

As the number of AST nodes increase, it becomes more tedious to continue to define bridge
constructors and functions by hand. This can be improved by so-called ***generic bridge traits***.
This idea leverages the common structure between each of the bridge constructors and tries to build
a recipe for eliminating the boilerplate. This leverages another classic OOP design pattern, called
the _Template Method_ pattern:

> Define the skeleton of an algorithm in an operation, deferring some steps to subclasses. _Template
  Method_ lets subclassses redefine certain steps of an algorithm (called hooks) without changing
  the algorithm's structure.

Let's first desuguar these four objects a little to make the shared structure between `Let` and
`Binding` as well as between `Num` and `Var` more apparent:

```scala
object Let {
    def apply(bindings: Parsley[List[Binding]], x: Parsley[Expr]): Parsley[Let] =
        pos <**> (bindings, x).zipped(Let.apply(_, _) _)
}
object Binding {
    def apply(v: Parsley[String], x: Parsley[LetExpr]): Parsley[Binding] =
        pos <**> (v, x).zipped(Binding.apply(_, _) _)
}
object Num {
    def apply(x: Parsley[BigInt]): Parsley[Num] = pos <**> x.map(Num.apply(_) _)
}
object Var {
    def apply(x: Parsley[String]): Parsley[Var] = pos <**> x.map(Var.apply(_) _)
}
```

This exposes the fact that `Let()` is just sugar for `Let.apply()`, which is automatically
generated by the compiler into companion objects. Now simplify the scoping of these `apply` calls:

```scala
object Let {
    def apply(bindings: Parsley[List[Binding]], x: Parsley[Expr]): Parsley[Let] =
        pos <**> (bindings, x).zipped(this.apply(_, _) _)
}
object Binding {
    def apply(v: Parsley[String], x: Parsley[LetExpr]): Parsley[Binding] =
        pos <**> (v, x).zipped(this.apply(_, _) _)
}
object Num {
    def apply(x: Parsley[BigInt]): Parsley[Num] = pos <**> x.map(this.apply(_) _)
}
object Var {
    def apply(x: Parsley[String]): Parsley[Var] = pos <**> x.map(this.apply(_) _)
}
```

Now, the shared structure of each of these bridge constructors should hopefully be much clearer.
That doesn't mean they are all identical, indeed, the types vary, as do the arities of the
constructors themselves. But there is enough structure here to extract some shiny new bridge
template traits:

```scala mdoc:invisible
import parsley.syntax.zipped._
import parsley.position.pos
```
```scala mdoc
trait ParserBridgePos1[-A, +B] {
    // this is called the "hook": it's the hole in the template that must be implemented
    def apply(x: A)(pos: (Int, Int)): B
    // this is the template method, in this case the template for the bridge constructor
    def apply(x: Parsley[A]): Parsley[B] = pos <**> x.map(this.apply(_) _)
}

trait ParserBridgePos2[-A, -B, +C] {
    def apply(x: A, y: B)(pos: (Int, Int)): C
    def apply(x: Parsley[A], y: Parsley[B]): Parsley[C] =
        pos <**> (x, y).zipped(this.apply(_, _) _)
}
```

These are the two generic bridge traits that provide the implementations of our bridge constructors.
Obviously, there are many many more possible such traits. At the very least, it is also useful to
have "plain" versions that do not interact with positions at all also (these are provided by `parsley`
within `parsley.generic`):

```scala mdoc
trait ParserBridge1[-A, +B] {
    def apply(x: A): B
    def apply(x: Parsley[A]): Parsley[B] = x.map(this.apply(_))
}

trait ParserBridge2[-A, -B, +C] {
    def apply(x: A, y: B): C
    def apply(x: Parsley[A], y: Parsley[B]): Parsley[C] =
        (x, y).zipped(this.apply(_, _))
}
```

So, how are these used to help remove the boilerplate? Well, the companion objects for each of the
AST nodes will simply extend one of the generic bridge traits as appropriate:

```scala
object Let extends ParserBridgePos2[List[Binding], Expr, LetExpr]
object Binding extends ParserBridgePos2[String, LetExpr, Binding]
object Num extends ParserBridgePos1[BigInt, Num]
object Var extends ParserBridgePos1[String, Var]
```

Ahhhhh, much better! If position information was removed from say `Num`, then it would just have
to extend `ParserBridge1` instead, and no more changes need to be made!

## _Singleton Bridge_ for Precedence Ops
With the basics of bridge constructors (as well as generic bridge traits) under our belt, let's
explore how we might add position information to the arithmetic operators, which are used within
the `precedence` combinator. The problem with these is that the arguments to the bridge constructors
are not "immediately" available when we use them: it's the `precedence` combinator that provides
the arguments internally. This means we can't use the same shape of bridge here. That said, there
are a couple of different ways we can implement a bridge constructor for the operators:

1) Treat them just the same as `Num`, `Var`, `Let`, and `Binding` and create a bridge constructor
   that looks like `Neg("negate")`, `Mul("*")`, etc. This is the easiest, and they'll differ because
   of the type they return (they need to be parsers that return functions).
2) Build a special combinator `from` (or `<#`) that can transform the bridges for these operators into just
   looking like they do now. It would look like: `Neg from "negate"`, `Mul from "*"`, etc. This is
   slightly more effort to do, however I think it is more faithful to how these operators
   usually behave. The _Parser Bridge_ pattern treats arguments to the builder as arguments to the
   data-type, but the argument to `Neg` isn't the `()` returned by `"negate": Parsley[Unit]`, so
   _personally_ I think it's a bit jarring to use (1).

So that you can make the choice about which style you prefer, we'll go ahead and implement both,
with `Mul` using style (1) and the rest using style (2).

```scala
case class Mul(x: Expr, y: Expr)(val pos: (Int, Int)) extends Expr

object Mul {
    def apply(op: Parsley[Unit]): Parsley[(Expr, Expr) => Mul] =
        pos.map[(Expr, Expr) => Mul](p => Mul(_, _)(p)) <~ op
}

// or, alternatively, we can explicitly provide a new hook for our generic bridge trait:
object Mul extends ParserBridgePos1[Unit, (Expr, Expr) => Mul] {
    def apply(x: Unit)(pos: (Int, Int)): (Expr, Expr) => Mul = Mul(_, _)(pos)
}
```

Firstly, we can see this is a bit more effort than the previous bridge constructors. This is because
there is nothing for scala's inference to "latch" onto, since we are returning a function and not
providing the arguments here. While this is annoying, there isn't much we can do about it for this
style. Interestingly, here we can also see an example of where the `apply` hook method can be
overriden explicitly to adapt our existing bridge behaviour to a type that is **not** consistent
with the AST nodes type itself: this can be useful!

Let's see how style (2) compares. To accomplish this, we can think of the new `from` combinator as
being another template method provided by our generic bridge traits:

```scala mdoc:reset:invisible
import parsley.Parsley
import parsley.position.pos
```
```scala mdoc
import parsley.ap._

trait ParserBridgePos1[-A, +B] {
    def apply(x: A)(pos: (Int, Int)): B
    private def con(pos: (Int, Int)): A => B = this.apply(_)(pos)

    def apply(x: Parsley[A]): Parsley[B] = ap1(pos.map(con), x)
    def from(op: Parsley[_]): Parsley[A => B] = pos.map(con) <~ op
    final def <#(op: Parsley[_]): Parsley[A => B] = this from op

}

trait ParserBridgePos2[-A, -B, +C] {
    def apply(x: A, y: B)(pos: (Int, Int)): C
    private def con(pos: (Int, Int)): (A, B) => C = this.apply(_, _)(pos)

    def apply(x: Parsley[A], y: =>Parsley[B]): Parsley[C] = ap2(pos.map(con), x, y)
    def from(op: Parsley[_]): Parsley[(A, B) => C] = pos.map(con) <* op
    final def <#(op: Parsley[_]): Parsley[(A, B) => C] = this from op
}
```

In amongst this, I've factored out the application of our hook `apply` into the private function
`con`, which helps keep the code a bit cleaner. In the unsaturated case, the `from` combinator just
maps `con` over the position, leaving a residual function, and the saturated template `apply` case has been
cleaned up to use the `ap`-family of combinators, which allow for the application of a parser returning
a function to many parsers; this is similar to `lift`/`zipped`, but works for parsers instead of functions,
which is helpful here. As a side-effect of using `ap`, the laziness of the template `apply` combinator
has been improved, which is nice.

Now, by mixing in one of the generic bridge traits, we get two ways of using bridge constructors:
the first, `apply`, allows for fully-saturated application of a constructor to its parser arguments;
and the second, `from`, allows for fully-unsaturated application of a constructor to its arguments,
whilst still handling the position tracking. You can imagine that partially-saturated bridge
constructors can also be templated in a similar way, perhaps to fit some unconventional use-cases.
In this case, here are the definitions of the companion objects for `Add`, `Sub` and `Neg` now:

```scala
case class Add(x: Expr, y: Expr)(val pos: (Int, Int)) extends Expr
case class Sub(x: Expr, y: Expr)(val pos: (Int, Int)) extends Expr
case class Neg(x: Expr)(val pos: (Int, Int)) extends Expr

object Add extends ParserBridgePos2[Expr, Expr, Add]
object Sub extends ParserBridgePos2[Expr, Expr, Sub]
object Neg extends ParserBridgePos1[Expr, Neg]
```

To make it clear, this automatically gives us the option to use `Add(p, q)` _or_ `Add from "+"`, and
its the latter that we'll want to use inside the `precedence` combinator:

```scala
object expressions {
    import parsley.expr.{precedence, Ops, InfixL, Prefix}
    import parsley.combinator.sepEndBy1

    import lexer.implicits.implicitSymbol
    import lexer.{number, fully, identifier}
    import ast._

    private lazy val atom: Parsley[Expr] =
        "(" ~> expr <~ ")" | Num(number) | Var(identifier)
    private lazy val expr = precedence[Expr](atom)(
        Ops(Prefix)(Neg from "negate"),
        Ops(InfixL)(Mul("*")),
        Ops(InfixL)(Add from "+", Sub from "-"))

    private lazy val binding = Binding(identifier, "=" ~> letExpr)
    private lazy val bindings = sepEndBy1(binding, ";")
    private lazy val letExpr: Parsley[LetExpr] =
      Let("let" ~> bindings, "in" ~> expr) | expr

    val parser = fully(letExpr)
}
```

### Abstracting Again
In the refined definition of our generic bridge traits we supported the _Singleton Bridge_
parsing design pattern by allowing the companion object itself to "appear" like a parser itself.
However, if we peer in closely we can even spot some common structure between the two different
`from` implementations from above:

```scala mdoc:invisible:reset
import parsley.Parsley
import parsley.position.pos
```
```scala mdoc
trait ParserBridgePos1[-A, +B] {
    def apply(x: A)(pos: (Int, Int)): B
    private def con(pos: (Int, Int)): A => B = this.apply(_)(pos)

    def from(op: Parsley[_]): Parsley[A => B] = pos.map(con) <* op
    final def <#(op: Parsley[_]): Parsley[A => B] = this from op
}

trait ParserBridgePos2[-A, -B, +C] {
    def apply(x: A, y: B)(pos: (Int, Int)): C
    private def con(pos: (Int, Int)): (A, B) => C = this.apply(_, _)(pos)

    def from(op: Parsley[_]): Parsley[(A, B) => C] = pos.map(con) <* op
    final def <#(op: Parsley[_]): Parsley[(A, B) => C] = this from op
}
```

Both bridges are _almost_
identical except for the shape returned by their `con` function. It's possible to abstract one more
layer and introduce another couple of traits to help factor
the common code:

```scala mdoc:invisible:reset
import parsley.Parsley
import parsley.position.pos
import parsley.ap._
```
```scala mdoc
trait ParserSingletonBridgePos[+A] {
    protected def con(pos: (Int, Int)): A
    def from(op: Parsley[_]): Parsley[A] = pos.map(this.con(_)) <* op
    final def <#(op: Parsley[_]): Parsley[A] = this from op
}

trait ParserBridgePos1[-A, +B] extends ParserSingletonBridgePos[A => B] {
    def apply(x: A)(pos: (Int, Int)): B
    def apply(x: Parsley[A]): Parsley[B] = ap1(pos.map(con), x)

    override final def con(pos: (Int, Int)): A => B = this.apply(_)(pos)
}

trait ParserBridgePos2[-A, -B, +C] extends ParserSingletonBridgePos[(A, B) => C] {
    def apply(x: A, y: B)(pos: (Int, Int)): C
    def apply(x: Parsley[A], y: =>Parsley[B]): Parsley[C] = ap2(pos.map(con), x, y)

    override final def con(pos: (Int, Int)): (A, B) => C = this.apply(_, _)(pos)
}
```

This provides a _modest_ improvement over the original versions.

### Adding Errors
The generic bridges found in `parsley.generic` offer one additional component that is absent from the
descriptions above: they all extend `generic.ParserSingleBridge`, which in turn extends
`generic.ErrorBridge`. This trait exposes hook methods for `labels` and `reason`, which allow for
error messages to be associated with the bridge, and in exchange provides the template `error` combinator:
this is supposed to be called within the bridge to annotate a parser with the provided error messages.
Our definitions of `ParserBridgePos1` and `ParserBridgePos2` can also benefit from this:

```scala mdoc:invisible:reset
import parsley.Parsley
import parsley.position.pos
import parsley.ap._
```
```scala mdoc
import parsley.generic

trait ParserSingletonBridgePos[+A] extends generic.ErrorBridge {
    protected def con(pos: (Int, Int)): A
    def from(op: Parsley[_]): Parsley[A] = error(pos.map(this.con(_)) <* op)
    final def <#(op: Parsley[_]): Parsley[A] = this from op
}

trait ParserBridgePos1[-A, +B] extends ParserSingletonBridgePos[A => B] {
    def apply(x: A)(pos: (Int, Int)): B
    def apply(x: Parsley[A]): Parsley[B] = error(ap1(pos.map(con), x))

    override final def con(pos: (Int, Int)): A => B = this.apply(_)(pos)
}

trait ParserBridgePos2[-A, -B, +C] extends ParserSingletonBridgePos[(A, B) => C] {
    def apply(x: A, y: B)(pos: (Int, Int)): C
    def apply(x: Parsley[A], y: =>Parsley[B]): Parsley[C] = error(ap2(pos.map(con), x, y))

    override final def con(pos: (Int, Int)): (A, B) => C = this.apply(_, _)(pos)
}
```

## The Final Parser

This is our final parser which compiles fine, and tracks positions correctly. As we can see, all of
the work we needed to handle the position tracking, AST node construction, whitespace handling and
lexing has all been abstracted elsewhere, leaving a clean core. For completeness, here's the entire
file:

@:format(html)
<details>
<summary>The full completed parser</summary>
<p>
@:@

```scala mdoc
object lexer {
    import parsley.token.{Lexer, Basic}
    import parsley.token.descriptions.{LexicalDesc, NameDesc, SymbolDesc}

    private val desc = LexicalDesc.plain.copy(
        nameDesc = NameDesc.plain.copy(
            identifierStart = Basic(_.isLetter),
            identifierLetter = Basic(_.isLetter),
        ),
        symbolDesc = SymbolDesc.plain.copy(
            hardKeywords = Set("negate", "let", "in"),
            hardOperators = Set("*", "+", "-"),
        ),
    )

    private val lexer = new Lexer(desc)

    val identifier = lexer.lexeme.names.identifier
    val number = lexer.lexeme.natural.decimal

    def fully[A](p: Parsley[A]) = lexer.fully(p)
    val implicits = lexer.lexeme.symbol.implicits
}

object ast {
    sealed trait LetExpr
    case class Let(bindings: List[Binding], x: Expr)(val pos: (Int, Int)) extends LetExpr
    case class Binding(v: String, x: LetExpr)(val pos: (Int, Int))

    sealed trait Expr extends LetExpr
    case class Add(x: Expr, y: Expr)(val pos: (Int, Int)) extends Expr
    case class Mul(x: Expr, y: Expr)(val pos: (Int, Int)) extends Expr
    case class Sub(x: Expr, y: Expr)(val pos: (Int, Int)) extends Expr
    case class Neg(x: Expr)(val pos: (Int, Int)) extends Expr
    case class Num(x: BigInt)(val pos: (Int, Int)) extends Expr
    case class Var(x: String)(val pos: (Int, Int)) extends Expr

    object Let extends ParserBridgePos2[List[Binding], Expr, LetExpr]
    object Binding extends ParserBridgePos2[String, LetExpr, Binding]
    object Add extends ParserBridgePos2[Expr, Expr, Add]
    object Mul extends ParserBridgePos2[Expr, Expr, Mul]
    object Sub extends ParserBridgePos2[Expr, Expr, Sub]
    object Neg extends ParserBridgePos1[Expr, Neg]
    object Num extends ParserBridgePos1[BigInt, Num]
    object Var extends ParserBridgePos1[String, Var]
}

object expressions {
    import parsley.expr.{precedence, Ops, InfixL, Prefix}
    import parsley.combinator.sepEndBy1

    import lexer.implicits.implicitSymbol
    import lexer.{number, fully, identifier}
    import ast._

    private lazy val atom: Parsley[Expr] =
        "(" ~> expr <~ ")" | Num(number) | Var(identifier)
    private lazy val expr = precedence[Expr](atom)(
        Ops(Prefix)(Neg from "negate"),
        Ops(InfixL)(Mul from "*"),
        Ops(InfixL)(Add from "+", Sub from "-"))

    private lazy val binding = Binding(identifier, "=" ~> letExpr)
    private lazy val bindings = sepEndBy1(binding, ";")
    private lazy val letExpr: Parsley[LetExpr] =
      Let("let" ~> bindings, "in" ~> expr) | expr

    val parser = fully(letExpr)
}
```
@:format(html)
</p>
</details>
@:@

As a last thought, it's worth reinforcing that the parser bridge pattern is just a guideline: it's
free to take any shape you need it to, so experiment with what works well for your own structures.
The value in it really in how easy you can separate the concerns of building a structure from the
parser for the grammar. Of course, there is nothing to say you _have_ to use it either. If you
are fine with writing the bridge constructors inline in the parser, then do it! It might be that you
find the extra lines of code in the file that defines your AST to be too grating. Really this is just
another exercise in how leveraging Scala's functionality when we make our parsers can help us
abstract and manage our code, once again showcasing the limitless flexibility combinators
provide.
