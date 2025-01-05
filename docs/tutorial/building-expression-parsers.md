{%
laika.versioned = true
laika.site.metadata.description = "How to handle left-recursion and precedence in a parser."
%}
# Building Expression Parsers

```scala mdoc:invisible
import scala.annotation.unused
```

@:callout(info)
This page builds from the ground up on expression parsing. For a less
discussion-based explanation see [precedence](../api-guide/expr/precedence.md), as well as [chain combinators](../api-guide/expr/chain.md) and [heterogeneous chain combinators](../api-guide/expr/infix.md) for more specific use-cases.
@:@

Expression parsing is a ubiquitous problem in parsing. It concerns the correct reading of
operators and values, which are usually organised into precedence and fixities. For the
purposes of this page a fixity will represent both the fixity _and_ the associativity: infix-left,
infix-right, prefix, and postfix. For example, this is a grammar for reading simple expressions
consisting of numbers, brackets, addition, subtraction and multiplication with the standard
precedences and left associativity.

```ebnf
<number> ::= <digit>+
<expr> ::= <expr> '+' <term> | <expr> '-' <term> | <term>
<term> ::= <term> '*' <atom> | <atom>
<atom> ::= '(' <expr> ')' | <number>
```

Here, the precedence is encoded by the fact that `<expr>` contains `<term>`, but `<term>`
only contains `<atom>`. The `<expr>` on the left of the `'+'` denotes that addition binds
more tightly to the left, which is what we expect.

## The Problem with Left-Recursion
For a first atomic, let's directly translate this grammar into Parsley (for now, we'll
parse into an `Int`: behold, the magic of combinators!):

```scala mdoc:silent
import parsley.Parsley, Parsley.atomic
import parsley.character.digit
import parsley.syntax.character.charLift
import parsley.syntax.zipped._

// Standard number parser
val number = digit.foldLeft1[Int](0)((n, d) => n * 10 + d.asDigit)

lazy val expr: Parsley[Int] =
  atomic((expr <* '+', term).zipped(_ + _)) |
  (expr <* '-', term).zipped(_ - _) |
  term
lazy val term: Parsley[Int] = (term <* '*', atom).zipped(_ * _) | atom
lazy val atom: Parsley[Int] = '(' *> expr <* ')' | number
```

This parser has a few glaring issues: for a start, the `atomic` is causing excessive backtracking!
While there are ways to improve this, the real problem here is
the _left-recursion_. Imagine you are evaluating this parser, first you look at `expr`, and then
your first task is to evaluate `expr`! In fact, due to the strictness of Parsley's combinators, this example breaks before the parser runs: on Scala 2, it will `StackOverflowError` at runtime when constructing the parser, and on Scala 3, it
will report an infinitely recursive definition for `expr` and `term`. The solution is to turn to the `chain` combinators, but before we do that, let's
eliminate the atomics and refactor it a little to make the transition less jarring:

```scala mdoc:nest:silent
import parsley.Parsley, Parsley.atomic
import parsley.character.digit
import parsley.syntax.character.charLift

// Standard number parser
val number = digit.foldLeft1[Int](0)((n, d) => n * 10 + d.asDigit)

val add = (y: Int) => (x: Int) => x + y
val sub = (y: Int) => (x: Int) => x - y
val mul = (y: Int) => (x: Int) => x * y

lazy val expr: Parsley[Int] =
  atomic(expr <**> ('+'.as(add) <*> term)) |
  expr <**> ('-'.as(sub) <*> term) |
  term
lazy val term: Parsley[Int] = term <**> ('*'.as(mul) <*> atom) | atom
lazy val atom: Parsley[Int] = '(' ~> expr <~ ')' | number
```

The first step is to perform the translation from the previous post, where we make the operator
result a function and apply that (flipped) to the right hand side (with `<*>`) and then the left
(with `<**>`). Now, in this form, hopefully you can notice we've exposed the leading `expr` so that
its on its own: now we can factor a bit more:

```scala mdoc:nest:silent
lazy val expr: Parsley[Int] =
  expr <**> (('+'.as(add) <*> term) | ('-'.as(sub) <*> term)) |
  term
```

Now we've eliminated the "backtracking" (if only we could make it that far!), but we can right factor
the `|` too to obtain the simplest form for the parser:

