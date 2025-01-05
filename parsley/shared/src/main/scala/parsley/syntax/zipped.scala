/*
 * Copyright 2020 Parsley Contributors <https://github.com/j-mie6/Parsley/graphs/contributors>
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package parsley.syntax

import parsley.Parsley
import parsley.lift._

/** This module provides alternatives to the `f.lift(x, y)` syntax, `(x, y).zipped(f)`, which works better with type inference.
  *
  * Also enables a parameterless `zipped` method, to pair an arbitrary number of parsers such that `(p, q).zipped = p.zip(q)`.
  *
  * ''Thanks to Andrei Gramescu and George Stacey for ensuring that these combinators even exist in the first place''.
  *
  * @example {{{
  * scala> import parsley.character.char
  * scala> import parsley.syntax.zipped._
  * scala> case class Add(x: Int, y: Int)
  * scala> val p = (char('a').as(4), char('b').as(5)).zipped(Add)
  * scala> p.parse("ab")
  * val res0 = Success(Add(4, 5))
  * scala> val q = (char('a').as(3), char('b').as(2), char('c').as(5)).zipped((x, y, z) => x * y + z)
  * scala> q.parse("abc")
  * val res1 = Success(11)
  * scala> q.parse("ab")
  * val res2 = Failure(..)
  * }}}
  *
  * @note these methods are '''not''' lazy like the `lift` syntax or `liftN` functions! Use the [[parsley.Parsley.LazyParsley.unary_~ prefix `~`]] combinator
  *       to make arguments lazy where necessary.
  * @since 4.5.0
  */
object zipped extends zipped
private [syntax] trait zipped {
    implicit def zippedSyntax2[T1, T2](t: (Parsley[T1], Parsley[T2])): Zipped2[T1, T2] = new Zipped2(t)
    implicit def zippedSyntax3[T1, T2, T3](t: (Parsley[T1], Parsley[T2], Parsley[T3])): Zipped3[T1, T2, T3] = new Zipped3(t)
    implicit def zippedSyntax4[T1, T2, T3, T4](t: (Parsley[T1], Parsley[T2], Parsley[T3], Parsley[T4])): Zipped4[T1, T2, T3, T4] = new Zipped4(t)
    implicit def zippedSyntax5[T1, T2, T3, T4, T5](t: (Parsley[T1], Parsley[T2], Parsley[T3], Parsley[T4], Parsley[T5])): Zipped5[T1, T2, T3, T4, T5] = new Zipped5(t)
    implicit def zippedSyntax6[T1, T2, T3, T4, T5, T6](t: (Parsley[T1], Parsley[T2], Parsley[T3], Parsley[T4], Parsley[T5], Parsley[T6])): Zipped6[T1, T2, T3, T4, T5, T6] = new Zipped6(t)
    implicit def zippedSyntax7[T1, T2, T3, T4, T5, T6, T7](t: (Parsley[T1], Parsley[T2], Parsley[T3], Parsley[T4], Parsley[T5], Parsley[T6], Parsley[T7])): Zipped7[T1, T2, T3, T4, T5, T6, T7] = new Zipped7(t)
    implicit def zippedSyntax8[T1, T2, T3, T4, T5, T6, T7, T8](t: (Parsley[T1], Parsley[T2], Parsley[T3], Parsley[T4], Parsley[T5], Parsley[T6], Parsley[T7], Parsley[T8])): Zipped8[T1, T2, T3, T4, T5, T6, T7, T8] = new Zipped8(t)
    implicit def zippedSyntax9[T1, T2, T3, T4, T5, T6, T7, T8, T9](t: (Parsley[T1], Parsley[T2], Parsley[T3], Parsley[T4], Parsley[T5], Parsley[T6], Parsley[T7], Parsley[T8], Parsley[T9])): Zipped9[T1, T2, T3, T4, T5, T6, T7, T8, T9] = new Zipped9(t)
    implicit def zippedSyntax10[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10](t: (Parsley[T1], Parsley[T2], Parsley[T3], Parsley[T4], Parsley[T5], Parsley[T6], Parsley[T7], Parsley[T8], Parsley[T9], Parsley[T10])): Zipped10[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10] = new Zipped10(t)
    implicit def zippedSyntax11[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11](t: (Parsley[T1], Parsley[T2], Parsley[T3], Parsley[T4], Parsley[T5], Parsley[T6], Parsley[T7], Parsley[T8], Parsley[T9], Parsley[T10], Parsley[T11])): Zipped11[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11] = new Zipped11(t)
    implicit def zippedSyntax12[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12](t: (Parsley[T1], Parsley[T2], Parsley[T3], Parsley[T4], Parsley[T5], Parsley[T6], Parsley[T7], Parsley[T8], Parsley[T9], Parsley[T10], Parsley[T11], Parsley[T12])): Zipped12[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12] = new Zipped12(t)
    implicit def zippedSyntax13[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13](t: (Parsley[T1], Parsley[T2], Parsley[T3], Parsley[T4], Parsley[T5], Parsley[T6], Parsley[T7], Parsley[T8], Parsley[T9], Parsley[T10], Parsley[T11], Parsley[T12], Parsley[T13])): Zipped13[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13] = new Zipped13(t)
    implicit def zippedSyntax14[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14](t: (Parsley[T1], Parsley[T2], Parsley[T3], Parsley[T4], Parsley[T5], Parsley[T6], Parsley[T7], Parsley[T8], Parsley[T9], Parsley[T10], Parsley[T11], Parsley[T12], Parsley[T13], Parsley[T14])): Zipped14[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14] = new Zipped14(t)
    implicit def zippedSyntax15[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15](t: (Parsley[T1], Parsley[T2], Parsley[T3], Parsley[T4], Parsley[T5], Parsley[T6], Parsley[T7], Parsley[T8], Parsley[T9], Parsley[T10], Parsley[T11], Parsley[T12], Parsley[T13], Parsley[T14], Parsley[T15])): Zipped15[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15] = new Zipped15(t)
    implicit def zippedSyntax16[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16](t: (Parsley[T1], Parsley[T2], Parsley[T3], Parsley[T4], Parsley[T5], Parsley[T6], Parsley[T7], Parsley[T8], Parsley[T9], Parsley[T10], Parsley[T11], Parsley[T12], Parsley[T13], Parsley[T14], Parsley[T15], Parsley[T16])): Zipped16[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16] = new Zipped16(t)
    implicit def zippedSyntax17[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17](t: (Parsley[T1], Parsley[T2], Parsley[T3], Parsley[T4], Parsley[T5], Parsley[T6], Parsley[T7], Parsley[T8], Parsley[T9], Parsley[T10], Parsley[T11], Parsley[T12], Parsley[T13], Parsley[T14], Parsley[T15], Parsley[T16], Parsley[T17])): Zipped17[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17] = new Zipped17(t)
    implicit def zippedSyntax18[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18](t: (Parsley[T1], Parsley[T2], Parsley[T3], Parsley[T4], Parsley[T5], Parsley[T6], Parsley[T7], Parsley[T8], Parsley[T9], Parsley[T10], Parsley[T11], Parsley[T12], Parsley[T13], Parsley[T14], Parsley[T15], Parsley[T16], Parsley[T17], Parsley[T18])): Zipped18[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18] = new Zipped18(t)
    implicit def zippedSyntax19[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19](t: (Parsley[T1], Parsley[T2], Parsley[T3], Parsley[T4], Parsley[T5], Parsley[T6], Parsley[T7], Parsley[T8], Parsley[T9], Parsley[T10], Parsley[T11], Parsley[T12], Parsley[T13], Parsley[T14], Parsley[T15], Parsley[T16], Parsley[T17], Parsley[T18], Parsley[T19])): Zipped19[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19] = new Zipped19(t)
    implicit def zippedSyntax20[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19, T20](t: (Parsley[T1], Parsley[T2], Parsley[T3], Parsley[T4], Parsley[T5], Parsley[T6], Parsley[T7], Parsley[T8], Parsley[T9], Parsley[T10], Parsley[T11], Parsley[T12], Parsley[T13], Parsley[T14], Parsley[T15], Parsley[T16], Parsley[T17], Parsley[T18], Parsley[T19], Parsley[T20])): Zipped20[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19, T20] = new Zipped20(t)
    implicit def zippedSyntax21[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19, T20, T21](t: (Parsley[T1], Parsley[T2], Parsley[T3], Parsley[T4], Parsley[T5], Parsley[T6], Parsley[T7], Parsley[T8], Parsley[T9], Parsley[T10], Parsley[T11], Parsley[T12], Parsley[T13], Parsley[T14], Parsley[T15], Parsley[T16], Parsley[T17], Parsley[T18], Parsley[T19], Parsley[T20], Parsley[T21])): Zipped21[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19, T20, T21] = new Zipped21(t)
    implicit def zippedSyntax22[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19, T20, T21, T22](t: (Parsley[T1], Parsley[T2], Parsley[T3], Parsley[T4], Parsley[T5], Parsley[T6], Parsley[T7], Parsley[T8], Parsley[T9], Parsley[T10], Parsley[T11], Parsley[T12], Parsley[T13], Parsley[T14], Parsley[T15], Parsley[T16], Parsley[T17], Parsley[T18], Parsley[T19], Parsley[T20], Parsley[T21], Parsley[T22])): Zipped22[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19, T20, T21, T22] = new Zipped22(t)
}

