{%
laika.versioned = true
laika.title = "`infix`"
parsley.tabname = "Infix Chain Combinators"
laika.site.metadata.description = "This page describes how to chain together values and operators."
%}

# Infix Chain Combinators
The `parsley.expr.infix` module contains combinators for
the strongly-typed abstraction of operators to values in
expressions. This allows `parsley` to handle left recursion
idiomatically. To distinguish between these chains and those
found in [parsley.expr.chain][Chain Combinators], it is
recommended to always import this module qualified as
`import parsley.expr.infix`.

@:callout(info)
*The Scaladoc for this page can be found at [`parsley.expr.infix`](@:api(parsley.expr.infix$)).*
@:@

## Binary Chains
The stronger types implied by `infix` means that only binary
chains are useful to define. Both `infix.left1` and
`infix.right1` behave the same as `chain.left1` and
`chain.right1`. The difference is in the types:

```scala
def left1[A, B](p: Parsley[A], op: Parsley[(B, A) => B])(implicit wrap: A => B): Parsley[B]
def right1[A, B](p: Parsley[A], op: Parsley[(A, B) => B])(implicit wrap: A => B): Parsley[B]
```

As both combinators return type `B`, the `B` denotes where
recursion can appear. The `wrap` function is a way of converting
the final `p` value in either bracketing to be a value of type
`B` to fit into the operator. When `A <: B`, the `A <:< B`
instance, which is of type `A => B` will be summoned, meaning the
terminal `p` is upcast into a `B`.  When `A` and `B` are the same
type, `A =:= B` is summoned, which acts as the `identity`
function, and they behave the same as `chain.left1` or `chain.right1`.

Ultimately, these chains are useful when the parser writer wants further guarantees that the parser adheres to the grammar precisely: when strong types are employed, `infix.left1` and
`infix.right1` cannot be substituted for each other.

As an example:

```scala mdoc:to-string:nest
import parsley.character.digit
import parsley.expr.infix
import parsley.syntax.character.stringLift

sealed trait Expr
case class Add(x: Expr, y: Num) extends Expr
case class Num(n: Int) extends Expr

lazy val expr = infix.left1(num)("+".as(Add(_, _)))
lazy val num = digit.foldLeft1(0)((n, d) => n * 10 + d.asDigit).map(Num(_))
expr.parse("56+43+123")
```

In the above example, the `Add` constructor is recursive on the
left,  but `Num` must appear on the right. As `Num` and `Add`
share a common supertype `Expr`, this is what the chains will return. To illustrate what happens if `right1` was used instead:

```scala mdoc:fail
lazy val badExpr = infix.right1(num)("+".as(Add(_, _)))
```

Notice that the error message here refers to the type `(A, C) => B`. In practice, to help reduce type ascriptions, the explicit `C >: B` is used as well -- in the working example, `C` is `Expr`, `A` is `Num`, and `B` is `Add`. The wrap is actually `A => C`, which in this case is provided by `Num <:< Expr`.
