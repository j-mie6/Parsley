/*
 * Copyright 2020 Parsley Contributors <https://github.com/j-mie6/Parsley/graphs/contributors>
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package parsley.expr

import parsley.Parsley

import parsley.internal.deepembedding.frontend

/** This module contains the very useful chaining family of combinators,
  * which are mostly used to parse operators and expressions of varying fixities.
  * It is a more low-level API compared with [[precedence]].
  * @since 2.2.0
  * @group Chains
  *
  * @groupprio binary 0
  * @groupname binary Binary Operator Chains
  * @groupdesc binary
  *     These chains allow for the chaining together of values and binary operators in either left- or right-associative application.
  *
  * @groupprio unary 0
  * @groupname unary Unary Operator Chains
  * @groupdesc unary
  *     These chains allow for the chaining together and application of multiple prefix or postfix unary operators to a single value.
  */
object chain {
    /** This combinator handles right-associative parsing, and application of, '''zero''' or more binary operators between '''one''' or more values.
      *
      * First parse `p`, then parse `op` followed by a `p` repeatedly. The results of the `p`s, `x,,1,,` through `x,,n,,`, are combined with the results
      * of the `op`s, `f,,1,,` through `f,,n-1,,`, with right-associative application: `f,,1,,(x,,1,,, f,,2,,(x,,2,,, ..f,,n-1,,(x,,n-1,,, x,,n,,)..))`. This
      * application is then returned as the result of the combinator. If `p` or `op` fails having consumed input at any point, the whole combinator fails.
      *
      * @example {{{
      * scala> import parsley.expr.chain
      * scala> import parsley.character.{digit, char}
      * scala> sealed trait Expr
      * scala> case class Add(x: Expr, y: Expr) extends Expr
      * scala> case class Num(x: Int) extends Expr
      * scala> val expr = chain.right1(digit.map(d => Num(d.asDigit)))(char('+').as(Add))
      * scala> expr.parse("1+2+3+4")
      * val res0 = Success(Add(Num(1), Add(Num(2), Add(Num(3), Num(4)))))
      * scala> expr.parse("")
      * val res1 = Failure(..)
      * }}}
      *
      * @param p the value to be parsed.
      * @param op the operator between each value.
      * @return a parser that parses alternating `p` and `op`, ending in a `p` and applies their results right-associatively.
      * @see [[infix.right1 `infix.right1`]] for a version where the types can vary, ensuring that the associativity is enforced in a type-safe way.
      * @since 4.0.0
      * @group binary
      */
    def right1[A](p: Parsley[A])(op: =>Parsley[(A, A) => A]): Parsley[A] = infix.right1(p)(op)

    /** This combinator handles left-associative parsing, and application of, '''zero''' or more binary operators between '''one''' or more values.
      *
      * First parse `p`, then parse `op` followed by a `p` repeatedly. The results of the `p`s, `x,,1,,` through `x,,n,,`, are combined with the results
      * of the `op`s, `f,,1,,` through `f,,n-1,,`, with left-associative application: `f,,n-1,,(f,,n-2,,(..f,,1,,(x,,1,,, x,,2,,).., x,,n-1,,), x,,n,,)`. This
      * application is then returned as the result of the combinator. If `p` or `op` fails having consumed input at any point, the whole combinator fails.
      *
      * @example {{{
      * scala> import parsley.expr.chain
      * scala> import parsley.character.{digit, char}
      * scala> sealed trait Expr
      * scala> case class Add(x: Expr, y: Expr) extends Expr
      * scala> case class Num(x: Int) extends Expr
      * scala> val expr = chain.left1(digit.map(d => Num(d.asDigit)))(char('+').as(Add))
      * scala> expr.parse("1+2+3+4")
      * val res0 = Success(Add(Add(Add(Num(1), Num(2)), Num(3)), Num(4)))
      * scala> expr.parse("")
      * val res1 = Failure(..)
      * }}}
      *
      * @param p the value to be parsed.
      * @param op the operator between each value.
      * @return a parser that parses alternating `p` and `op`, ending in a `p` and applies their results left-associatively.
      * @see [[infix.left1 `infix.left1`]] for a version where the types can vary, ensuring that the associativity is enforced in a type-safe way.
      * @since 4.0.0
      * @group binary
      */
    def left1[A](p: Parsley[A])(op: =>Parsley[(A, A) => A]): Parsley[A] = infix.left1(p)(op)