// $COVERAGE-OFF$
// scalastyle:off parameter.number ensure.single.space.after.token
/** This class enables the `zipped` syntax on pairs of two parsers.
  *
  * @param t the two parsers whose results should be zipped together.
  */
final class Zipped2[T1, T2] private [syntax] (private val t: (Parsley[T1], Parsley[T2])) extends AnyVal {
    /** This combinator executes each of these parsers and combines their results with a given function.
      *
      * Each of these parsers is executed in turn, each yielding a result. So long as every parser
      * succeeded, the whole combinator succeeds and each of the results is fed into the function `f`.
      * The result of applying `f` to the results is returned by the combinator. If any of these parsers
      * fail, the whole combinator fails.
      *
      * @param f the function to apply across the results of all the parsers.
      * @return a parser that seqeunces each of these parsers and combines their results with the function `f`.
      */
    def zipped[R](f: (T1, T2) => R): Parsley[R] = lift2(f, t._1, t._2)
    /** This combinator executes each of these parsers and pairs all their results together.
      *
      * Each of these parsers is executed in turn, each yielding a result. So long as every parser
      * succeeded, the whole combinator succeeds.
      * The pair formed from all of the results is returned by the parser. If any of these parsers
      * fail, the whole combinator fails.
      *
      * @return a parser that seqeunces each of these parsers and pairs them all together.
      */
    def zipped: Parsley[(T1, T2)] = this.zipped((_, _))
}
/** This class enables the `zipped` syntax on tuples of three parsers.
  *
  * @param t the three parsers whose results should be zipped together.
  */
