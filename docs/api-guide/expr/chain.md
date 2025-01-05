{%
laika.versioned = true
laika.title = "`chain`"
parsley.tabname = "Chain Combinators"
laika.site.metadata.description = "This page describes how to chain together values and operators."
%}

# Chain Combinators
The `parsley.expr.chain` module contains a variety of combinators for abstracting, most commonly, the application of
operators to values in expressions. This allows `parsley` to
handle *left recursion* idiomatically. To distinguish between
these chains and the functionality found in [`parsley.expr.infix`][Infix Chain Combinators], it is recommended to always import this module qualified as `import parsley.expr.chain` -- except for `postfix` and `prefix`.

@:callout(info)
*The Scaladoc for this page can be found at [`parsley.expr.chain`](@:api(parsley.expr.chain$)).*
@:@

## Binary Chains
The first kind of chains found in `chain` are the binary chains,
which handle infix application of a binary operator to values
in either a left- or right-associative way. These are called `chain.left1` or `chain.right1`. The `1` here means that there
must be at least one value present, though there may be no
operators. As an example:

> <h3>`p op p op p op p op p`</h3>

The above can be parsed using `chain.left1(p)(op)` to have the
effect of parsing like:

> <h3>`(((p op p) op p) op p) op p`</h3>

It can also be parsed using `chain.right1(p)(op)` to have the
effect of parsing like:

> <h3>`p op (p op (p op (p op p)))`</h3>

@:callout(warning)
Both of these combinators share the same type, where the parser
`p: Parsley[A]`, and the parser `op: Parsley[(A, A) => A]`. This
means that the two combinators can be freely swapped between
in an implementation. This is useful when the grammar being
encoded for is fully-associative and the associativity within
the parser is an implementation detail.

However, if more type-safety is desired, the `infix.left1` and
`infix.right1` combinators may be more appropriate.
@:@

## Unary Chains
The other kind of chains found in `chain` are unary chains, which
handle repeated prefix or postfix application of an operator to
a single value. These are called `chain.prefix` and
`chain.postfix`. There also `1` variants of these combinators, which will be discussed later.

Given input of shape:

> <h3>`p op op op op`</h3>

The combinator `postfix(p)(op)` will parse the input and apply
results such that it would look like:

> <h3>`(((p op) op) op) op`</h3>

Similarly, given input of shape:

> <h3>`op op op op p`</h3>

The combinator `prefix(p)(op)` will parse the input and apply
results such that it would look like:

> <h3>`op (op (op (op p)))`</h3>

@:callout(info)
Unlike `chain.left1` and `chain.right1`, there is no `infix`
equivalent for `prefix` and `postfix`. This is because a
refined type will not add much to the way the combinators
operate.
@:@

### Ensuring an operator exists
Unlike the difference between `chain.left` and `chain.left1`,
which allows for an absence of terminal value; `prefix` vs `prefix1` describe whether or not an operator is required
or not. This is enforced by the type signature of the operations
themselves:

```scala
def prefix1[A, B <: A](p: Parsley[A])(op: Parsley[A => B]): Parsley[B]
```

Given that `B` is a subtype of `A`, it is not possible for the
`p`'s result of type `A` to be the final return value of type `B`. As such, an operator must be parsed which wraps the `A`
into a `B`. The subtying relation then allows a nested application of an operator to be upcast into an `A` so it can
be fed into another layer of operator.