    /** This combinator handles right-associative parsing, and application of, '''zero''' or more binary operators between '''zero''' or more values.
      *
      * First parse `p`, then parse `op` followed by a `p` repeatedly. The results of the `p`s, `x,,1,,` through `x,,n,,`, are combined with the results
      * of the `op`s, `f,,1,,` through `f,,n-1,,`, with right-associative application: `f,,1,,(x,,1,,, f,,2,,(x,,2,,, ..f,,n-1,,(x,,n-1,,, x,,n,,)..))`. This
      * application is then returned as the result of the combinator. If `p` or `op` fails having consumed input at any point, the whole combinator fails.
      * If no `p` could be parsed, this combinator will return a default result `x`.
      *
      * @example {{{
      * scala> import parsley.expr.chain
      * scala> import parsley.character.{digit, char}
      * scala> sealed trait Expr
      * scala> case class Add(x: Expr, y: Expr) extends Expr
      * scala> case class Num(x: Int) extends Expr
      * scala> val expr = chain.right(digit.map(d => Num(d.asDigit)))(char('+').as(Add), Num(0))
      * scala> expr.parse("1+2+3+4")
      * val res0 = Success(Add(Num(1), Add(Num(2), Add(Num(3), Num(4)))))
      * scala> expr.parse("")
      * val res1 = Success(Num(0))
      * }}}
      *
      * @param p the value to be parsed.
      * @param op the operator between each value.
      * @param x the default value to return if no `p`s can be parsed.
      * @return a parser that parses alternating `p` and `op`, ending in a `p` and applies their results right-associatively or
      *         returns `x` if no `p` was parsed.
      * @see [[infix.right `infix.right`]] for a version where the types can vary, ensuring that the associativity is enforced in a type-safe way.
      * @since 4.0.0
      * @group binary
      */
    def right[A](p: Parsley[A])(op: =>Parsley[(A, A) => A], x: A): Parsley[A] = infix.right(p)(op, x)

    /** This combinator handles left-associative parsing, and application of, '''zero''' or more binary operators between '''zero''' or more values.
      *
      * First parse `p`, then parse `op` followed by a `p` repeatedly. The results of the `p`s, `x,,1,,` through `x,,n,,`, are combined with the results
      * of the `op`s, `f,,1,,` through `f,,n-1,,`, with left-associative application: `f,,n-1,,(f,,n-2,,(..f,,1,,(x,,1,,, x,,2,,).., x,,n-1,,), x,,n,,)`. This
      * application is then returned as the result of the combinator. If `p` or `op` fails having consumed input at any point, the whole combinator fails.
      * If no `p` could be parsed, this combinator will return a default result `x`.
      *
      * @example {{{
      * scala> import parsley.expr.chain
      * scala> import parsley.character.{digit, char}
      * scala> sealed trait Expr
      * scala> case class Add(x: Expr, y: Expr) extends Expr
      * scala> case class Num(x: Int) extends Expr
      * scala> val expr = chain.left(digit.map(d => Num(d.asDigit)))(char('+').as(Add), Num(0))
      * scala> expr.parse("1+2+3+4")
      * val res0 = Success(Add(Add(Add(Num(1), Num(2)), Num(3)), Num(4)))
      * scala> expr.parse("")
      * val res1 = Success(Num(0))
      * }}}
      *
      * @param p the value to be parsed.
      * @param op the operator between each value.
      * @param x the default value to return if no `p`s can be parsed.
      * @return a parser that parses alternating `p` and `op`, ending in a `p` and applies their results left-associatively or
      *         returns `x` if no `p` was parsed.
      * @see [[infix.left `infix.left`]] for a version where the types can vary, ensuring that the associativity is enforced in a type-safe way.
      * @since 4.0.0
      * @group binary
      */
    def left[A](p: Parsley[A])(op: =>Parsley[(A, A) => A], x: A): Parsley[A] = infix.left(p)(op, x)

    /** This combinator handles right-assocative parsing, and application of, '''zero''' or more prefix unary operators to a single value.
      *
      * First parse many repeated `op`s. When there are no more `op`s left to parse, parse a single `p`. The result of `p`, `x`, is
      * applied to each of the results of the `op`s, `f,,1,,` through `f,,n,,`, such that `f,,n,,` is applied first and `f,,1,,` last:
      * `f,,1,,(f,,2,,(..f,,n,,(x)..))`. This application is then returned as the result of the combinator. If `p` or `op` fails having
      * consumed input at any point, the whole combinator fails.
      *
      * @example {{{
      * scala> import parsley.expr.chain
      * scala> import parsley.character.{digit, char}
      * scala> sealed trait Expr
      * scala> case class Negate(x: Expr) extends Expr
      * scala> case class Id(x: Expr) extends Expr
      * scala> case class Num(x: Int) extends Expr
      * scala> val expr = chain.prefix(digit.map(d => Num(d.asDigit))(char('-').as(Negate) <|> char('+').as(Id))
      * scala> expr.parse("--+1")
      * val res0 = Success(Negate(Negate(Id(Num(1)))))
      * scala> expr.parse("1")
      * val res1 = Success(Num(1))
      * scala> expr.parse("")
      * val res2 = Failure(..)
      * }}}
      *
      * @param op the prefix operator to repeatedly parse before `p`.
      * @param p the single value to be parsed.
      * @return a parser that parses many `op`s, and a final `p`, and applies all of the results right-associatively.
      * @since 2.2.0
      * @group unary
      */
    def prefix[A](p: Parsley[A])(op: Parsley[A => A]): Parsley[A] = new Parsley(new frontend.ChainPre(p.internal, op.internal))