final class Zipped3[T1, T2, T3] private [syntax] (private val t: (Parsley[T1], Parsley[T2], Parsley[T3])) extends AnyVal {
    /** This combinator executes each of these parsers and combines their results with a given function.
      *
      * Each of these parsers is executed in turn, each yielding a result. So long as every parser
      * succeeded, the whole combinator succeeds and each of the results is fed into the function `f`.
      * The result of applying `f` to the results is returned by the combinator. If any of these parsers
      * fail, the whole combinator fails.
      *
      * @param f the function to apply across the results of all the parsers.
      * @return a parser that seqeunces each of these parsers and combines their results with the function `f`.
      */
    def zipped[R](f: (T1, T2, T3) => R): Parsley[R] = lift3(f, t._1, t._2, t._3)
    /** This combinator executes each of these parsers and pairs all their results together.
      *
      * Each of these parsers is executed in turn, each yielding a result. So long as every parser
      * succeeded, the whole combinator succeeds.
      * The pair formed from all of the results is returned by the parser. If any of these parsers
      * fail, the whole combinator fails.
      *
      * @return a parser that seqeunces each of these parsers and pairs them all together.
      */
    def zipped: Parsley[(T1, T2, T3)] = this.zipped((_, _, _))
}
/** This class enables the `zipped` syntax on tuples of four parsers.
  *
  * @param t the four parsers whose results should be zipped together.
  */
final class Zipped4[T1, T2, T3, T4] private [syntax] (private val t: (Parsley[T1], Parsley[T2], Parsley[T3], Parsley[T4])) extends AnyVal {
    /** This combinator executes each of these parsers and combines their results with a given function.
      *
      * Each of these parsers is executed in turn, each yielding a result. So long as every parser
      * succeeded, the whole combinator succeeds and each of the results is fed into the function `f`.
      * The result of applying `f` to the results is returned by the combinator. If any of these parsers
      * fail, the whole combinator fails.
      *
      * @param f the function to apply across the results of all the parsers.
      * @return a parser that seqeunces each of these parsers and combines their results with the function `f`.
      */
    def zipped[R](f: (T1, T2, T3, T4) => R): Parsley[R] = lift4(f, t._1, t._2, t._3, t._4)
    /** This combinator executes each of these parsers and pairs all their results together.
      *
      * Each of these parsers is executed in turn, each yielding a result. So long as every parser
      * succeeded, the whole combinator succeeds.
      * The pair formed from all of the results is returned by the parser. If any of these parsers
      * fail, the whole combinator fails.
      *
      * @return a parser that seqeunces each of these parsers and pairs them all together.
      */
    def zipped: Parsley[(T1, T2, T3, T4)] = this.zipped((_, _, _, _))
}
/** This class enables the `zipped` syntax on tuples of five parsers.
  *
  * @param t the five parsers whose results should be zipped together.
  */
final class Zipped5[T1, T2, T3, T4, T5] private [syntax] (private val t: (Parsley[T1], Parsley[T2], Parsley[T3], Parsley[T4], Parsley[T5])) extends AnyVal {
    /** This combinator executes each of these parsers and combines their results with a given function.
      *
      * Each of these parsers is executed in turn, each yielding a result. So long as every parser
      * succeeded, the whole combinator succeeds and each of the results is fed into the function `f`.
      * The result of applying `f` to the results is returned by the combinator. If any of these parsers
      * fail, the whole combinator fails.
      *
      * @param f the function to apply across the results of all the parsers.
      * @return a parser that seqeunces each of these parsers and combines their results with the function `f`.
      */
    def zipped[R](f: (T1, T2, T3, T4, T5) => R): Parsley[R] = lift5(f, t._1, t._2, t._3, t._4, t._5)
    /** This combinator executes each of these parsers and pairs all their results together.
      *
      * Each of these parsers is executed in turn, each yielding a result. So long as every parser
      * succeeded, the whole combinator succeeds.
      * The pair formed from all of the results is returned by the parser. If any of these parsers
      * fail, the whole combinator fails.
      *
      * @return a parser that seqeunces each of these parsers and pairs them all together.
      */
    def zipped: Parsley[(T1, T2, T3, T4, T5)] = this.zipped((_, _, _, _, _))
}
/** This class enables the `zipped` syntax on tuples of six parsers.
  *
  * @param t the six parsers whose results should be zipped together.
  */
final class Zipped6[T1, T2, T3, T4, T5, T6] private [syntax]
    (private val t: (Parsley[T1], Parsley[T2], Parsley[T3], Parsley[T4], Parsley[T5], Parsley[T6])) extends AnyVal {
    /** This combinator executes each of these parsers and combines their results with a given function.
      *
      * Each of these parsers is executed in turn, each yielding a result. So long as every parser
      * succeeded, the whole combinator succeeds and each of the results is fed into the function `f`.
      * The result of applying `f` to the results is returned by the combinator. If any of these parsers
      * fail, the whole combinator fails.
      *
      * @param f the function to apply across the results of all the parsers.
      * @return a parser that seqeunces each of these parsers and combines their results with the function `f`.
      */
    def zipped[R](f: (T1, T2, T3, T4, T5, T6) => R): Parsley[R] = lift6(f, t._1, t._2, t._3, t._4, t._5, t._6)
    /** This combinator executes each of these parsers and pairs all their results together.
      *
      * Each of these parsers is executed in turn, each yielding a result. So long as every parser
      * succeeded, the whole combinator succeeds.
      * The pair formed from all of the results is returned by the parser. If any of these parsers
      * fail, the whole combinator fails.
      *
      * @return a parser that seqeunces each of these parsers and pairs them all together.
      */
    def zipped: Parsley[(T1, T2, T3, T4, T5, T6)] = this.zipped((_, _, _, _, _, _))
}
/** This class enables the `zipped` syntax on tuples of seven parsers.
  *
  * @param t the seven parsers whose results should be zipped together.
  */
