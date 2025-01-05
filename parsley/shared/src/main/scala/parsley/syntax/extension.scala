/*
 * Copyright 2020 Parsley Contributors <https://github.com/j-mie6/Parsley/graphs/contributors>
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package parsley.syntax

import parsley.Parsley, Parsley.{many, notFollowedBy, some}
import parsley.combinator.{ifS, option}

/**
  * These implicit classes can be used to extend the core combinator set of Parsley.
  *
  * This may mean that importing them enables combinators that can be used on ''non-`Parsley`'' types,
  * or might enable some syntactic sugar that is not part of the core combinator "style".
  *
  * @since 4.5.0
  */
// $COVERAGE-OFF$
object extension extends extension
private [parsley] trait extension {
    def haskellStyleMapSyntax[A, B](f: A => B): HaskellStyleMap[A, B] = new HaskellStyleMap(f)
    def lazyChooseParsleySyntax[P, Q, A](pq: =>(P, Q))(implicit conP: P => Parsley[A], conQ: Q => Parsley[A]): LazyChooseParsley[A] = {
        lazy val (p, q) = pq
        new LazyChooseParsley(conP(p), conQ(q))
    }
    def operatorSugarSyntax[P, A](p: P)(implicit con: P => Parsley[A]): OperatorSugar[A] = new OperatorSugar(con(p))
}

/** This class exposes the `<#>` combinator on functions.
  *
  * This extension class operates on functions. It enables the use of
  * the `<#>` combinator, which is an alias for `map` designed to more
  * closely mimic Haskell's style.
  */
final class HaskellStyleMap[-A, +B] private [syntax] (private val f: A => B) extends AnyVal {
    /** This combinator maps this function over the given parser `p` to alter its result.
      *
      * @see [[Parsley.map `map`]]
      */
    def <#>(p: Parsley[A]): Parsley[B] = p.map(f)
}
/** This class exposes an if combinator on pairs of parsers.
  *
  * This extension class operators on pairs of values that are convertible to parsers. It enables the
  * use of the `?:` combinator, which is an alias for `ifP`.
  */
final class LazyChooseParsley[+A] private [syntax] (p: =>Parsley[A], q: =>Parsley[A]) {
    /** This combinator will conditionally parse one of the parsers in this pair depending on the result of a third parser `b`.
      *
      * Equivalent to `ifP`, but in operator form: `ifP(b, p, q)` is the same as `b ?: (p, q)`.
      *
      * @note Scala applies methods ending in `:` right-associatively, and the pair is on the right-hand side of the operator.
      * @see [[combinator.ifS `ifP`]]
      */
    def ?:(b: Parsley[Boolean]): Parsley[A] = ifS(b, p, q)
}
/** This class enables "operator-style" alternative combinators on parsers.
  *
  * This extension class exposes a collection of "operator-style" combinators on values that are convertible to parsers
  * that are plain syntactic sugar for other functionality in the library; they are potentially
  * less readable than the combinators they replace, so should be used sparingly.
  *
  * @since 4.0.0
  */
final class OperatorSugar[+A] private [syntax] (private val p: Parsley[A]) {
    /** This combinator, pronounced "star", will parse this parser '''zero''' or more times returning a list of the parsed results.
      *
      * Equivalent to `many`, but as a postfix operator: `many(p)` is the same as `p.*`.
      *
      * @note an alias for `many`.
      * @see [[Parsley.many `many`]] for more details.
      */
    def * : Parsley[List[A]] = many(p)
    /** This combinator, pronounced "plus", will parse this parser '''one''' or more times returning a list of the parsed results.
      *
      * Equivalent to `some`, but as a postfix operator: `some(p)` is the same as `p.+`.
      *
      * @note an alias for `some`.
      * @see [[Parsley.some `some`]] for more details.
      */
    def + : Parsley[List[A]] = some(p)
    /** This combinator, pronounced "not", will succeed when this parser fails, and vice-versa, never consuming input.
      *
      * Equivalent to `notFollowedBy`, but as a prefix operator: `notFollowedBy(p)` is the same as `!p`.
      *
      * @note an alias for `notFollowedBy`.
      * @see [[Parsley.notFollowedBy `notFollowedBy`]] for more details.
      */
    def unary_! : Parsley[Unit] = notFollowedBy(p)
    /** This combinator, pronounced "and not", first parses its argument `q`, and if it fails, it will parse this parser, returning its result.
      *
      * First `q` is parsed, which will never consume input regardless of failure or success. If it failed, then this parser is executed and its result
      * is returned. If either `q` succeeds or this parser fails, the combinator fails.
      *
      * @example {{{
      * // a less efficient version of a keyword: it's more efficient to not have to read the entire keyword twice
      * def keyword(kw: String): Parsley[Unit] = {
      *     string(kw).void - identifier
      * }
      * }}}
      *
      * @param q the parser to quotient this parser by.
      * @return a parser that only parses this parser if `q` cannot parse.
      */
    def -(q: Parsley[_]): Parsley[A] = !new OperatorSugar(q) *> p
    /** This combinator, pronounced "option", will try parsing this parser wrapping its result in `Some`, and return `None` if it fails.
      *
      * Equivalent to `option`, but as a postfix operator: `option(p)` is the same as `p.?`.
      *
      * @note an alias for `option`.
      * @see [[combinator.option `option`]] for more details.
      */
    def ? : Parsley[Option[A]] = option(p)
}
// $COVERAGE-ON$