```scala mdoc:nest:silent
lazy val expr: Parsley[Int] =
  expr <**> (('+'.as(add) | '-'.as(sub)) <*> term) |
  term
```
```scala mdoc:invisible
lazy val _ = expr: @unused
```

Now, at this point, I could demonstrate how to left-factor this grammar and produce something that
is right recursive whilst preserving left-associativity. However, there isn't much point in doing
this, as now we are in a good position to use the `chain.left1` combinator, which perfectly embodies
the translation.

## Using `expr.chain`
The left-recursion problem is not a new one, the parser combinator community has known about it
for a long time. For parser combinator libraries it is necessary to _left-factor_ the grammar.
Thankfully, the left-factoring algorithm can be itself encoded nicely as a combinator: this is
embodied by the `chain`-family. Here is the same example as before, but fixed using `chain.left1`:

```scala mdoc:silent:nest
import parsley.Parsley
import parsley.character.digit
import parsley.syntax.character.charLift
import parsley.expr.chain

// Standard number parser
val number = digit.foldLeft1[Int](0)((n, d) => n * 10 + d.asDigit)

val add = (x: Int, y: Int) => x + y
val sub = (x: Int, y: Int) => x - y

// chain.left1[A](p: Parsley[A])(op: Parsley[(A, A) => A]): Parsley[A]
lazy val expr: Parsley[Int] = chain.left1(term)('+'.as(add) | '-'.as(sub))
lazy val term               = chain.left1(atom)('*' as (_ * _))
lazy val atom               = '(' ~> expr <~ ')' | number
```

The structure of the parser is roughly the same, however now you'll notice that `expr` and `term`
are no longer self-recursive, and neither `term` nor `atom` need to be lazy (or have explicit types).

@:todo(The first type parameter represents the type of the `atom`s, and the second is the type of the `term`
itself: unlike mainstream parser combinator libraries in Haskell, Parsley allows these types to vary \(see
the very end of this page\).)

To make the relationship very clear between what we had before and what we have now, observe that
the transformation from recursive to `chains` follows these shape:

```scala
self <**> (op <*> next) | next        == chain.left1(next)(op)  // flipped op
self <**> op <*> next | next          == chain.left1(next)(op)  // normal op
next <**> (op <*> self </> identity)  == chain.right1(next)(op) // no backtracking, flipped
atomic(next <**> op <*> self) | next  == chain.right1(next)(op) // backtracking, normal op
```

In this parser, the nesting of the chains dictates the precedence order (again, terms are found _inside_
expressions and atoms _inside_ terms). Since the addition and subtraction are on the same level, they
belong in the same chain. The `left1` indicates that the operator/s are left-associative and that there
should be at least _one_ of the next layer down. There are also `chain.right1`, `chain.prefix`, and
`chain.postfix` combinators. The building of these parsers, however, is fairly mechanical, and it is
tiresome to keep finding new names for new layers of the precedence table. For instances where there
is more than one chain interacting together then `expr.precedence` comes in handy (but note that
`expr.precedence` is complete overkill to replace a single chain!).

## Using `expr.precedence`
The final form of this parser uses a expression parser builder, called `precedence`. Since Parsley parsers
are implemented in pure Scala, there is nothing to stop you from developing tools like this yourself: the
ability to work with parsers as values and develop combinators with them is the biggest advantage of
the approach. That being said, most combinator libraries provide this sort of functionality out of the box
and Parsley is no exception. Let's see the same parser one last time and see what's changed:

```scala mdoc:nest:silent
import parsley.Parsley
import parsley.character.digit
import parsley.syntax.character.charLift
import parsley.expr.{precedence, Ops, InfixL}

val number = digit.foldLeft1[Int](0)((n, d) => n * 10 + d.asDigit)

lazy val expr: Parsley[Int] = precedence[Int]('(' ~> expr <~ ')', number)(
    Ops(InfixL)('*' as (_ * _)),
    Ops(InfixL)('+' as (_ + _), '-' as (_ - _)))
```

This is a _lot_ smaller! The way `precedence` works is that it is first provided with the
`atom`s of the expression, and then each precedence level in turn (as many as needed),
starting with the tightest binding operators. These levels are provided in the `Ops`, which
take a fixity, and then any number of parsers which return functions matching the fixity given.
Under the hood it will form the same nested chains that were used in the previous section.
In essence, there is no practical difference between the two implementations.

The precedence table can actually also be reversed so that it works the other way round:

