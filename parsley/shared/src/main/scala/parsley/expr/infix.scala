/*
 * Copyright 2020 Parsley Contributors <https://github.com/j-mie6/Parsley/graphs/contributors>
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package parsley.expr

import parsley.Parsley, Parsley.notFollowedBy
import parsley.XAnnotation.{implicitNotFound212, implicitNotFound213}
import parsley.errors.combinator.ErrorMethods
import parsley.syntax.zipped.zippedSyntax2

import parsley.internal.deepembedding.frontend

/** This module contains the very useful chaining family of combinators,
  * which are mostly used to parse operators and expressions of varying fixities.
  * It is a more low-level API compared with [[precedence]].
  *
  * Compared with the combinators in [[chain]], these allow for more freedom in
  * the type of the values and the operators.
  *
  * @since 4.0.0
  * @group Chains
  */
object infix {
    /** This combinator handles right-associative parsing, and application of, '''zero''' or more binary operators between '''one''' or more values.
      *
      * First parse `p`, then parse `op` followed by a `p` repeatedly. The results of the `p`s, `x,,1,,` through `x,,n,,`, are combined with the results
      * of the `op`s, `f,,1,,` through `f,,n-1,,`, with right-associative application: `f,,1,,(x,,1,,, f,,2,,(x,,2,,, ..f,,n-1,,(x,,n-1,,, x,,n,,)..))`. This
      * application is then returned as the result of the combinator. If `p` or `op` fails having consumed input at any point, the whole combinator fails.
      *
      * Compared with [[chain.right1 `chain.right1`]], this combinator allows the types of the operators to more accurately encode their associativity
      * in their types. The recursive values of type `C` may only be applied on the right-hand side of the operators.
      *
      * @example {{{
      * scala> import parsley.expr.infix
      * scala> import parsley.character.{digit, char}
      * scala> sealed trait Expr
      * scala> case class Add(x: Num, y: Expr) extends Expr
      * scala> case class Num(x: Int) extends Expr
      * scala> val expr = infix.right1[Num, Add, Expr](digit.map(d => Num(d.asDigit)))(char('+').as(Add)))
      * scala> expr.parse("1+2+3+4")
      * val res0 = Success(Add(Num(1), Add(Num(2), Add(Num(3), Num(4)))))
      * scala> expr.parse("")
      * val res1 = Failure(..)
      * }}}
      *
      * @tparam A the type of the values.
      * @tparam B the type returned by the operator, which must be a subtype of the result type `C`.
      * @tparam C the result type of the chain, which also fits into the recursive application site of the operators.
      * @param p the value to be parsed.
      * @param op the operator between each value.
      * @param wrap a function that can convert the value type into the result type, ''this is provided automatically when `A <:< C`''.
      * @return a parser that parses alternating `p` and `op`, ending in a `p` and applies their results right-associatively.
      * @see [[chain.right1 `chain.right1`]] for a version where the types must match, allowing for flexibility to change the associativity.
      * @since 4.0.0
      */
    def right1[A, B, C >: B](p: Parsley[A])(op: =>Parsley[(A, C) => B])
            (implicit @implicitNotFound213("Please provide a wrapper function from ${A} to ${C}")
                      @implicitNotFound212("Please provide a wrapper function from A to C") wrap: A => C): Parsley[C] = {
        new Parsley(new frontend.Chainr(p.internal, op.internal, wrap))
    }