final class Zipped7[T1, T2, T3, T4, T5, T6, T7] private [syntax]
    (private val t: (Parsley[T1], Parsley[T2], Parsley[T3], Parsley[T4], Parsley[T5], Parsley[T6], Parsley[T7])) extends AnyVal {
    /** This combinator executes each of these parsers and combines their results with a given function.
      *
      * Each of these parsers is executed in turn, each yielding a result. So long as every parser
      * succeeded, the whole combinator succeeds and each of the results is fed into the function `f`.
      * The result of applying `f` to the results is returned by the combinator. If any of these parsers
      * fail, the whole combinator fails.
      *
      * @param f the function to apply across the results of all the parsers.
      * @return a parser that seqeunces each of these parsers and combines their results with the function `f`.
      */
    def zipped[R](f: (T1, T2, T3, T4, T5, T6, T7) => R): Parsley[R] = lift7(f, t._1, t._2, t._3, t._4, t._5, t._6, t._7)
    /** This combinator executes each of these parsers and pairs all their results together.
      *
      * Each of these parsers is executed in turn, each yielding a result. So long as every parser
      * succeeded, the whole combinator succeeds.
      * The pair formed from all of the results is returned by the parser. If any of these parsers
      * fail, the whole combinator fails.
      *
      * @return a parser that seqeunces each of these parsers and pairs them all together.
      */
    def zipped: Parsley[(T1, T2, T3, T4, T5, T6, T7)] = this.zipped((_, _, _, _, _, _, _))
}
/** This class enables the `zipped` syntax on tuples of eight parsers.
  *
  * @param t the eight parsers whose results should be zipped together.
  */
final class Zipped8[T1, T2, T3, T4, T5, T6, T7, T8] private [syntax]
    (private val t: (Parsley[T1], Parsley[T2], Parsley[T3], Parsley[T4], Parsley[T5], Parsley[T6], Parsley[T7], Parsley[T8])) extends AnyVal {
    /** This combinator executes each of these parsers and combines their results with a given function.
      *
      * Each of these parsers is executed in turn, each yielding a result. So long as every parser
      * succeeded, the whole combinator succeeds and each of the results is fed into the function `f`.
      * The result of applying `f` to the results is returned by the combinator. If any of these parsers
      * fail, the whole combinator fails.
      *
      * @param f the function to apply across the results of all the parsers.
      * @return a parser that seqeunces each of these parsers and combines their results with the function `f`.
      */
    def zipped[R](f: (T1, T2, T3, T4, T5, T6, T7, T8) => R): Parsley[R] = lift8(f, t._1, t._2, t._3, t._4, t._5, t._6, t._7, t._8)
    /** This combinator executes each of these parsers and pairs all their results together.
      *
      * Each of these parsers is executed in turn, each yielding a result. So long as every parser
      * succeeded, the whole combinator succeeds.
      * The pair formed from all of the results is returned by the parser. If any of these parsers
      * fail, the whole combinator fails.
      *
      * @return a parser that seqeunces each of these parsers and pairs them all together.
      */
    def zipped: Parsley[(T1, T2, T3, T4, T5, T6, T7, T8)] = this.zipped((_, _, _, _, _, _, _, _))
}
/** This class enables the `zipped` syntax on tuples of nine parsers.
  *
  * @param t the nine parsers whose results should be zipped together.
  */
final class Zipped9[T1, T2, T3, T4, T5, T6, T7, T8, T9] private [syntax]
    (private val t: (Parsley[T1], Parsley[T2], Parsley[T3], Parsley[T4], Parsley[T5], Parsley[T6], Parsley[T7], Parsley[T8], Parsley[T9])) extends AnyVal {
    /** This combinator executes each of these parsers and combines their results with a given function.
      *
      * Each of these parsers is executed in turn, each yielding a result. So long as every parser
      * succeeded, the whole combinator succeeds and each of the results is fed into the function `f`.
      * The result of applying `f` to the results is returned by the combinator. If any of these parsers
      * fail, the whole combinator fails.
      *
      * @param f the function to apply across the results of all the parsers.
      * @return a parser that seqeunces each of these parsers and combines their results with the function `f`.
      */
    def zipped[R](f: (T1, T2, T3, T4, T5, T6, T7, T8, T9) => R): Parsley[R] = lift9(f, t._1, t._2, t._3, t._4, t._5, t._6, t._7, t._8, t._9)
    /** This combinator executes each of these parsers and pairs all their results together.
      *
      * Each of these parsers is executed in turn, each yielding a result. So long as every parser
      * succeeded, the whole combinator succeeds.
      * The pair formed from all of the results is returned by the parser. If any of these parsers
      * fail, the whole combinator fails.
      *
      * @return a parser that seqeunces each of these parsers and pairs them all together.
      */
    def zipped: Parsley[(T1, T2, T3, T4, T5, T6, T7, T8, T9)] = this.zipped((_, _, _, _, _, _, _, _, _))
}
/** This class enables the `zipped` syntax on tuples of ten parsers.
  *
  * @param t the ten parsers whose results should be zipped together.
  */