```scala mdoc:nest
lazy val expr: Parsley[Int] = precedence[Int](
    Ops[Int](InfixL)('+' as (_ + _), '-' as (_ - _)),
    Ops[Int](InfixL)('*' as (_ * _)))(
    '(' ~> expr <~ ')', number)
```

But due to the ordering that type inference happens, this form is a bit more cumbersome.

As mentioned before, the fixity given to `Ops` influences what type the operators need to have.
This works by a Scala feature called _path-dependent_ typing, which is extraordinarily useful.
If you want to know more about this, see the relevant sub-section: you don't need to know about it
or understand it to use `precedence`, however.

There is still a little more to this story though. So far we've been working with a homogenous
datatype: every level in the precedence table shares the same type `Int`. Now, in an abstract syntax
tree, which is the far more common result of parsing, you _could_ represent all expressions
homogenously (which I call a _monolithic_ AST). But sometimes, it's desirable to maintain stronger
guarantees about how the AST is structured, and for that we need a heterogenous precedence table.

### Generalising `precedence` with `GOps`, `SOps`, and `Levels`
#### Subtyped ASTs with `SOps`
In some circumstances, it might be desirable to change the type of the parsers at each layer of the
precedence table. This allows for a more strongly-typed AST, for example. Compared to Haskell, this
can be easily achieved in Scala using subtyping.

For example, we can make an AST for our expressions like so:

```scala mdoc
sealed trait Expr
case class Add(x: Expr, y: Term) extends Expr
case class Sub(x: Expr, y: Term) extends Expr

sealed trait Term extends Expr
case class Mul(x: Term, y: Atom) extends Term

sealed trait Atom extends Term
case class Number(x: Int) extends Atom
case class Parens(x: Expr) extends Atom
```

The magic of subtyping means that `Number(10)` is a valid value of type `Expr`. That being said,
we have a guarantee that an `Expr` can only be found inside a `Mul` if it is wrapped in `Parens`:
since `Expr` is not a subtype of `Term` or `Atom`, `Mul(Add(Number(6), Number(7)), Number(8))` does
_not_ type-check!

Let's see what happens if we try and use our existing `precedence` knowledge with `Ops`:

```scala mdoc:nest:fail
val mul = (x: Expr, y: Expr) => Mul(x, y)
val add = (x: Expr, y: Expr) => Add(x, y)
val sub = (x: Expr, y: Expr) => Sub(x, y)

lazy val atom: Parsley[Atom] = number.map(Number) | '(' ~> expr.map(Parens) <~ ')'
lazy val expr = precedence[Expr](atom)(
  Ops(InfixL)('*' as mul),
  Ops(InfixL)('+' as add, '-' as sub))
```

The problem is that, though all `Term`s are `Expr`s (and ditto
for `Atom`), we are forced to create operators of the shape `(Expr, Expr) => Expr` to fit into the
precedence table, but we can't guarantee that those `Expr`s we are passing into the function are
actually `Term`s (even though we know intuitively that they will be). In other words,
`(Term, Atom) => Term` is not a subtype of `(Expr, Expr) => Expr`!

So, how do we fix this? Well, we need to stop using `Ops` and use `SOps`: instead of requiring an
`(A, A) => A` operator for `InfixL`, `SOps` will demand those with shape `(B, A) => B` such that
`A <: B`. So, does our `(Term, Atom) => Term` match this type? Yes: `A = Atom`, `B = Term` and
`Atom <: Term`; all is good. Why do we require that `A <: B` exactly? Well, consider that we didn't
read any multiplication operators, then we are going to be handing just an `Atom` to the layer
above, but we are making the claim that we produce `Term`s. Of course, this is ok because `Atom`s
are themselves `Term`s.

Unfortunately, we can't just provide `SOps` as variadic arguments to the combinator, since they all
have different types to each other (that is the point, after all). Instead we use a heterogenous
list of precedence levels called, well, `Levels`.

```scala mdoc
trait Levels[+A]
case class Atoms[+A](atoms: Parsley[A]*) extends Levels[A]
// and
case class Level[A, B](
    nextLevels: Levels[A],
    ops: Ops[A, B])
  extends Levels[B]
```