    /** This combinator handles left-associative parsing, and application of, '''zero''' or more binary operators between '''one''' or more values.
      *
      * First parse `p`, then parse `op` followed by a `p` repeatedly. The results of the `p`s, `x,,1,,` through `x,,n,,`, are combined with the results
      * of the `op`s, `f,,1,,` through `f,,n-1,,`, with left-associative application: `f,,n-1,,(f,,n-2,,(..f,,1,,(x,,1,,, x,,2,,).., x,,n-1,,), x,,n,,)`. This
      * application is then returned as the result of the combinator. If `p` or `op` fails having consumed input at any point, the whole combinator fails.
      *
      * Compared with [[chain.left1 `chain.left1`]], this combinator allows the types of the operators to more accurately encode their associativity
      * in their types. The recursive values of type `C` may only be applied on the left-hand side of the operators.
      *
      * @example {{{
      * scala> import parsley.expr.infix
      * scala> import parsley.character.{digit, char}
      * scala> sealed trait Expr
      * scala> case class Add(x: Expr, y: Num) extends Expr
      * scala> case class Num(x: Int) extends Expr
      * scala> val expr = infix.left1[Num, Add, Expr](digit.map(d => Num(d.asDigit)))(char('+').as(Add))
      * scala> expr.parse("1+2+3+4")
      * val res0 = Success(Add(Add(Add(Num(1), Num(2)), Num(3)), Num(4)))
      * scala> expr.parse("")
      * val res1 = Failure(..)
      * }}}
      *
      * @tparam A the type of the values.
      * @tparam B the type returned by the operator, which must be a subtype of the result type `C`.
      * @tparam C the result type of the chain, which also fits into the recursive application site of the operators.
      * @param p the value to be parsed.
      * @param op the operator between each value.
      * @param wrap a function that can convert the value type into the result type, ''this is provided automatically when `A <:< C`''.
      * @return a parser that parses alternating `p` and `op`, ending in a `p` and applies their results left-associatively.
      * @see [[chain.left1 `chain.left1`]] for a version where the types must match, allowing for flexibility to change the associativity.
      * @since 4.0.0
      */
    def left1[A, B, C >: B](p: Parsley[A])(op: =>Parsley[(C, A) => B])
            (implicit @implicitNotFound213("Please provide a wrapper function from ${A} to ${C}")
                      @implicitNotFound212("Please provide a wrapper function from A to C") wrap: A => C): Parsley[C] = {
        // a sneaky sneaky trick :) If we know that A =:= B because refl was provided, then we can skip the wrapping
        secretLeft1(parsley.XCompat.applyWrap(wrap)(p), p, op, "infix.left1")
    }

    private [parsley] def secretLeft1[A, B, C >: B](p0: Parsley[C], p: =>Parsley[A], op: =>Parsley[(C, A) => B], name: String): Parsley[C] = {
        new Parsley(new frontend.Chainl(p0.internal, p.internal, op.internal, name))
    }

    /** This combinator handles right-associative parsing, and application of, '''zero''' or more binary operators between '''zero''' or more values.
      *
      * First parse `p`, then parse `op` followed by a `p` repeatedly. The results of the `p`s, `x,,1,,` through `x,,n,,`, are combined with the results
      * of the `op`s, `f,,1,,` through `f,,n-1,,`, with right-associative application: `f,,1,,(x,,1,,, f,,2,,(x,,2,,, ..f,,n-1,,(x,,n-1,,, x,,n,,)..))`. This
      * application is then returned as the result of the combinator. If `p` or `op` fails having consumed input at any point, the whole combinator fails.
      * If no `p` could be parsed, this combinator will return a default result `x`.
      *
      * Compared with [[chain.right `chain.right`]], this combinator allows the types of the operators to more accurately encode their associativity
      * in their types. The recursive values of type `C` may only be applied on the right-hand side of the operators.
      *
      * @example {{{
      * scala> import parsley.expr.infix
      * scala> import parsley.character.{digit, char}
      * scala> sealed trait Expr
      * scala> case class Add(x: Num, y: Expr) extends Expr
      * scala> case class Num(x: Int) extends Expr
      * scala> val expr = infix.right[Num, Add, Expr](digit.map(d => Num(d.asDigit)))(char('+').as(Add), Num(0))
      * scala> expr.parse("1+2+3+4")
      * val res0 = Success(Add(Num(1), Add(Num(2), Add(Num(3), Num(4)))))
      * scala> expr.parse("")
      * val res1 = Success(Num(0))
      * }}}
      *
      * @tparam A the type of the values.
      * @tparam B the type returned by the operator, which must be a subtype of the result type `C`.
      * @tparam C the result type of the chain, which also fits into the recursive application site of the operators.
      * @param p the value to be parsed.
      * @param op the operator between each value.
      * @param x the default value to return if no `p`s can be parsed.
      * @param wrap a function that can convert the value type into the result type, ''this is provided automatically when `A <:< C`''.
      * @return a parser that parses alternating `p` and `op`, ending in a `p` and applies their results right-associatively or
      *         returns `x` if no `p` was parsed.
      * @see [[chain.right `chain.right`]] for a version where the types must match, allowing for flexibility to change the associativity.
      * @since 4.0.0
      */
    def right[A, B, C >: B](p: Parsley[A])(op: =>Parsley[(A, C) => B], x: C)
            (implicit @implicitNotFound213("Please provide a wrapper function from ${A} to ${C}")
                      @implicitNotFound212("Please provide a wrapper function from A to C") wrap: A => C): Parsley[C] = right1(p)(op) </> x