final class Zipped10[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10] private [syntax]
    (private val t: (Parsley[T1], Parsley[T2], Parsley[T3], Parsley[T4], Parsley[T5], Parsley[T6], Parsley[T7], Parsley[T8], Parsley[T9],
                     Parsley[T10])) extends AnyVal {
    /** This combinator executes each of these parsers and combines their results with a given function.
      *
      * Each of these parsers is executed in turn, each yielding a result. So long as every parser
      * succeeded, the whole combinator succeeds and each of the results is fed into the function `f`.
      * The result of applying `f` to the results is returned by the combinator. If any of these parsers
      * fail, the whole combinator fails.
      *
      * @param f the function to apply across the results of all the parsers.
      * @return a parser that seqeunces each of these parsers and combines their results with the function `f`.
      */
    def zipped[R](f: (T1, T2, T3, T4, T5, T6, T7, T8, T9, T10) => R): Parsley[R] = lift10(f, t._1, t._2, t._3, t._4, t._5, t._6, t._7, t._8, t._9, t._10)
    /** This combinator executes each of these parsers and pairs all their results together.
      *
      * Each of these parsers is executed in turn, each yielding a result. So long as every parser
      * succeeded, the whole combinator succeeds.
      * The pair formed from all of the results is returned by the parser. If any of these parsers
      * fail, the whole combinator fails.
      *
      * @return a parser that seqeunces each of these parsers and pairs them all together.
      */
    def zipped: Parsley[(T1, T2, T3, T4, T5, T6, T7, T8, T9, T10)] = this.zipped((_, _, _, _, _, _, _, _, _, _))
}
/** This class enables the `zipped` syntax on tuples of eleven parsers.
  *
  * @param t the eleven parsers whose results should be zipped together.
  */
final class Zipped11[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11] private [syntax]
    (private val t: (Parsley[T1], Parsley[T2], Parsley[T3], Parsley[T4], Parsley[T5], Parsley[T6], Parsley[T7], Parsley[T8], Parsley[T9], Parsley[T10],
                     Parsley[T11])) extends AnyVal {
    /** This combinator executes each of these parsers and combines their results with a given function.
      *
      * Each of these parsers is executed in turn, each yielding a result. So long as every parser
      * succeeded, the whole combinator succeeds and each of the results is fed into the function `f`.
      * The result of applying `f` to the results is returned by the combinator. If any of these parsers
      * fail, the whole combinator fails.
      *
      * @param f the function to apply across the results of all the parsers.
      * @return a parser that seqeunces each of these parsers and combines their results with the function `f`.
      */
    def zipped[R](f: (T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11) => R): Parsley[R] =
        lift11(f, t._1, t._2, t._3, t._4, t._5, t._6, t._7, t._8, t._9, t._10, t._11)
    /** This combinator executes each of these parsers and pairs all their results together.
      *
      * Each of these parsers is executed in turn, each yielding a result. So long as every parser
      * succeeded, the whole combinator succeeds.
      * The pair formed from all of the results is returned by the parser. If any of these parsers
      * fail, the whole combinator fails.
      *
      * @return a parser that seqeunces each of these parsers and pairs them all together.
      */
    def zipped: Parsley[(T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11)] = this.zipped((_, _, _, _, _, _, _, _, _, _, _))
}
/** This class enables the `zipped` syntax on tuples of twelve parsers.
  *
  * @param t the twelve parsers whose results should be zipped together.
  */
final class Zipped12[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12] private [syntax]
    (private val t: (Parsley[T1], Parsley[T2], Parsley[T3], Parsley[T4], Parsley[T5], Parsley[T6], Parsley[T7], Parsley[T8], Parsley[T9], Parsley[T10],
                     Parsley[T11], Parsley[T12])) extends AnyVal {
    /** This combinator executes each of these parsers and combines their results with a given function.
      *
      * Each of these parsers is executed in turn, each yielding a result. So long as every parser
      * succeeded, the whole combinator succeeds and each of the results is fed into the function `f`.
      * The result of applying `f` to the results is returned by the combinator. If any of these parsers
      * fail, the whole combinator fails.
      *
      * @param f the function to apply across the results of all the parsers.
      * @return a parser that seqeunces each of these parsers and combines their results with the function `f`.
      */
    def zipped[R](f: (T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12) => R): Parsley[R] =
        lift12(f, t._1, t._2, t._3, t._4, t._5, t._6, t._7, t._8, t._9, t._10, t._11, t._12)
    /** This combinator executes each of these parsers and pairs all their results together.
      *
      * Each of these parsers is executed in turn, each yielding a result. So long as every parser
      * succeeded, the whole combinator succeeds.
      * The pair formed from all of the results is returned by the parser. If any of these parsers
      * fail, the whole combinator fails.
      *
      * @return a parser that seqeunces each of these parsers and pairs them all together.
      */
    def zipped: Parsley[(T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12)] = this.zipped((_, _, _, _, _, _, _, _, _, _, _, _))
}
/** This class enables the `zipped` syntax on tuples of thirteen parsers.
  *
  * @param t the thirteen parsers whose results should be zipped together.
  */
final class Zipped13[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13] private [syntax]
    (private val t: (Parsley[T1], Parsley[T2], Parsley[T3], Parsley[T4], Parsley[T5], Parsley[T6], Parsley[T7], Parsley[T8], Parsley[T9], Parsley[T10],
                     Parsley[T11], Parsley[T12], Parsley[T13])) extends AnyVal {
    /** This combinator executes each of these parsers and combines their results with a given function.
      *
      * Each of these parsers is executed in turn, each yielding a result. So long as every parser
      * succeeded, the whole combinator succeeds and each of the results is fed into the function `f`.
      * The result of applying `f` to the results is returned by the combinator. If any of these parsers
      * fail, the whole combinator fails.
      *
      * @param f the function to apply across the results of all the parsers.
      * @return a parser that seqeunces each of these parsers and combines their results with the function `f`.
      */
    def zipped[R](f: (T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13) => R): Parsley[R] =
        lift13(f, t._1, t._2, t._3, t._4, t._5, t._6, t._7, t._8, t._9, t._10, t._11, t._12, t._13)
    /** This combinator executes each of these parsers and pairs all their results together.
      *
      * Each of these parsers is executed in turn, each yielding a result. So long as every parser
      * succeeded, the whole combinator succeeds.
      * The pair formed from all of the results is returned by the parser. If any of these parsers
      * fail, the whole combinator fails.
      *
      * @return a parser that seqeunces each of these parsers and pairs them all together.
      */
    def zipped: Parsley[(T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13)] = this.zipped((_, _, _, _, _, _, _, _, _, _, _, _, _))
}
/** This class enables the `zipped` syntax on tuples of fourteen parsers.
  *
  * @param t the fourteen parsers whose results should be zipped together.
  */