    /** This combinator handles left-assocative parsing, and application of, '''zero''' or more postfix unary operators to a single value.
      *
      * First parse a single `p`. Then, parse many repeated `op`s. The result of `p`, `x`, is
      * applied to each of the results of the `op`s, `f,,1,,` through `f,,n,,`, such that `f,,1,,` is applied first and `f,,n,,` last:
      * `f,,n,,(f,,n-1,,(..f,,1,,(x)..))`. This application is then returned as the result of the combinator. If `p` or `op` fails having
      * consumed input at any point, the whole combinator fails.
      *
      * @example {{{
      * scala> import parsley.expr.chain
      * scala> import parsley.character.{digit, string}
      * scala> sealed trait Expr
      * scala> case class Inc(x: Expr) extends Expr
      * scala> case class Dec(x: Expr) extends Expr
      * scala> case class Num(x: Int) extends Expr
      * scala> val expr = chain.postfix(digit.map(d => Num(d.asDigit)))(string("++").as(Inc) <|> string("--").as(Dec))
      * scala> expr.parse("1++----")
      * val res0 = Success(Dec(Dec(Inc(Num(1)))))
      * scala> expr.parse("1")
      * val res1 = Success(Num(1))
      * scala> expr.parse("")
      * val res2 = Failure(..)
      * }}}
      *
      * @param p the single value to be parsed.
      * @param op the postfix operator to repeatedly parser after `p`.
      * @return a parser that an initial `p`, then many `op`s, and applies all of the results left-associatively.
      * @since 2.2.0
      * @group unary
      */
    def postfix[A](p: Parsley[A])(op: =>Parsley[A => A]): Parsley[A] = new Parsley(new frontend.ChainPost(p.internal, op.internal))

    /** This combinator handles right-assocative parsing, and application of, '''one''' or more prefix unary operators to a single value.
      *
      * First parse at least one repeated `op`s. When there are no more `op`s left to parse, parse a single `p`. The result of `p`, `x`, is
      * applied to each of the results of the `op`s, `f,,1,,` through `f,,n,,`, such that `f,,n,,` is applied first and `f,,1,,` last:
      * `f,,1,,(f,,2,,(..f,,n,,(x)..))`. This application is then returned as the result of the combinator. If `p` or `op` fails having
      * consumed input at any point, the whole combinator fails.
      *
      * @example {{{
      * scala> import parsley.expr.chain
      * scala> import parsley.character.{digit, char}
      * scala> sealed trait Expr
      * scala> case class Negate(x: Expr) extends Expr
      * scala> case class Id(x: Expr) extends Expr
      * scala> case class Num(x: Int) extends Expr
      * scala> val expr = chain.prefix1(digit.map(d => Num(d.asDigit)))(char('-').as(Negate) <|> char('+').as(Id))
      * scala> expr.parse("--+1")
      * val res0 = Success(Negate(Negate(Id(Num(1)))))
      * scala> expr.parse("1")
      * val res1 = Failure(..)
      * scala> expr.parse("")
      * val res2 = Failure(..)
      * }}}
      *
      * @param op the prefix operator to repeatedly parse before `p`.
      * @param p the single value to be parsed.
      * @return a parser that parses some `op`s, and a final `p`, and applies all of the results right-associatively.
      * @since 3.0.0
      * @group unary
      */
    def prefix1[A, B <: A](p: =>Parsley[A])(op: Parsley[A => B]): Parsley[B] = op <*> prefix(p)(op)

    /** This combinator handles left-assocative parsing, and application of, '''one''' or more postfix unary operators to a single value.
      *
      * First parse a single `p`. Then, parse at least one repeated `op`s. The result of `p`, `x`, is
      * applied to each of the results of the `op`s, `f,,1,,` through `f,,n,,`, such that `f,,1,,` is applied first and `f,,n,,` last:
      * `f,,n,,(f,,n-1,,(..f,,1,,(x)..))`. This application is then returned as the result of the combinator. If `p` or `op` fails having
      * consumed input at any point, the whole combinator fails.
      *
      * @example {{{
      * scala> import parsley.expr.chain
      * scala> import parsley.character.{digit, string}
      * scala> sealed trait Expr
      * scala> case class Inc(x: Expr) extends Expr
      * scala> case class Dec(x: Expr) extends Expr
      * scala> case class Num(x: Int) extends Expr
      * scala> val expr = chain.postfix1(digit.map(d => Num(d.asDigit)), string("++").as(Inc) <|> string("--").as(Dec))
      * scala> expr.parse("1++----")
      * val res0 = Success(Dec(Dec(Inc(Num(1)))))
      * scala> expr.parse("1")
      * val res1 = Failure(..)
      * scala> expr.parse("")
      * val res2 = Failure(..)
      * }}}
      *
      * @param p the single value to be parsed.
      * @param op the postfix operator to repeatedly parser after `p`.
      * @return a parser that an initial `p`, then some `op`s, and applies all of the results left-associatively.
      * @since 3.0.0
      * @group unary
      */
    def postfix1[A, B <: A](p: Parsley[A])(op: =>Parsley[A => B]): Parsley[B] = {
        lazy val op_ = op
        postfix(p <**> op_)(op_)
    }
}