    /** This combinator handles left-associative parsing, and application of, '''zero''' or more binary operators between '''zero''' or more values.
      *
      * First parse `p`, then parse `op` followed by a `p` repeatedly. The results of the `p`s, `x,,1,,` through `x,,n,,`, are combined with the results
      * of the `op`s, `f,,1,,` through `f,,n-1,,`, with left-associative application: `f,,n-1,,(f,,n-2,,(..f,,1,,(x,,1,,, x,,2,,).., x,,n-1,,), x,,n,,)`. This
      * application is then returned as the result of the combinator. If `p` or `op` fails having consumed input at any point, the whole combinator fails.
      * If no `p` could be parsed, this combinator will return a default result `x`.
      *
      * Compared with [[chain.left `chain.left`]], this combinator allows the types of the operators to more accurately encode their associativity
      * in their types. The recursive values of type `C` may only be applied on the left-hand side of the operators.
      *
      * @example {{{
      * scala> import parsley.expr.infix
      * scala> import parsley.character.{digit, char}
      * scala> sealed trait Expr
      * scala> case class Add(x: Expr, y: Num) extends Expr
      * scala> case class Num(x: Int) extends Expr
      * scala> val expr = infix.left[Num, Add, Expr](digit.map(d => Num(d.asDigit)))(char('+').as(Add), Num(0))
      * scala> expr.parse("1+2+3+4")
      * val res0 = Success(Add(Add(Add(Num(1), Num(2)), Num(3)), Num(4)))
      * scala> expr.parse("")
      * val res1 = Success(Num(0))
      * }}}
      *
      * @tparam A the type of the values.
      * @tparam B the type returned by the operator, which must be a subtype of the result type `C`.
      * @tparam C the result type of the chain, which also fits into the recursive application site of the operators.
      * @param p the value to be parsed.
      * @param op the operator between each value.
      * @param x the default value to return if no `p`s can be parsed.
      * @param wrap a function that can convert the value type into the result type, ''this is provided automatically when `A <:< C`''.
      * @return a parser that parses alternating `p` and `op`, ending in a `p` and applies their results left-associatively or
      *         returns `x` if no `p` was parsed.
      * @see [[chain.left `chain.left`]] for a version where the types must match, allowing for flexibility to change the associativity.
      * @since 4.0.0
      */
    def left[A, B, C >: B](p: Parsley[A])(op: =>Parsley[(C, A) => B], x: C)
            (implicit @implicitNotFound213("Please provide a wrapper function from ${A} to ${C}")
                      @implicitNotFound212("Please provide a wrapper function from A to C") wrap: A => C): Parsley[C] = left1(p)(op) </> x

    //TODO: document
    def nonassoc[A, B](p: Parsley[A])(op: Parsley[(A, A) => B])(implicit wrap: A => B): Parsley[B] = {
        val guardNonAssoc = notFollowedBy(op).explain("non-associative operators cannot be chained together")
        p <**> ((op, p).zipped((f, y) => f(_, y)) </> wrap) <* guardNonAssoc
    }

    // Private Helpers (maybe expose these in future?)
    private [expr] def prefix[A, B](p: Parsley[A])(op: Parsley[B => B])(implicit wrap: A => B): Parsley[B] =
        chain.prefix(parsley.XCompat.applyWrap(wrap)(p))(op)
    private [expr] def postfix[A, B](p: Parsley[A])(op: Parsley[B => B])(implicit wrap: A => B): Parsley[B] =
        chain.postfix(parsley.XCompat.applyWrap(wrap)(p))(op)
}