final class Zipped14[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14] private [syntax]
    (private val t: (Parsley[T1], Parsley[T2], Parsley[T3], Parsley[T4], Parsley[T5], Parsley[T6], Parsley[T7], Parsley[T8], Parsley[T9], Parsley[T10],
                     Parsley[T11], Parsley[T12], Parsley[T13], Parsley[T14])) extends AnyVal {
    /** This combinator executes each of these parsers and combines their results with a given function.
      *
      * Each of these parsers is executed in turn, each yielding a result. So long as every parser
      * succeeded, the whole combinator succeeds and each of the results is fed into the function `f`.
      * The result of applying `f` to the results is returned by the combinator. If any of these parsers
      * fail, the whole combinator fails.
      *
      * @param f the function to apply across the results of all the parsers.
      * @return a parser that seqeunces each of these parsers and combines their results with the function `f`.
      */
    def zipped[R](f: (T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14) => R): Parsley[R] =
        lift14(f, t._1, t._2, t._3, t._4, t._5, t._6, t._7, t._8, t._9, t._10, t._11, t._12, t._13, t._14)
    /** This combinator executes each of these parsers and pairs all their results together.
      *
      * Each of these parsers is executed in turn, each yielding a result. So long as every parser
      * succeeded, the whole combinator succeeds.
      * The pair formed from all of the results is returned by the parser. If any of these parsers
      * fail, the whole combinator fails.
      *
      * @return a parser that seqeunces each of these parsers and pairs them all together.
      */
    def zipped: Parsley[(T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14)] = this.zipped((_, _, _, _, _, _, _, _, _, _, _, _, _, _))
}
/** This class enables the `zipped` syntax on tuples of fifteen parsers.
  *
  * @param t the fifteen parsers whose results should be zipped together.
  */
final class Zipped15[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15] private [syntax]
    (private val t: (Parsley[T1], Parsley[T2], Parsley[T3], Parsley[T4], Parsley[T5], Parsley[T6], Parsley[T7], Parsley[T8], Parsley[T9], Parsley[T10],
                     Parsley[T11], Parsley[T12], Parsley[T13], Parsley[T14], Parsley[T15])) extends AnyVal {
    /** This combinator executes each of these parsers and combines their results with a given function.
      *
      * Each of these parsers is executed in turn, each yielding a result. So long as every parser
      * succeeded, the whole combinator succeeds and each of the results is fed into the function `f`.
      * The result of applying `f` to the results is returned by the combinator. If any of these parsers
      * fail, the whole combinator fails.
      *
      * @param f the function to apply across the results of all the parsers.
      * @return a parser that seqeunces each of these parsers and combines their results with the function `f`.
      */
    def zipped[R](f: (T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15) => R): Parsley[R] =
        lift15(f, t._1, t._2, t._3, t._4, t._5, t._6, t._7, t._8, t._9, t._10, t._11, t._12, t._13, t._14, t._15)
    /** This combinator executes each of these parsers and pairs all their results together.
      *
      * Each of these parsers is executed in turn, each yielding a result. So long as every parser
      * succeeded, the whole combinator succeeds.
      * The pair formed from all of the results is returned by the parser. If any of these parsers
      * fail, the whole combinator fails.
      *
      * @return a parser that seqeunces each of these parsers and pairs them all together.
      */
    def zipped: Parsley[(T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15)] = this.zipped((_, _, _, _, _, _, _, _, _, _, _, _, _, _, _))
}
/** This class enables the `zipped` syntax on tuples of sixteen parsers.
  *
  * @param t the sixteen parsers whose results should be zipped together.
  */
final class Zipped16[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16] private [syntax]
    (private val t: (Parsley[T1], Parsley[T2], Parsley[T3], Parsley[T4], Parsley[T5], Parsley[T6], Parsley[T7], Parsley[T8], Parsley[T9], Parsley[T10],
                     Parsley[T11], Parsley[T12], Parsley[T13], Parsley[T14], Parsley[T15], Parsley[T16])) extends AnyVal {
    /** This combinator executes each of these parsers and combines their results with a given function.
      *
      * Each of these parsers is executed in turn, each yielding a result. So long as every parser
      * succeeded, the whole combinator succeeds and each of the results is fed into the function `f`.
      * The result of applying `f` to the results is returned by the combinator. If any of these parsers
      * fail, the whole combinator fails.
      *
      * @param f the function to apply across the results of all the parsers.
      * @return a parser that seqeunces each of these parsers and combines their results with the function `f`.
      */
    def zipped[R](f: (T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16) => R): Parsley[R] =
        lift16(f, t._1, t._2, t._3, t._4, t._5, t._6, t._7, t._8, t._9, t._10, t._11, t._12, t._13, t._14, t._15, t._16)
    /** This combinator executes each of these parsers and pairs all their results together.
      *
      * Each of these parsers is executed in turn, each yielding a result. So long as every parser
      * succeeded, the whole combinator succeeds.
      * The pair formed from all of the results is returned by the parser. If any of these parsers
      * fail, the whole combinator fails.
      *
      * @return a parser that seqeunces each of these parsers and pairs them all together.
      */
    def zipped: Parsley[(T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16)] =
        this.zipped((_, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _))
}
/** This class enables the `zipped` syntax on tuples of seventeen parsers.
  *
  * @param t the seventeen parsers whose results should be zipped together.
  */
