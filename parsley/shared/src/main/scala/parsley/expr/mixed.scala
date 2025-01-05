/*
 * Copyright 2020 Parsley Contributors <https://github.com/j-mie6/Parsley/graphs/contributors>
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package parsley.expr

import parsley.Parsley, Parsley._
import parsley.XAnnotation.{implicitNotFound212, implicitNotFound213}
import parsley.lift.lift4
import parsley.syntax.zipped.zippedSyntax2

/** This module contains specialist combinators for mixing unary and binary operators
  * on the same level. This is only sensible when mixing infix-left and postfix
  * or infix-right and prefix.
  *
  * @since 4.0.0
  * @group Chains
  * @note the behaviour of the enclosed combinators may be counter intuitive: they should
  *       not be used unless you know what you are doing.
  */
object mixed {
    /** This combinator handles right-associative parsing, and application of, '''zero''' or more binary operators ''and'' prefix unary operators between/before
      * '''one''' or more values.
      *
      * First parse `p`, then parse either a `uop` or an `op` followed by a `p`, repeatedly. The results of the `p`s, `x,,1,,` through `x,,n,,`, are combined
      * with the results of the `bop`s, `f,,1,,` through `f,,n-1,,`, and the `uop`s, `g,,1,1,,` through `g,,m,i,,`, with right-associative application:
      * `g,,1,1,,(g,,1,2,,(..g,,1,i,,(f,,1,,(x,,1,,, g,,2,1,,(..g,,2,j,,(f,,2,,(x,,2,,,..` . This
      * application is then returned as the result of the combinator. If `p`, `uop`, or `bop` fails having consumed input at any point, the
      * whole combinator fails.
      *
      * @example {{{
      * scala> import parsley.expr.mixed, parsley.character.{digit, char}
      * scala> sealed trait Expr
      * scala> case class B(l: C, r: Expr) extends Expr
      * scala> case class U(c: Expr) extends Expr
      * scala> case class C(x: Char) extends Expr
      * scala> val p = mixed.right1(digit.map(C), char('-').as(U), char('+').as(B))
      * scala> p.parse("-1+--2+3")
      * val res0 = Success(U(B(C('1'), U(U(B(C('2'), C('3')))))))
      * }}}
      *
      * @tparam A the type of the values.
      * @tparam B the type returned by the operator, which must be a subtype of the result type `C`.
      * @tparam C the result type of the chain, which also fits into the recursive application site of the operators.
      * @param p the value to be parsed.
      * @param uop the operator before each binary application.
      * @param bop the operator between each value.
      * @param wrap a function that can convert the value type into the result type, ''this is provided automatically when `A <:< C`''.
      * @return a parser that parses alternating `p`, `uop`, and `bop`, ending in a `p` and applies their results right-associatively.
      * @note if you ''know'' this is what you are looking for, you have found it: otherwise, this is not what you are looking for.
      * @since 4.0.0
      */
    def right1[A, B, C >: B](p: Parsley[A], uop: Parsley[C => C], bop: =>Parsley[(A, C) => B])
            (implicit @implicitNotFound213("Please provide a wrapper function from ${A} to ${C}")
                      @implicitNotFound212("Please provide a wrapper function from A to C") wrap: A => C): Parsley[C] = {
        lazy val rest: Parsley[C] = (
                uop <*> rest
            <|> (p <**> ((bop, rest).zipped(flip(_, _) _) <|> pure(wrap)))
        )
        rest
    }

    /** This combinator handles left-associative parsing, and application of, '''zero''' or more binary operators ''and'' postfix unary operators between/before
      * '''one''' or more values.
      *
      * First parse `p`, then parse either a `uop` or an `op` followed by a `p`, repeatedly. The results of the `p`s, `x,,1,,` through `x,,n,,`, are combined
      * with the results of the `bop`s, `f,,1,,` through `f,,n-1,,`, and the `uop`s, `g,,1,1,,` through `g,,m,i,,`, with left-associative application:
      * `g,,m,i,,(g,,m,i-1,,(..g,,m,1,,(f,,n-1,,(..f,,1,,(g,,1,j,,(..g,,1,1,,(x,,1,,)..)), ..` . This
      * application is then returned as the result of the combinator. If `p`, `uop`, or `bop` fails having consumed input at any point, the
      * whole combinator fails.
      *
      * @example {{{
      * scala> import parsley.expr.mixed, parsley.character.{digit, char}
      * scala> sealed trait Expr
      * scala> case class B(l: Expr, r: C) extends Expr
      * scala> case class U(c: Expr) extends Expr
      * scala> case class C(x: Char) extends Expr
      * scala> val p = mixed.left1(digit.map(Constant), char('?').as(U), char('+').as(B))
      * scala> p.parse("1?+2+3??")
      * val res0 = Success(U(U(B(B(U(C('1')), C('2')), C('3')))))
      * }}}
      *
      * @tparam A the type of the values.
      * @tparam B the type returned by the operator, which must be a subtype of the result type `C`.
      * @tparam C the result type of the chain, which also fits into the recursive application site of the operators.
      * @param p the value to be parsed.
      * @param uop the operator before each binary application.
      * @param bop the operator between each value.
      * @param wrap a function that can convert the value type into the result type, ''this is provided automatically when `A <:< C`''.
      * @return a parser that parses alternating `p`, `uop`, and `bop`, ending in a `p` and applies their results left-associatively.
      * @note if you ''know'' this is what you are looking for, you have found it: otherwise, this is not what you are looking for.
      * @since 4.0.0
      */
    def left1[A, B, C >: B](p: Parsley[A], uop: =>Parsley[C => C], bop: =>Parsley[(C, A) => B])
            (implicit @implicitNotFound213("Please provide a wrapper function from ${A} to ${C}")
                      @implicitNotFound212("Please provide a wrapper function from A to C") wrap: A => C): Parsley[C] = {
        lazy val _uop = uop
        lazy val uops = _uop.foldLeft(identity[C] _)(_ compose _)
        lazy val rest: Parsley[C => C] = (
                lift4((b: (C, A) => B, y: A, u: C => C, r: C => C) => (x: C) => r(u(b(x, y))), bop, p, uops, rest)
            <|> pure(identity[C] _)
        )
        chain.postfix(p.map(wrap))(_uop) <**> rest
    }

    private def flip[A, B, C](f: (A, B) => C, y: B)(x: A) = f(x, y)
}