Basically, the type parameter to `Levels` is saying that we *produce* values of
type `A` from the outer-most level in the structure. There are two choices of constructor in the list: `Atoms` is the end of the
list, it produces `A`s. The equivalent to `::`,
`Level[A, B]` is a bit more complex: it says that, if you give it a precedence table that produces `A`s, then it can use its own operators that work on `A` to produce values of type `B`. As a result, the larger table produces `B`s.

Now, to make life nicer for us, the `Levels` list supports the common-place Scala collections
operators of `+:` and `:+`, which can be used in place of `Level`. Just like other Scala
collections, the rest of the table appears on the side of the `:`. As a result, we can build tables
like:

```scala
Atoms(atom1, atom2, .., atomN) :+ ops1 :+ ops2 :+ .. :+ opsN
// or
opsN +: .. +: ops2 +: ops1 +: Atoms(atom1, atom2, .., atomN)
```

The first form is the tightest first approach, and the second is the weakest first approach. So, what does our parser look like if we use `Levels` and `SOps`?

```scala mdoc:reset:silent
import parsley.Parsley
import parsley.character.digit
import parsley.syntax.character.charLift
import parsley.expr.{precedence, SOps, InfixL, Atoms}

val number = digit.foldLeft1[Int](0)((n, d) => n * 10 + d.asDigit)

sealed trait Expr
case class Add(x: Expr, y: Term) extends Expr
case class Sub(x: Expr, y: Term) extends Expr

sealed trait Term extends Expr
case class Mul(x: Term, y: Atom) extends Term

sealed trait Atom extends Term
case class Number(x: Int) extends Atom
case class Parens(x: Expr) extends Atom

lazy val expr: Parsley[Expr] = precedence {
  Atoms(number.map(Number), '(' ~> expr.map(Parens) <~ ')') :+
  SOps(InfixL)('*' as Mul) :+
  SOps(InfixL)('+' as Add, '-' as Sub)
}
```

Not so bad! We've constructed the `Levels` list using `:+`, so this is strongest-first.
This time, if we turn the list around it isn't going to make us need to add type-annotations
like it did when we turned the `Ops` based table round earlier. Nice! An extra advantage of
using this approach now is that if we tried to use `InfixR` instead of `InfixL`, this happens:

```
type mismatch;
    found   : (Term, Atom) => Term
    required: (Atom, Term) => Term
```

This means that, by using `SOps`, we get a guarantee that our parser correctly matches the intended
associativity advertised by our ASTs constructors!

#### Non-Subtyped Heterogenous ASTs with `GOps`

So far we've seen how to generalise our expression parsers to work with heterogenous trees that rely
on subtyping. However, there may be cases where the subtyping is undesirable, or otherwise not
possible (for example, if you want layers from `Int` to `Expr`) but we still want these strongly typed guarantees about the shape of the tree. In this case we would change the data-type as follows:

```scala mdoc:nest
sealed trait Expr
case class Add(x: Expr, y: Term) extends Expr
case class Sub(x: Expr, y: Term) extends Expr
case class OfTerm(t: Term) extends Expr

sealed trait Term
case class Mul(x: Term, y: Atom) extends Term
case class OfAtom(x: Atom) extends Term

sealed trait Atom
case class Number(x: Int) extends Atom
case class Parens(x: Expr) extends Atom
```

Now the question is, how do we use the `precedence` parser now? The types of each of
these constructors no longer match `(B, A) => B` with `A <: B`! This is where `GOps` comes in. It's
very similar to `SOps`, except it doesn't come with the constraint that `A` is a subtype of `B`.
Instead, a `GOps` constructor requires you to provide a function of type `A => B` too! In our case,
these will correspond to the `OfAtom` and `OfTerm` functions from above. Note that, if there are
any implicit conversion available from `A` to `B`, `GOps` will happily use those (this includes the
implicit conversions called `A =:= A` and `A <:< B` for type equality and subtyping respectively:
`GOps` can implement the behaviour of `Ops` and `SOps` via these conversions). So, what does this
look like in practice?

```scala mdoc:silent
import parsley.Parsley
import parsley.character.digit
import parsley.syntax.character.charLift
import parsley.expr.{precedence, GOps, InfixL, Atoms}

val number = digit.foldLeft1[Int](0)((n, d) => n * 10 + d.asDigit)

lazy val expr: Parsley[Expr] = precedence {
  Atoms(number.map(Number), '(' ~> expr.map(Parens) <~ ')') :+
  GOps[Atom, Term](InfixL)('*' as Mul)(OfAtom) :+
  GOps[Term, Expr](InfixL)('+' as Add, '-' as Sub)(OfTerm)
}
```