final class Zipped17[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17] private [syntax]
    (private val t: (Parsley[T1], Parsley[T2], Parsley[T3], Parsley[T4], Parsley[T5], Parsley[T6], Parsley[T7], Parsley[T8], Parsley[T9], Parsley[T10],
                     Parsley[T11], Parsley[T12], Parsley[T13], Parsley[T14], Parsley[T15], Parsley[T16], Parsley[T17])) extends AnyVal {
    /** This combinator executes each of these parsers and combines their results with a given function.
      *
      * Each of these parsers is executed in turn, each yielding a result. So long as every parser
      * succeeded, the whole combinator succeeds and each of the results is fed into the function `f`.
      * The result of applying `f` to the results is returned by the combinator. If any of these parsers
      * fail, the whole combinator fails.
      *
      * @param f the function to apply across the results of all the parsers.
      * @return a parser that seqeunces each of these parsers and combines their results with the function `f`.
      */
    def zipped[R](f: (T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17) => R): Parsley[R] =
        lift17(f, t._1, t._2, t._3, t._4, t._5, t._6, t._7, t._8, t._9, t._10, t._11, t._12, t._13, t._14, t._15, t._16, t._17)
    /** This combinator executes each of these parsers and pairs all their results together.
      *
      * Each of these parsers is executed in turn, each yielding a result. So long as every parser
      * succeeded, the whole combinator succeeds.
      * The pair formed from all of the results is returned by the parser. If any of these parsers
      * fail, the whole combinator fails.
      *
      * @return a parser that seqeunces each of these parsers and pairs them all together.
      */
    def zipped: Parsley[(T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17)] =
        this.zipped((_, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _))
}
/** This class enables the `zipped` syntax on tuples of eighteen parsers.
  *
  * @param t the eighteen parsers whose results should be zipped together.
  */
final class Zipped18[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18] private [syntax]
    (private val t: (Parsley[T1], Parsley[T2], Parsley[T3], Parsley[T4], Parsley[T5], Parsley[T6], Parsley[T7], Parsley[T8], Parsley[T9], Parsley[T10],
                     Parsley[T11], Parsley[T12], Parsley[T13], Parsley[T14], Parsley[T15], Parsley[T16], Parsley[T17], Parsley[T18])) extends AnyVal {
    /** This combinator executes each of these parsers and combines their results with a given function.
      *
      * Each of these parsers is executed in turn, each yielding a result. So long as every parser
      * succeeded, the whole combinator succeeds and each of the results is fed into the function `f`.
      * The result of applying `f` to the results is returned by the combinator. If any of these parsers
      * fail, the whole combinator fails.
      *
      * @param f the function to apply across the results of all the parsers.
      * @return a parser that seqeunces each of these parsers and combines their results with the function `f`.
      */
    def zipped[R](f: (T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18) => R): Parsley[R] =
        lift18(f, t._1, t._2, t._3, t._4, t._5, t._6, t._7, t._8, t._9, t._10, t._11, t._12, t._13, t._14, t._15, t._16, t._17, t._18)
    /** This combinator executes each of these parsers and pairs all their results together.
      *
      * Each of these parsers is executed in turn, each yielding a result. So long as every parser
      * succeeded, the whole combinator succeeds.
      * The pair formed from all of the results is returned by the parser. If any of these parsers
      * fail, the whole combinator fails.
      *
      * @return a parser that seqeunces each of these parsers and pairs them all together.
      */
    def zipped: Parsley[(T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18)] =
        this.zipped((_, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _))
}
/** This class enables the `zipped` syntax on tuples of nineteen parsers.
  *
  * @param t the nineteen parsers whose results should be zipped together.
  */
final class Zipped19[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19] private [syntax]
    (private val t: (Parsley[T1], Parsley[T2], Parsley[T3], Parsley[T4], Parsley[T5], Parsley[T6], Parsley[T7], Parsley[T8], Parsley[T9], Parsley[T10],
                     Parsley[T11], Parsley[T12], Parsley[T13], Parsley[T14], Parsley[T15], Parsley[T16], Parsley[T17], Parsley[T18],
                     Parsley[T19])) extends AnyVal {
    /** This combinator executes each of these parsers and combines their results with a given function.
      *
      * Each of these parsers is executed in turn, each yielding a result. So long as every parser
      * succeeded, the whole combinator succeeds and each of the results is fed into the function `f`.
      * The result of applying `f` to the results is returned by the combinator. If any of these parsers
      * fail, the whole combinator fails.
      *
      * @param f the function to apply across the results of all the parsers.
      * @return a parser that seqeunces each of these parsers and combines their results with the function `f`.
      */
    def zipped[R](f: (T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19) => R): Parsley[R] =
        lift19(f, t._1, t._2, t._3, t._4, t._5, t._6, t._7, t._8, t._9, t._10, t._11, t._12, t._13, t._14, t._15, t._16, t._17, t._18, t._19)
    /** This combinator executes each of these parsers and pairs all their results together.
      *
      * Each of these parsers is executed in turn, each yielding a result. So long as every parser
      * succeeded, the whole combinator succeeds.
      * The pair formed from all of the results is returned by the parser. If any of these parsers
      * fail, the whole combinator fails.
      *
      * @return a parser that seqeunces each of these parsers and pairs them all together.
      */
    def zipped: Parsley[(T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19)] =
        this.zipped((_, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _))
}
/** This class enables the `zipped` syntax on tuples of twenty parsers.
  *
  * @param t the twenty parsers whose results should be zipped together.
  */
final class Zipped20[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19, T20] private [syntax]
    (private val t: (Parsley[T1], Parsley[T2], Parsley[T3], Parsley[T4], Parsley[T5], Parsley[T6], Parsley[T7], Parsley[T8], Parsley[T9], Parsley[T10],
                     Parsley[T11], Parsley[T12], Parsley[T13], Parsley[T14], Parsley[T15], Parsley[T16], Parsley[T17], Parsley[T18], Parsley[T19],
                     Parsley[T20])) extends AnyVal {
    /** This combinator executes each of these parsers and combines their results with a given function.
      *
      * Each of these parsers is executed in turn, each yielding a result. So long as every parser
      * succeeded, the whole combinator succeeds and each of the results is fed into the function `f`.
      * The result of applying `f` to the results is returned by the combinator. If any of these parsers
      * fail, the whole combinator fails.
      *
      * @param f the function to apply across the results of all the parsers.
      * @return a parser that seqeunces each of these parsers and combines their results with the function `f`.
      */
    def zipped[R](f: (T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19, T20) => R): Parsley[R] =
        lift20(f, t._1, t._2, t._3, t._4, t._5, t._6, t._7, t._8, t._9, t._10, t._11, t._12, t._13, t._14, t._15, t._16, t._17, t._18, t._19, t._20)
    /** This combinator executes each of these parsers and pairs all their results together.
      *
      * Each of these parsers is executed in turn, each yielding a result. So long as every parser
      * succeeded, the whole combinator succeeds.
      * The pair formed from all of the results is returned by the parser. If any of these parsers
      * fail, the whole combinator fails.
      *
      * @return a parser that seqeunces each of these parsers and pairs them all together.
      */
    def zipped: Parsley[(T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19, T20)] =
        this.zipped((_, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _))
}
/** This class enables the `zipped` syntax on tuples of twenty-one parsers.
  *
  * @param t the twenty-one parsers whose results should be zipped together.
  */
final class Zipped21[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19, T20, T21] private [syntax]
    (private val t: (Parsley[T1], Parsley[T2], Parsley[T3], Parsley[T4], Parsley[T5], Parsley[T6], Parsley[T7], Parsley[T8], Parsley[T9], Parsley[T10],
                     Parsley[T11], Parsley[T12], Parsley[T13], Parsley[T14], Parsley[T15], Parsley[T16], Parsley[T17], Parsley[T18], Parsley[T19],
                     Parsley[T20], Parsley[T21])) extends AnyVal {
    /** This combinator executes each of these parsers and combines their results with a given function.
      *
      * Each of these parsers is executed in turn, each yielding a result. So long as every parser
      * succeeded, the whole combinator succeeds and each of the results is fed into the function `f`.
      * The result of applying `f` to the results is returned by the combinator. If any of these parsers
      * fail, the whole combinator fails.
      *
      * @param f the function to apply across the results of all the parsers.
      * @return a parser that seqeunces each of these parsers and combines their results with the function `f`.
      */
    def zipped[R](f: (T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19, T20, T21) => R): Parsley[R] =
        lift21(f, t._1, t._2, t._3, t._4, t._5, t._6, t._7, t._8, t._9, t._10, t._11, t._12, t._13, t._14, t._15, t._16, t._17, t._18, t._19, t._20, t._21)
    /** This combinator executes each of these parsers and pairs all their results together.
      *
      * Each of these parsers is executed in turn, each yielding a result. So long as every parser
      * succeeded, the whole combinator succeeds.
      * The pair formed from all of the results is returned by the parser. If any of these parsers
      * fail, the whole combinator fails.
      *
      * @return a parser that seqeunces each of these parsers and pairs them all together.
      */
    def zipped: Parsley[(T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19, T20, T21)] =
        this.zipped((_, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _))
}
/** This class enables the `zipped` syntax on tuples of twenty-two parsers.
  *
  * @param t the twenty-two parsers whose results should be zipped together.
  */
final class Zipped22[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19, T20, T21, T22] private [syntax]
    (private val t: (Parsley[T1], Parsley[T2], Parsley[T3], Parsley[T4], Parsley[T5], Parsley[T6], Parsley[T7], Parsley[T8], Parsley[T9], Parsley[T10],
                     Parsley[T11], Parsley[T12], Parsley[T13], Parsley[T14], Parsley[T15], Parsley[T16], Parsley[T17], Parsley[T18], Parsley[T19],
                     Parsley[T20], Parsley[T21], Parsley[T22])) extends AnyVal {
    /** This combinator executes each of these parsers and combines their results with a given function.
      *
      * Each of these parsers is executed in turn, each yielding a result. So long as every parser
      * succeeded, the whole combinator succeeds and each of the results is fed into the function `f`.
      * The result of applying `f` to the results is returned by the combinator. If any of these parsers
      * fail, the whole combinator fails.
      *
      * @param f the function to apply across the results of all the parsers.
      * @return a parser that seqeunces each of these parsers and combines their results with the function `f`.
      */
    def zipped[R](f: (T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19, T20, T21, T22) => R): Parsley[R] =
        lift22(f, t._1, t._2, t._3, t._4, t._5, t._6, t._7, t._8, t._9, t._10, t._11,
                  t._12, t._13, t._14, t._15, t._16, t._17, t._18, t._19, t._20, t._21, t._22)
    /** This combinator executes each of these parsers and pairs all their results together.
      *
      * Each of these parsers is executed in turn, each yielding a result. So long as every parser
      * succeeded, the whole combinator succeeds.
      * The pair formed from all of the results is returned by the parser. If any of these parsers
      * fail, the whole combinator fails.
      *
      * @return a parser that seqeunces each of these parsers and pairs them all together.
      */
    def zipped: Parsley[(T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19, T20, T21, T22)] =
        this.zipped((_, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _))
}
// scalastyle:on parameter.number ensure.single.space.after.token
// $COVERAGE-ON$