Not so different from the original using `SOps`, but if you can allow subtyping in your AST, you
can use the much less brittle `SOps` form. What makes it brittle? Well, notice that this time we've
had to manually specify the types that each level deals with: this is because, without a subtyping
constraint, Scala is reluctant to make `Mul` be of type `(Term, Atom) => Term`. Instead it makes it
`(Term, Atom) => Mul` and complains that `OfAtom` hasn't got type `Atom => Mul`. Oops!

By the way, you can actually intermingle `Ops`, `SOps`, _and_ `GOps` all in the same table, just as
long as you are using `Levels`. Each of them are just builders for values of type `Ops[A, B]`.

### Path-Dependent Typing and `Ops`/`SOps`/`GOps`
To support the advertised behaviour that the type of an operator depends on the fixity it has,
the `Fixity` trait has an _abstract type_ called `Op`. Let's take the machinery behind the simpler `Ops` as an
example.

```scala
sealed trait Fixity {
    type Op[A, B]
}

object Ops {
    def apply[A](fixity: Fixity)(ops: Parsley[fixity.Op[A, A]]*): Ops[A, A] = ???
}
```

This is saying that the types of the parsers we pass to a call to `Ops.apply` should depend on the
type of the `Op` supported by the `fixity`. For instance, let's take `InfixL` and `Prefix`:

```scala
case object InfixL extends Fixity {
    override type Op[-A, B] = (B, A) => B
}
case object Prefix extends Fixity {
    override type Op[A, B] = B => B
}
```

Why `Op` works with `A`s and `B`s is explained in the very last subsection, so for now just always assume that `A =:= B`. Now observe the types of the partial applications of `Ops.apply`
to the different fixities:

```scala
def infixLefts[A](ops: Parsley[(A, A) => A]*): Ops[A, A] =
  Ops(InfixL)(ops: _*)
def prefixes[A](ops: Parsley[A => A]*): Ops[A, A] =
  Ops(Prefix)(ops: _*)
```

The path-dependent type of `fixity.Op[A, A]` allows the types of the parsers to change accordingly.
There is a similar story for the `GOps` and `SOps` objects, but they instead rely on `Levels` as
opposed to variadic arguments.

### Afternote: Why `(B, A) => B` and `B => B`?
The types given to each fixity are as follows:

* `InfixL` is `(B, A) => B`
* `InfixR` is `(A, B) => B`
* `Prefix` and `Postfix` are both `B => B`

This might seem confusing at first: why, for instance, do the unary operators not mention `A` at all?
Well, let's first understand why `(B, A) => B` is appropriate for left-associative things but not
right ones.

```scala mdoc:reset
sealed trait Expr
case class LOp(x: Expr, y: Int) extends Expr
case class ROp(x: Int, y: Expr) extends Expr
case class Number(x: Int) extends Expr
```

Notice that `LOp(LOp(Number(6), 5), 4)` is ok, because the right hand argument to `LOp` is always an
`Int` and the left-hand argument is always an expression. In the case of `6`, `Int` is not an `Expr`,
so we wrap it up in the `Number` constructor. So, for `LOp`, if we take `A = Int` and `B = Expr`,
it has the shape `(B, A) => B`. On the other hand, `ROp(ROp(Number(6), 5), 4)` is not ok, because
`ROp(...)` is not an `Int`! This justifies the `(A, B) => B` type: like-expressions can appear on the
right, but not the left. The level for this would be `GOp[Int, Expr](InfixL)('@'.as(LOp))(Number)` or
`GOp[Int, Expr](InfixR)('@'.as(ROp))(Number)` (notice that switching them round wouldn't type-check!)

For `Prefix` and `Postfix` it's a similar story:

```scala mdoc
sealed trait BoolExpr
case class Not(x: BoolExpr) extends BoolExpr
case class Literal(b: Boolean) extends BoolExpr
```

We would like to be able to write `Not(Not(Literal(False)))`, which means that `Not` needs to accept
the same type as itself. This explains `B => B`, and in this case, the booleans themselves need to be
wrapped up with `Literal` of shape `A => B`. This is the same role of `Number` before, which also has
shape `A => B`. The level for this would be `GOp[Boolean, BoolExpr]("not" #> Not)(Literal)`.
