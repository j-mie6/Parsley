/*
 * Copyright 2020 Parsley Contributors <https://github.com/j-mie6/Parsley/graphs/contributors>
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package parsley

import scala.collection.{Factory, mutable}

import parsley.combinator.option
import parsley.errors.ErrorBuilder
import parsley.expr.{chain, infix}

import parsley.internal.diagnostics.UserException
import parsley.internal.deepembedding.{frontend, singletons}
import parsley.internal.machine.Context

import Parsley.{emptyErr, transPure => pure, some}
import XCompat._ // substituteCo

/**
  * This is the class that encapsulates the act of parsing and running an object of this class with `parse` will
  * parse the string given as input to `parse`.
  *
  * @note In order to construct an object of this class you must use the combinators; the class itself is opaque.
  *
  * @author Jamie Willis
  * @version 4.0.0
  *
  * @groupprio run 0
  * @groupname run Running Parsers
  * @groupdesc run These methods allow for a parser to be executed.
  *
  * @groupprio map 20
  * @groupname map Result Changing Combinators
  * @groupdesc map
  *     These combinators change the result of the parser they are called on into a value
  *     of a different type. This new result value may or may not be derived from the previous
  *     result.
  *
  * @groupprio alt 25
  * @groupname alt Branching Combinators
  * @groupdesc alt
  *     These combinators allow for parsing one alternative or another. All of these combinators
  *     are ''left-biased'', which means that the left-hand side of the combinator is tried first:
  *     the right-hand side of the combinator will only be tried when the left-hand one failed
  *     (and did not consume input in the process).
  *
  * @groupprio seq 25
  * @groupname seq Sequencing Combinators
  * @groupdesc seq
  *     These combinators all combine two parsers in sequence. The receiver of the combinator
  *     will be executed first, then the argument second. The results of both parsers are combined
  *     in some way (depending on the individual combinator). If one of the parsers fails, the
  *     combinator as a whole fails.
  *
  * @groupprio filter 50
  * @groupname filter Filtering Combinators
  * @groupdesc filter
  *     These combinators perform filtering on the results of a parser. This means that, given
  *     the result of a parser, they will perform some function on that result, and the success
  *     of that function effects whether or not the parser fails.
  *
  * @groupprio fold 50
  * @groupname fold Folding Combinators
  * @groupdesc fold
  *     These combinators repeatedly execute a parser (at least zero or one times depending on
  *     the specific combinator) until it fails. The results of the successes are then combined
  *     together using a folding function. An initial value for the accumulation may be given
  *     (for the `fold`s), or the first successful result is the initial accumulator (for the
  *     `reduce`s). These are implemented efficiently and do not need to construct any intermediate
  *     list with which to store the results.
  *
  * @groupprio monad 75
  * @groupname monad Expensive Sequencing Combinators
  * @groupdesc monad
  *     These combinators can sequence two parsers, where the first parser's result influences
  *     the structure of the second one. This may be because the second parser is generated
  *     from the result of the first, or that the first parser ''returns'' the second parser.
  *     Either way, the second parser cannot be known until runtime, when the first parser
  *     has been executed: this means that Parsley is forced to compile the second parser during
  *     parse-time, which is '''very''' expensive to do repeatedly. These combinators are only
  *     needed in exceptional circumstances, and should be avoided otherwise.
  *
  * @groupprio special 100
  * @groupname special Special Methods
  * @groupdesc special These are methods that should be rarely needed.
  *
  * @define attemptreason
  *     The reason for this behaviour is that it prevents space leaks and improves error messages. If this behaviour
  *     is not desired, use `atomic(this)` to rollback any input consumed on failure.
  *
  * @define or
  *     parses `q` if this parser fails '''without''' consuming input.
  *
  *     If this parser is successful, then this combinator is successful and no further action is taken. Otherwise, if this parser
  *     fails '''without''' consuming input, then `q` is parsed instead. If this parser fails having consumed
  *     input, this combinator fails.
  *
  * @define orconst
  *     returns `x` if this parser fails '''without''' consuming input.
  *
  *     If this parser is successful, then this combinator is successful and no further action is taken. Otherwise, if this parser
  *     fails '''without''' consuming input, then `x` is unconditionally returned. If this parser fails having consumed
  *     input, this combinator fails. Functionally the same as `this <|> pure(x)`.
  *
  * @define bind
  *     first performs this parser, then uses its result to generate and execute a new parser.
  *
  *     First, this combinator runs this parser. If it succeeded, the result `x` is given to the function `f`
  *     to produce a new parser `f(x)`. This new parser is then executed and its result returned. If either
  *     of this parser or the new parser fails, then the entire combinator fails.
  *
  *     This is a very powerful combinator (Turing-powerful, in fact): it is only necessary (and not ''all''
  *     the time) to use this for context-sensitive parsing. The idea is that it can make any form of
  *     decision based on the result of a parser, allowing it to perform a different parser for each
  *     possible output of another without exhaustively enumerating them all.
  *
  * @define autoAmend
  *     when this combinator fails (and not this parser itself), it will generate errors rooted at the start of the
  *     parse (as if [[parsley.errors.combinator$.amend `amend`]] had been used) and the caret will span the entire
  *     successful parse of this parser.
  */
final class Parsley[+A] private [parsley] (private [parsley] val internal: frontend.LazyParsley[A]) extends AnyVal {
    /** This method is responsible for actually executing parsers. Given an input
      * array, will parse the string with the parser. The result is either a `Success` or a `Failure`.
      * @param input The input to run against
      * @return Either a success with a value of type `A` or a failure with error message
      * @since 3.0.0
      * @group run
      */
    def parse[Err: ErrorBuilder](input: String): Result[Err, A] = {
        try new Context(internal.instrs, input, internal.numRefs, None).run()
        catch {
            // $COVERAGE-OFF$
            case UserException(err) => throw err // scalastyle:ignore throw
            // $COVERAGE-ON$
        }
    }

    // RESULT CHANGING COMBINATORS
    /** This combinator allows the result of this parser to be changed using a given function.
      *
      * When this parser succeeds, `map(f)` will adjust its result using the function `f`, which can
      * potentially change its type. This can be used to build more complex results from parsers, instead
      * of just characters or strings.
      *
      * ''In Haskell, this combinator is known as `fmap` or `(<$>)`''.
      *
      * @example {{{
      * scala> import parsley.character.digit
      * scala> digit.map(_.asDigit).parse("7")
      * val res0 = Success(7)
      * }}}
      *
      * @note This is subject to aggressive optimisations assuming purity; the compiler is permitted to optimise such
      * that the application of `f` actually only happens once at compile time. In order to preserve the behaviour of
      * impure functions, consider using the `unsafe` method before map; `p.unsafe.map(f)`.
      * @param f the function to apply to the result of the parse
      * @return a new parser that behaves the same as this parser, but with the given function `f` applied to its result.
      * @group map
      */
    def map[B](f: A => B): Parsley[B] = (pure(f) <*> this).uo("map")
    /** This combinator, pronounced "as", replaces the result of this parser, ignoring the old result.
      *
      * Similar to `map`, except the old result of this parser is not required to
      * compute the new result. This is useful when the result is a constant value (or function!).
      * Functionally the same as `this *> pure(x)` or `this.map(_ => x)`.
      *
      * ''In Haskell, this combinator is known as `($>)`''.
      *
      * @example {{{
      * scala> import parsley.character.string
      * scala> (string("true") #> true).parse("true")
      * val res0 = Success(true)
      * }}}
      *
      * @param x the new result of this parser.
      * @return a new parser that behaves the same as this parser, but always succeeds with `x` as the result.
      * @note just an alias for `as`.
      * @group map
      */
    def #>[B](x: B): Parsley[B] = this.as(x).uo("#>")
    /** This combinator replaces the result of this parser, ignoring the old result.
      *
      * Similar to `map`, except the old result of this parser is not required to
      * compute the new result. This is useful when the result is a constant value (or function!).
      * Functionally the same as `this *> pure(x)` or `this.map(_ => x)`.
      *
      * ''In Haskell, this combinator is known as `($>)`''.
      *
      * @example {{{
      * scala> import parsley.character.string
      * scala> string("true").as(true).parse("true")
      * val res0 = Success(true)
      * }}}
      *
      * @param x the new result of this parser.
      * @return a new parser that behaves the same as this parser, but always succeeds with `x` as the result.
      * @group map
      */
    infix def as[B](x: B): Parsley[B] = this.rseq(pure(x), "as")
    /** Replaces the result of this parser with `()`.
      *
      * This combinator is useful when the result of this parser is not required, and the
      * type must be `Parsley[Unit]`. Functionally the same as `this.as(())`.
      *
      * @return a new parser that behaves the same as this parser, but always returns `()` on success.
      * @group map
      */
    def void: Parsley[Unit] = this.as(()).uo("void")

    // BRANCHING COMBINATORS
    /** This combinator, pronounced "or", $or
      *
      * $attemptreason
      *
      * @example {{{
      * scala> import parsley.character.string
      * scala> val p = string("a") <|> string("b")
      * scala> p.parse("a")
      * val res0 = Success("a")
      * scala> p.parse("b")
      * val res1 = Success("b")
      * scala> val q = string("ab") <|> string("ac")
      * scala> q.parse("ac")
      * val res2 = Failure(..) // first parser consumed an 'a'!
      * }}}
      *
      * @param q the parser to run if this parser fails having not consumed input.
      * @tparam Aʹ the type returned by `q`, which must be a supertype of the result type of this parser: this allows for weakening of the result type.
      * @return a parser which either parses this parser or parses `q`.
      * @group alt
      */
    def <|>[Aʹ >: A](q: Parsley[Aʹ]): Parsley[Aʹ] = this.alt(q, "<|>")
    @inline private def alt[Aʹ >: A](q: Parsley[Aʹ], name: String): Parsley[Aʹ] = new Parsley(new frontend.<|>(this.internal, q.internal, name))
    // transparent right-associative alt combinator
    private [parsley] def |:[Aʹ >: A](q: Parsley[Aʹ]): Parsley[Aʹ] = q.alt(this, null)
    /** This combinator, pronounced "or", $or
      *
      * $attemptreason
      *
      * @example {{{
      * scala> import parsley.character.string
      * scala> val p = string("a") | string("b")
      * scala> p.parse("a")
      * val res0 = Success("a")
      * scala> p.parse("b")
      * val res1 = Success("b")
      * scala> val q = string("ab") | string("ac")
      * scala> q.parse("ac")
      * val res2 = Failure(..) // first parser consumed an 'a'!
      * }}}
      *
      * @since 4.0.0
      * @param q the parser to run if this parser fails having not consumed input.
      * @tparam Aʹ the type returned by `q`, which must be a supertype of the result type of this parser: this allows for weakening of the result type.
      * @return a parser which either parses this parser or parses `q`.
      * @note just an alias for `<|>`.
      * @group alt
      */
    def |[Aʹ >: A](q: Parsley[Aʹ]): Parsley[Aʹ] = this.alt(q, "|")
    /** This combinator $or
      *
      * $attemptreason
      *
      * @example {{{
      * scala> import parsley.character.string
      * scala> val p = string("a").orElse(string("b"))
      * scala> p.parse("a")
      * val res0 = Success("a")
      * scala> p.parse("b")
      * val res1 = Success("b")
      * scala> val q = string("ab").orElse(string("ac"))
      * scala> q.parse("ac")
      * val res2 = Failure(..) // first parser consumed an 'a'!
      * }}}
      *
      * @since 4.0.0
      * @param q the parser to run if this parser fails having not consumed input.
      * @tparam Aʹ the type returned by `q`, which must be a supertype of the result type of this parser: this allows for weakening of the result type.
      * @return a parser which either parses this parser or parses `q`.
      * @note just an alias for `<|>`.
      * @group alt
      */
    infix def orElse[Aʹ >: A](q: Parsley[Aʹ]): Parsley[Aʹ] = this.alt(q, "orElse")
    /** This combinator, pronounced "or constant", $orconst
      *
      * $attemptreason
      *
      * @example {{{
      * scala> import parsley.character.string
      * scala> val p = string("aa") </> "b"
      * scala> p.parse("aa")
      * val res0 = Success("a")
      * scala> p.parse("xyz")
      * val res1 = Success("b")
      * scala> p.parse("ab")
      * val res2 = Failure(..) // first parser consumed an 'a'!
      * }}}
      *
      * @param x the value to return if this parser fails having not consumed input.
      * @tparam Aʹ the type of `x`, which must be a supertype of the result type of this parser: this allows for weakening of the result type.
      * @return a parser which either parses this parser or returns `x`.
      * @group alt
      */
    def </>[Aʹ >: A](x: Aʹ): Parsley[Aʹ] = this.alt(pure(x), "</>")
    /** This combinator, pronounced "sum", wraps this parser's result in `Left` if it succeeds, and parses `q` if it failed '''without''' consuming input,
      * wrapping the result in `Right`.
      *
      * If this parser is successful, then its result is wrapped up using `Left(_)` and no further action is taken.
      * Otherwise, if this parser fails '''without''' consuming input, then `q` is parsed instead and its result is
      * wrapped up using `Right(_)`. If this parser fails having consumed input, this combinator fails.
      * This is functionally equivalent to `this.map(Left(_)) <|> q.map(Right(_))`.
      *
      * $attemptreason
      *
      * @example {{{
      * scala> import parsley.character.char
      * scala> val p = string("abc") <+> char("xyz")
      * scala> p.parse("abc")
      * val res0 = Success(Left("abc"))
      * scala> p.parse("xyz")
      * val res1 = Success(Right("xyz"))
      * scala> p.parse("ab")
      * val res2 = Failure(..) // first parser consumed an 'a'!
      * }}}
      *
      * @param q the parser to run if this parser fails having not consumed input.
      * @return a parser which either parses this parser or parses `q` projecting their results into an `Either[A, B]`.
      * @group alt
      */
    def <+>[B](q: Parsley[B]): Parsley[Either[A, B]] = this.map(Left(_)).ut().alt(q.map(Right(_)).ut(), "<+>")

    // SEQUENCING COMBINATORS
    /** This combinator, pronounced "ap", first parses this parser then parses `px`: if both succeed then the function
      * returned by this parser is applied to the value returned by `px`.
      *
      * The implicit (compiler-provided) evidence proves that this parser really has type `Parsley[B => C]`.
      * First, this parser is ran, yielding a function `f` on success, then `px` is ran, yielding a value `x`
      * on success. If both are successful, then `f(x)` is returned. If either fail then the entire combinator
      * fails.
      *
      * @example {{{
      * scala> import parsley.Parsley, parsley.character.char
      * scala> val sign: Parsley[Int => Int] = char('+').as(identity[Int] _) <|> char('-').as(x => -x)
      * scala> val nat: Parsley[Int] = ..
      * scala> val int = sign <*> nat
      * scala> int.parse("-7")
      * val res0 = Success(-7)
      * scala> int.parse("+42")
      * val res1 = Success(42)
      * scala> int.parse("2")
      * val res2 = Failure(..) // `sign` failed: no + or -
      * scala> int.parse("-a")
      * val res3 = Failure(..) // `nat` failed
      * }}}
      *
      * @param px the parser to run second, which returns a value applicable to this parser's result.
      * @param ev witnesses that the type of this parser, `A`, is actually `B => C`.
      * @return a parser that sequences this parser with `px` and combines their results with function application.
      * @note equivalent to `lift2((f, x) => f(x), this, px)`.
      * @group seq
      */
    def <*>[B, C](px: =>Parsley[B])
                 (implicit ev: A <:< (B=>C)): Parsley[C] = new Parsley(new frontend.<*>[B, C](ev.substituteParsley(this).internal, px.internal))

    // transparent version of <*>
    private [parsley] def ap[B, C](px: =>Parsley[B])(implicit ev: A <:< (B=>C)): Parsley[C] = {
        new Parsley(new frontend.<*>[B, C](ev.substituteParsley(this).internal, px.internal)).ut() // FIXME: move into node
    }

    /** This combinator, pronounced "reverse ap", first parses this parser then parses `pf`: if both succeed then the value
      * returned by this parser is applied to the function returned by `pf`.
      *
      * First, this parser is ran, yielding a value `x` on success, then `pf` is ran, yielding a function `f`
      * on success. If both are successful, then `f(x)` is returned. If either fail then the entire combinator
      * fails.
      *
      * Compared with `<*>`, this combinator is useful for left-factoring: when two branches of a parser share
      * a common prefix, this can often be factored out; but the result of that factored prefix may be required
      * to help generate the results of each branch. In this case, the branches can return functions that, when
      * given the factored result, can produce the original results from before the factoring.
      *
      * @example {{{
      *  // this has a common prefix "term" and requires backtracking
      * val expr1 = atomic(lift2(Add, term <* char('+'), expr2)) <|> term
      * // common prefix factored out, and branches return a function to recombine
      * val expr2 = term <**> (char('+') *> expr2.map(y => Add(_, y)) </> (identity[Expr] _))
      * }}}
      *
      * @param pf the parser to run second, which returns a function this parser's result can be applied to.
      * @return a parser that sequences this parser with `pf` and combines their results with function application.
      * @note equivalent to {{{lift2((x, f) => f(x), this, pf)}}}
      * @group seq
      */
    def <**>[B](pf: =>Parsley[A => B]): Parsley[B] = lift.lift2[A, A=>B, B]((x, f) => f(x), this, pf).uo("<**>")

    // FIXME: move into nodes
    @inline private def lseq[B](q: =>Parsley[B], name: String): Parsley[A] = new Parsley(new frontend.<*(this.internal, q.internal)).uo(name)
    @inline private def rseq[B](q: =>Parsley[B], name: String): Parsley[B] = new Parsley(new frontend.*>(this.internal, q.internal)).uo(name)

    /** This combinator, pronounced "then", first parses this parser then parses `q`: if both succeed then the result
      * of `q` is returned.
      *
      * First, this parser is ran, yielding `x` on success, then `q` is ran, yielding `y` on success. If both
      * are successful then `y` is returned and `x` is ignored. If either fail then the entire combinator fails.
      *
      * ''Identical to `~>`: `*>` is more common in Haskell, whereas `~>` is more common in Scala.''
      *
      * @example {{{
      * scala> import parsley.character.char
      * scala> ('a' *> 'b').parse("ab")
      * val res0 = Success('b')
      * }}}
      *
      * @param q the parser to run second, which returns the result of this combinator.
      * @return a parser that sequences this parser with `q` and returns `q`'s result.
      * @group seq
      */
    def *>[B](q: =>Parsley[B]): Parsley[B] = this.rseq(q, "*>")
    /** This combinator, pronounced "then discard", first parses this parser then parses `q`: if both succeed then the result
      * of this parser is returned.
      *
      * First, this parser is ran, yielding `x` on success, then `q` is ran, yielding `y` on success. If both
      * are successful then `x` is returned and `y` is ignored. If either fail then the entire combinator fails.
      *
      * ''Identical to `<~`: `<*` is more common in Haskell, whereas `<~` is more common in Scala.''
      *
      * @example {{{
      * scala> import parsley.character.char
      * scala> ('a' <* 'b').parse("ab")
      * val res0 = Success('a')
      * }}}
      *
      * @param q the parser to run second, which returns the result of this combinator.
      * @return a parser that sequences this parser with `q` and returns this parser's result.
      * @group seq
      */
    def <*[B](q: =>Parsley[B]): Parsley[A] = this.lseq(q, "<*")
    /** This combinator, pronounced "then", first parses this parser then parses `q`: if both succeed then the result
      * of `q` is returned.
      *
      * First, this parser is ran, yielding `x` on success, then `q` is ran, yielding `y` on success. If both
      * are successful then `y` is returned and `x` is ignored. If either fail then the entire combinator fails.
      *
      * ''Identical to `*>`: `*>` is more common in Haskell, whereas `~>` is more common in Scala.''
      *
      * @example {{{
      * scala> import parsley.character.char
      * scala> ('a' ~> 'b').parse("ab")
      * val res0 = Success('b')
      * }}}
      *
      * @param q the parser to run second, which returns the result of this combinator.
      * @return a parser that sequences this parser with `q` and returns `q`'s result.
      * @since 2.4.0
      * @group seq
      */
    def ~>[B](q: =>Parsley[B]): Parsley[B] = this.rseq(q, "~>")
    /** This combinator, pronounced "then discard", first parses this parser then parses `q`: if both succeed then the result
      * of this parser is returned.
      *
      * First, this parser is ran, yielding `x` on success, then `q` is ran, yielding `y` on success. If both
      * are successful then `x` is returned and `y` is ignored. If either fail then the entire combinator fails.
      *
      * ''Identical to `<*`: `<*` is more common in Haskell, whereas `<~` is more common in Scala.''
      *
      * @example {{{
      * scala> import parsley.character.char
      * scala> ('a' <~ 'b').parse("ab")
      * val res0 = Success('a')
      * }}}
      *
      * @param q the parser to run second, which returns the result of this combinator.
      * @return a parser that sequences this parser with `q` and returns this parser's result.
      * @since 2.4.0
      * @group seq
      */
    def <~[B](q: =>Parsley[B]): Parsley[A] = this.lseq(q, "<~")
    /** This combinator, pronounced "prepend", first parses this parser then parses `ps`: if both succeed the result of this
      * parser is prepended onto the result of `ps`.
      *
      * First, this parser is ran, yielding `x` on success, then `ps` is ran, yielding `xs` on success. If both
      * are successful then `x +: xs` is returned. If either fail then the entire combinator fails.
      *
      * @example {{{
      * def some[A](p: Parsley[A]): Parsley[List[A]] = {
      *     p <+:> many(p) // since List[A] <: Seq[A]
      * }
      * }}}
      *
      * @param ps the parser to run second, which returns a sequence.
      * @tparam Aʹ the type of the elements in the result sequence, which must be a supertype of
      *            the result type of this parser: this allows for weakening of the result type.
      * @return a parser that sequences this parser with `ps` and prepends its result onto `ps` result.
      * @note equivalent to {{{lift2(_ +: _, this, ps)}}}
      * @group seq
      */
    def <+:>[Aʹ >: A](ps: =>Parsley[Seq[Aʹ]]): Parsley[Seq[Aʹ]] = lift.lift2[A, Seq[Aʹ], Seq[Aʹ]](_ +: _, this, ps).uo("<+:>")
    /** This combinator, pronounced "cons", first parses this parser then parses `ps`: if both succeed the result of this
      * parser is prepended onto the result of `ps`.
      *
      * First, this parser is ran, yielding `x` on success, then `ps` is ran, yielding `xs` on success. If both
      * are successful then `x :: xs` is returned. If either fail then the entire combinator fails.
      *
      * @example {{{
      * def some[A](p: Parsley[A]): Parsley[List[A]] = {
      *     p <::> many(p)
      * }
      * }}}
      *
      * @param ps the parser to run second, which returns a list.
      * @tparam Aʹ the type of the elements in the result list, which must be a supertype of
      *            the result type of this parser: this allows for weakening of the result type.
      * @return a parser that sequences this parser with `ps` and prepends its result onto `ps` result.
      * @note equivalent to {{{lift2(_ :: _, this, ps)}}}
      * @group seq
      */
    def <::>[Aʹ >: A](ps: =>Parsley[List[Aʹ]]): Parsley[List[Aʹ]] = lift.lift2[A, List[Aʹ], List[Aʹ]](_ :: _, this, ps).uo("<::>")
    /** This combinator, pronounced "zip", first parses this parser then parses `q`: if both succeed the result of this
      * parser is paired with the result of `q`.
      *
      * First, this parser is ran, yielding `x` on success, then `q` is ran, yielding `y` on success. If both
      * are successful then `(x, y)` is returned. If either fail then the entire combinator fails.
      *
      * @example {{{
      * scala> import parsley.character.char
      * scala> val p = char('a') <~> char('b')
      * scala> p.parse("ab")
      * val res0 = Success(('a', 'b'))
      * scala> p.parse("b")
      * val res1 = Failure(..)
      * scala> p.parse("a")
      * val res2 = Failure(..)
      * }}}
      *
      * @param q the parser to run second.
      * @return a parser that sequences this parser with `q` and pairs their results together.
      * @note equivalent to {{{lift2((_, _), this, q)}}}
      * @group seq
      */
    def <~>[B](q: =>Parsley[B]): Parsley[(A, B)] = lift.lift2[A, B, (A, B)]((_, _), this, q).uo("<~>")
    /** This combinator first parses this parser then parses `q`: if both succeed the result of this
      * parser is paired with the result of `q`.
      *
      * First, this parser is ran, yielding `x` on success, then `q` is ran, yielding `y` on success. If both
      * are successful then `(x, y)` is returned. If either fail then the entire combinator fails.
      *
      * @example {{{
      * scala> import parsley.character.char
      * scala> val p = char('a').zip(char('b'))
      * scala> p.parse("ab")
      * val res0 = Success(('a', 'b'))
      * scala> p.parse("b")
      * val res1 = Failure(..)
      * scala> p.parse("a")
      * val res2 = Failure(..)
      * }}}
      *
      * @param q the parser to run second.
      * @return a parser that sequences this parser with `q` and pairs their results together.
      * @note alias for `<~>`.
      * @since 2.3.0
      * @group seq
      */
    infix def zip[B](q: =>Parsley[B]): Parsley[(A, B)] = lift.lift2[A, B, (A, B)]((_, _), this, q).uo("zip")

    // FILTERING COMBINATORS
    /** This combinator filters the result of this parser using a given predicate, succeeding only if the predicate returns `true`.
      *
      * First, parse this parser. If it succeeds then take its result `x` and apply it to the predicate `pred`. If `pred(x)` is
      * true, then return `x`. Otherwise, the combinator fails.
      *
      * @example {{{
      * scala> import parsley.character.letter
      * scala> val keywords = Set("if", "then", "else")
      * scala> val identifier = some(letter).map(_.mkString)
      *                                     .filter(!keywords.contains(_))
      * scala> identifier.parse("hello")
      * val res0 = Success("hello")
      * scala> idenfitier.parse("if")
      * val res1 = Failure(..)
      * }}}
      *
      * @param pred the predicate that is tested against the parser result.
      * @return a parser that returns the result of this parser if it passes the predicate.
      * @see [[parsley.errors.combinator.ErrorMethods.filterOut `filterOut`]] for a version which can produce custom ''reasons'' on failure.
      * @see [[parsley.errors.combinator.ErrorMethods.guardAgainst `guardAgainst`]] for a version which can produce custom error messages on failure.
      * @note $autoAmend
      * @group filter
      */
    def filter(pred: A => Boolean): Parsley[A] = parsley.errors.combinator.filterWith(this)(pred, emptyErr) //TODO: name
    /** This combinator filters the result of this parser using a given predicate, succeeding only if the predicate returns `false`.
      *
      * First, parse this parser. If it succeeds then take its result `x` and apply it to the predicate `pred`. If `pred(x) is
      * false, then return `x`. Otherwise, the combinator fails.
      *
      * @example {{{
      * scala> import parsley.character.letter
      * scala> val keywords = Set("if", "then", "else")
      * scala> val identifier = some(letter).map(_.mkString)
      *                                     .filterNot(keywords.contains(_))
      * scala> identifier.parse("hello")
      * val res0 = Success("hello")
      * scala> identifier.parse("if")
      * val res1 = Failure(..)
      * }}}
      *
      * @param pred the predicate that is tested against the parser result.
      * @return a parser that returns the result of this parser if it fails the predicate.
      * @see [[parsley.errors.combinator.ErrorMethods.filterOut `filterOut`]] for a version that can produce custom ''reasons'' on failure.
      * @see [[parsley.errors.combinator.ErrorMethods.guardAgainst `guardAgainst`]] for a version that can produce custom error messages on failure.
      * @note $autoAmend
      * @group filter
      */
    def filterNot(pred: A => Boolean): Parsley[A] = this.filter(!pred(_)) //TODO: name
    /** This combinator applies a partial function `pf` to the result of this parser if its result
      * is defined for `pf`, failing if it is not.
      *
      * First, parse this parser. If it succeeds, test whether its result `x` is in the
      * domain of the partial function `pf`. If it is defined for `pf`, return `pf(x)`.
      * If it is undefined, or this parser failed, then this combinator fails. Equivalent
      * to a `filter` followed by a `map`.
      *
      * @example {{{
      * scala> val int: Parsley[Int] = ..
      * scala> val safeDiv: Parsley[Int] = (int <~> char(' ') *> int).collect {
      *   case (x, y) if y != 0 => x / y
      * }
      * scala> safeDiv.parse("7 0")
      * val res0 = Failure(..) // y cannot be 0!
      * scala> safeDiv.parse("10 2")
      * val res1 = Success(5)
      * }}}
      *
      * @param pf the partial function used to both filter the result of this parser and transform it.
      * @return a parser that returns the result of this parser applied to `pf`, if possible.
      * @see [[parsley.errors.combinator.ErrorMethods.collectMsg[B](msg0:String,msgs:String*)* `collectMsg(String, String*)`]]
      *      and [[parsley.errors.combinator.ErrorMethods.collectMsg[B](msggen:A=>Seq[String])* `collectMsg(A => Seq[String])`]]
      *      for versions that can produce custom error messages on failure.
      * @since 2.0.0
      * @note $autoAmend
      * @group filter
      */
    def collect[B](pf: PartialFunction[A, B]): Parsley[B] = parsley.errors.combinator.collectWith(this)(pf, emptyErr) //TODO: name
    /** This combinator applies a function `f` to the result of this parser: if it returns a
      * `Some(y)`, `y` is returned, otherwise the parser fails.
      *
      * First, parse this parser. If it succeeds, apply the function `f` to the result `x`. If
      * `f(x)` returns `Some(y)`, return `y`. If `f(x)` returns `None`, or this parser failed
      * then this combinator fails. Is a more efficient way of performing a `filter` and `map`
      * at the same time.
      *
      * @example {{{
      * scala> val int: Parsley[Int] = ..
      * scala> val safeDiv: Parsley[Int] = (int <~> char(' ') *> int).collect {
      *   case (x, y) if y != 0 => Some(x / y)
      *   case _ => None
      * }
      * scala> safeDiv.parse("7 0")
      * val res0 = Failure(..) // y cannot be 0!
      * scala> safeDiv.parse("10 2")
      * val res1 = Success(5)
      * }}}
      *
      * @param f the function used to both filter the result of this parser and transform it.
      * @return a parser that returns the result of this parser applied to `f`, if it yields a value.
      * @since 4.0.0
      * @note $autoAmend
      * @group filter
      */
    def mapFilter[B](f: A => Option[B]): Parsley[B] = parsley.errors.combinator.mapFilterWith(this)(f, emptyErr) //TODO: name

    // FOLDING COMBINATORS
    /** This combinator will parse this parser '''zero''' or more times combining the results with the function `f` and base value `k` from the right.
      *
      * This parser will continue to be parsed until it fails having '''not consumed''' input.
      * All of the results generated by the successful parses are then combined in a right-to-left
      * fashion using the function `f`: the right-most value provided to `f` is the value `k`.
      * If this parser does fail at any point having consumed input, this combinator will fail.
      *
      * @example {{{
      * // in reality, many is implemented more efficiently
      * def many[A](p: Parsley[A]) = {
      *     p.foldRight(List.empty[A])(_ :: _)
      * }
      * }}}
      *
      * @param k value to use when this parser no longer succeeds.
      * @param f function to apply to each value produced by this parser, starting at the right.
      * @return a parser which parses this parser many times and folds the results together with `f` and `k` right-associatively.
      * @group fold
      */
    def foldRight[B](k: B)(f: (A, B) => B): Parsley[B] = chain.prefix(pure(k))(this.map(f.curried)) //TODO: name
    /** This combinator will parse this parser '''zero''' or more times combining the results with the function `f` and base value `k` from the left.
      *
      * This parser will continue to be parsed until it fails having '''not consumed''' input.
      * All of the results generated by the successful parses are then combined in a left-to-right
      * fashion using the function `f`: the accumulation is initialised with the value `k`.
      * If this parser does fail at any point having consumed input, this combinator will fail.
      *
      * @example {{{
      * // this is not an efficient implementation of stringOfMany
      * def stringOfMany(pc: Parsley[Char]): Parsley[String] = {
      *     pc.foldLeft("")(_ + _)
      * }
      * }}}
      *
      * @param k initial accumulation value.
      * @param f function to apply to each iteration's accumulator.
      * @return a parser which parses this parser many times and folds the results together with `f` and `k` left-associatively.
      * @group fold
      */
    def foldLeft[B](k: B)(f: (B, A) => B): Parsley[B] = expr.infix.secretLeft1(pure(k), this, pure(f), "foldLeft")
    /** This combinator will parse this parser '''one''' or more times combining the results with the function `f` and base value `k` from the right.
      *
      * This parser will continue to be parsed until it fails having '''not consumed''' input.
      * All of the results generated by the successful parses are then combined in a right-to-left
      * fashion using the function `f`: the right-most value provided to `f` is the value `k`.
      * If this parser does fail at any point having consumed input, this combinator will fail.
      *
      * @example {{{
      * // in reality, some is implemented more efficiently
      * def some[A](p: Parsley[A]) = {
      *     p.foldRight1(List.empty[A])(_ :: _)
      * }
      * }}}
      *
      * @param k value to use when this parser no longer succeeds.
      * @param f function to apply to each value produced by this parser, starting at the right.
      * @return a parser which parses this parser some times and folds the results together with `f` and `k` right-associatively.
      * @since 2.1.0
      * @group fold
      */
    def foldRight1[B](k: B)(f: (A, B) => B): Parsley[B] = {
        lift.lift2(f, this, this.foldRight(k)(f))
    } //TODO: name
    /** This combinator will parse this parser '''one''' or more times combining the results with the function `f` and base value `k` from the left.
      *
      * This parser will continue to be parsed until it fails having '''not consumed''' input.
      * All of the results generated by the successful parses are then combined in a left-to-right
      * fashion using the function `f`: the accumulation is initialised with the value `k`.
      * If this parser does fail at any point having consumed input, this combinator will fail.
      *
      * @example {{{
      * val natural: Parsley[Int] = digit.foldLeft1(0)((x, d) => x * 10 + d.toInt)
      * }}}
      *
      * @param k initial accumulation value.
      * @param f function to apply to each iteration's accumulator.
      * @return a parser which parses this parser some times and folds the results together with `f` and `k` left-associatively.
      * @since 2.1.0
      * @group fold
      */
    def foldLeft1[B](k: B)(f: (B, A) => B): Parsley[B] = expr.infix.secretLeft1(this.map(f(k, _)), this, pure(f), "foldLeft1")
    /** This combinator will parse this parser '''one''' or more times combining the results right-associatively with the function `op`.
      *
      * This parser will continue to be parsed until it fails having '''not consumed''' input.
      * All of the results generated by the successful parses are then combined in a right-to-left
      * fashion using the function `op`. If this parser does fail at any point having consumed input,
      * this combinator will fail.
      *
      * @example {{{
      * stmt.reduceRight(Seq(_, _))
      * }}}
      *
      * @param op function to apply to each value produced by this parser, starting from the right.
      * @return a parser which parses this parser some times and folds the results together with `op` right-associatively.
      * @since 2.3.0
      * @group fold
      */
    def reduceRight[B >: A](op: (A, B) => B): Parsley[B] = {
        // this cannot be infix.right1 because that combinator only fails gracefully when the op does, which this one never will
        some(this).map(_.reduceRight(op)) //TODO: name
    }
    /** This combinator will parse this parser '''zero''' or more times combining the results right-associatively with the function `op`.
      *
      * This parser will continue to be parsed until it fails having '''not consumed''' input.
      * If this parser succeeded at least once, all of the results generated by the successful
      * parses are then combined in a right-to-left fashion using the function `op` and returned
      * in a `Some`. If no successful parses occurred, then `None` is returned. If this
      * parser does fail at any point having consumed input, this combinator will fail.
      *
      * @example {{{
      * arg.reduceRightOption(Sep(_, _))
      * }}}
      *
      * @param op function to apply to each value produced by this parser, starting from the right.
      * @return a parser which parses this parser many times and folds the results together with `op` right-associatively or returns None if no parses occured.
      * @since 2.3.0
      * @group fold
      */
    def reduceRightOption[B >: A](op: (A, B) => B): Parsley[Option[B]] = option(this.reduceRight(op)) //TODO: name
    /** This combinator will parse this parser '''one''' or more times combining the results left-associatively with the function `op`.
      *
      * This parser will continue to be parsed until it fails having '''not consumed''' input.
      * All of the results generated by the successful parses are then combined in a left-to-right
      * fashion using the function `op`. If this parser does fail at any point having consumed input,
      * this combinator will fail.
      *
      * @example {{{
      * stmt.reduceLeft(Seq(_, _))
      * }}}
      *
      * @param op function to apply to each value produced by this parser, starting from the left.
      * @return a parser which parses this parser some times and folds the results together with `op` left-associatively.
      * @since 2.3.0
      * @group fold
      */
    def reduceLeft[B >: A](op: (B, A) => B): Parsley[B] = infix.left1(this)(pure(op)) //TODO: name
    /** This combinator will parse this parser '''zero''' or more times combining the results left-associatively with the function `op`.
      *
      * This parser will continue to be parsed until it fails having '''not consumed''' input.
      * If this parser succeeded at least once, all of the results generated by the successful
      * parses are then combined in a left-to-right fashion using the function `op` and returned
      * in a `Some`. If no successful parses occurred, then `None` is returned. If this
      * parser does fail at any point having consumed input, this combinator will fail.
      *
      * @example {{{
      * arg.reduceLeftOption(Sep(_, _))
      * }}}
      *
      * @param op function to apply to each value produced by this parser, starting from the left.
      * @return a parser which parses this parser many times and folds the results together with `op` left-associatively or returns None if no parses occured.
      * @since 2.3.0
      * @group fold
      */
    def reduceLeftOption[B >: A](op: (B, A) => B): Parsley[Option[B]] = option(this.reduceLeft(op)) //TODO: name

    // EXPENSIVE SEQUENCING COMBINATORS
    /** This combinator $bind
      *
      * ''In Haskell, this combinator is known as `(>>=)` (pronounced "bind")''.
      *
      * @example {{{
      * // this is an inefficient implementation, but does work
      * def filter(pred: A => Boolean): Parsley[A] = {
      *     this.flatMap { x =>
      *         if (pred(x)) pure(x)
      *         else empty
      *     }
      * }
      * }}}
      *
      * @note there is '''significant''' overhead for using `flatMap`: if possible try to avoid using it! This
      *       is because Parsley will need to generate, process, and compile each parser produced by the combinator
      *       during parse-time.
      * @param f the function that produces the next parser.
      * @return a new parser, which sequences this parser with the parser generated from its result.
      * @group monad
      */
    def flatMap[B](f: A => Parsley[B]): Parsley[B] = new Parsley(new frontend.>>=(this.internal, f.andThen(_.internal)))
    /** This combinator collapses two layers of parsing structure into one.
      *
      * The implicit (compiler-provided) evidence proves that this parser really has type `Parsley[Parsley[B]]`.
      * First, this parser is executed, which, if successful, will produce a new parser `p`. The parser `p` is
      * then executed, and its result is returned. If either this parser or `p` fail, then the entire combinator
      * fails.
      *
      * This is a very powerful combinator (Turing-powerful, in fact): it is only necessary (and not ''all''
      * the time) to use this for context-sensitive parsing. The idea is that it can allow a parser to return
      * any other parser as its result: this allows for an implementation of a later parser to be picked
      * by an earlier one.
      *
      * ''In Haskell, this combinator is known as `join`''.
      *
      * @example imagine a parser for RegEx that first reads the description of the regular expression, then runs
      *          that against the remaining input string. It is possible to implement the parser for the regular
      *          expression itself as a `Parsley[Parsley[Unit]]`, which returns a parser that matches the regular
      *          expression. This can then be used to parse the remaining input by using `flatten` to incorporate
      *          it into the parser again:
      * {{{
      * scala> val regexDesc: Parsley[Parsley[Unit]] = ..
      * // let's suppose "#" is the delimeter between expression and input
      * scala> val regex: Parsley[Unit] = (regexDesc <* char('#')).flatten
      * scala> regex.parse("a.(c*)#abccc")
      * val res0 = Success(())
      * scala> regex.parse("a.(c*)#a")
      * val res1 = Failure(..)
      * }}}
      *
      * @param ev witnesses that the result type of this parser, `A`, is really `Parsley[B]`.
      * @return a new parser, which sequences this parser with its result parser.
      * @note there is '''significant''' overhead for using `flatten`: if possible try to avoid using it! This
      *       is because Parsley will need to generate, process, and compile each parser produced by the combinator
      *       during parse-time.
      * @group monad
      */
    def flatten[B](implicit ev: A <:< Parsley[B]): Parsley[B] = this.flatMap[B](ev) //TODO: name

    /** This combinator ignores the result of this parser and instead returns the input parsed by it.
      *
      * This combinator executes this parser: if it fails, this combinator fails. Otherwise, if this
      * parser succeeded, its result is discarded and the input it consumed in the process of parsing
      * is returned directly. This can be used to efficiently obtain the underlying string of some parser
      * without having to reconstruct it. One potential application is to suppress complex results of
      * things within the `Lexer` without having to re-implement its functionality. However, it should be
      * used judiciously.
      *
      * @return a parser which returns the parsed input directly.
      * @since 4.4.0
      * @group map
      */
    def span: Parsley[String] = new Parsley(new frontend.Span(this.internal))

    // SPECIAL METHODS
    // $COVERAGE-OFF$
    /** Forces the compilation of a parser as opposed to the regular lazy evaluation.
      *
      * @group special
      */
    def force(): Unit = internal.force()

    /** Provides an indicator that this parser will likely stack-overflow and so a stack-safe
      * construction should be used when "compiling" this parser.
      *
      * @group special
      */
    def overflows(): Unit = internal.overflows()

    /** This combinator signifies that the parser it is invoked on is impure and any optimisations which assume purity
      * are disabled.
      *
      * @example Any parsers that make use of mutable state may need to use this combinator to control
      *          parsley's aggressive optimisations that remove results that are not needed: in this case,
      *          the optimiser cannot see that the result of a parser is mutating some value, and may remove it.
      * @group special
      */
    def impure: Parsley[A] = new Parsley(new frontend.Impure(this.internal))

    /** This is an alias for `p.filter(pred)`. It is needed to support for-comprehension syntax with `if`s.
      *
      * @since 4.0.0
      * @see [[parsley.Parsley.filter `filter`]] for more information.
      */
    def withFilter(pred: A => Boolean): Parsley[A] = this.filter(pred)
    // $COVERAGE-ON$

    // hidden methods (TODO: move these?)
    private [parsley] def ut(): Parsley[A] = {
        internal.transparent()
        this
    }
    private [parsley] def uo(name: String): Parsley[A] = {
        internal.opaque(name)
        this
    }
}

/** This object contains the core "function-style" combinators: all parsers will likely require something from within!
  *
  * In particular, it contains combinators for: controlling how input is consumed; injecting values into
  * the parser, or failing; extracting position information from the parser; conditional execution of
  * parsers; and more.
  *
  * @groupprio cond 25
  * @groupname cond Conditional Combinators
  * @groupdesc cond
  *     These combinators will decide which branch to take next based on the result of another parser.
  *     This differs from combinators like `<|>` which make decisions based on the success/failure of
  *     a parser: here the result of a ''successful'' parse will direct which option is done. These
  *     are sometimes known as "selective" combinators.
  *
  * @groupprio prim 0
  * @groupname prim Primitive Combinators
  * @groupdesc prim
  *     These combinators are specific to parser combinators. In one way or another, they influence how a
  *     parser consumes input, or under what conditions a parser does or does not fail. These are really
  *     important for most practical parsing considerations, although `lookAhead` is much less well used.
  *
  * @groupprio basic 5
  * @groupname basic Consumptionless Parsers
  * @groupdesc basic
  *     These combinators and parsers do not consume input: they are the most primitive ways of producing
  *     successes and failures with the minimal possible effect on the parse. They are, however, reasonably
  *     useful; in particular, `pure` and `unit` can be put to good use in injecting results into a parser
  *     without needing to consume anything, or mapping another parser.
  *
  * @groupprio iter 10
  * @groupname iter Iterative Combinators
  * @groupdesc iter
  *     These combinators all execute a given parser an unbounded number of times, until either it fails, or another
  *     parser succeeds, depending on the combinator. All of the results produced by the
  *     repeated execution of the parser are returned in a `List`. These are almost essential for any practical parsing
  *     task.
  *
  * @groupprio item 15
  * @groupname item Input Query Combinators
  * @groupdesc item
  *     These combinators do not consume input, but they allow for querying of the input stream - specifically checking
  *     whether or not there is more input that can be consumed or not. In particular, most parsers should be making
  *     use of `eof` to ensure that the parser consumes all the input available at the end of the parse.
  */
object Parsley extends ParsleyImpl with PlatformSpecific {
    private val emptyErr = new parsley.errors.VanillaGen[Any]

    /** This class enables the prefix `~` combinator, which allows a parser in an otherwise strict
      * position to be made lazy.
      *
      * @constructor This constructor should not be called manually, it is designed to be used via Scala's implicit resolution.
      * @param p the parser that `~` is enabled on.
      * @since 4.0.0
      */
    implicit final class LazyParsley[A](p: =>Parsley[A]) {
        /** This combinator makes a parser lazy.
          *
          * There are some combinators that are, due to Scala limitations,
          * strict in all their parameters. Usually, a combinator is strict
          * in its "first position", which is to say the first part of the
          * combinator to be executed; and lazy in all other "positions".
          * The rationale behind this is that recursion appearing in a
          * "first position" ''will'' result in infinite recursion at parse-time,
          * it is left-recursive after all, and so it makes little sense to
          * waste efficiency and complicate the API to support laziness
          * there. Since method receivers are strict and only
          * arguments can be lazy under regular conditions, this works well.
          *
          * However, for combinators that are always strict, this poses a
          * problem: a recursion point inside one of these strict fields
          * will cause an infinite loop at runtime! This can be fixed by
          * ensuring that this becomes part of a lazy argument. This is
          * a solution described by the [[combinator$.sequence `sequence`]]
          * combinator, for instance: `p <::> sequence(q, .., r)` will ensure
          * that the `sequence` is in a lazy position in `<::>` meaning that
          * even if any of `q` to `r` must be lazy, they can go in the strict
          * positions of skip because the `p <::>` provides the required
          * laziness. However, if this isn't possible (for instance, with
          * the [[syntax.zipped$ `zipped`]] combinators), then how can
          * this problem be solved?
          *
          * This is the job of the `~` combinator: very simply it wraps up
          * a parser in a lazy box, so that even if the box is forced by
          * a strict position, the parser will remain lazy. This means it
          * serves as an adequate solution to this problem.
          *
          * @example {{{
          * // this works fine, even though all of `zipped`'s parsers are strict
          * lazy val expr = (atomic(term) <* '+', ~expr).zipped(_ + _) <|> term
          * // in this case, however, the following would fix the problem more elegantly:
          * lazy val expr = (atomic(term), '+' *> expr).zipped(_ + _) <|> term
          * }}}
          *
          * @return the parser `p`, but guaranteed to be lazy.
          * @group special
          */
        def unary_~ : Parsley[A] = (transPure(()) *> p).ut() // I don't think this needs to appear in debug trace
    }
}
private [parsley] abstract class ParsleyImpl {
    /** This combinator produces a value without having any other effect.
      *
      * When this combinator is ran, no input is required, nor consumed, and
      * the given value will always be successfully returned. It has no other
      * effect on the state of the parser.
      *
      * @example {{{
      * scala> import parsley.Parsley.pure
      * scala> pure(7).parse("")
      * val res0 = Success(7)
      * scala> pure("hello!").parse("a")
      * val res1 = Success("hello!")
      * }}}
      *
      * @param x the value to be returned.
      * @return a parser which consumes no input and produces a value `x`.
      * @group basic
      */
    final def pure[A](x: A): Parsley[A] = new Parsley(new singletons.Pure(x, "pure"))
    @inline private [parsley] final def transPure[A](x: A) = new Parsley(new singletons.Pure(x, null))
    /** This combinator produces a '''new''' value everytime it is parsed without having any other effect.
      *
      * When this combinator is ran, no input is required, nor consumed, and
      * a '''new instance''' of the given value will always be successfully returned.
      * It has no other effect on the state of the parser.
      *
      * This is useful primarily if mutable data is being threaded around a parser: this
      * should not be needed for the ''vast'' majority of parsers.
      *
      * @example {{{
      * scala> import parsley.Parsley.{pure, fresh}
      * scala> val p = pure(new Object)
      * scala> p.parse("")
      * val res0 = Success(java.lang.Object@44a3ec6b)
      * scala> p.parse("")
      * val res1 = Success(java.lang.Object@44a3ec6b)
      * scala> val q = fresh(new Object)
      * scala> q.parse("")
      * val res2 = Success(java.lang.Object@71623278)
      * scala> q.parse("")
      * val res3 = Success(java.lang.Object@768b970c)
      * }}}
      *
      * @param x the value to be returned.
      * @return a parser which consumes no input and produces a value `x`.
      * @since 4.0.0
      * @group basic
      */
    final def fresh[A](x: =>A): Parsley[A] = new Parsley(new singletons.Fresh(x))

    /** This combinator parses its first argument `either`, and then parses either `left` or `right` depending on its result.
      *
      * First, `branch(either, left, right)` parses `either`, which, if successful, will produce either a `Left(x)` or a `Right(y)`.
      * If a `Left(x)` is produced, the parser `left` is executed to produce a function `f`, and `f(x)` is returned. Otherwise,
      * if a `Right(y)` is produced, the parser `right` is executed to produce a function `g`, and `g(y)` is returned. If either
      * of the two executed parsers fail, the entire combinator fails.
      *
      * ''First introduced in [[https://www.staff.ncl.ac.uk/andrey.mokhov/selective-functors.pdf Selective Applicative Functors]] (Mokhov et al. 2019)''.
      *
      * @example {{{
      * def ifP[A](b: Parsley[Boolean], t: =>Parsley[A], e: =>Parsley[A]): Parsley[A] = {
      *     val cond = b.map {
      *         case true => Left(())
      *         case false => Right(())
      *     }
      *     branch(cond, t.map[Unit => A](x => _ => x), e.map[Unit => A](x => _ => x))
      * }
      * }}}
      *
      * @param either the first parser to execute, its result decides which parser to execute next.
      * @param left a parser to execute if `either` returns a `Left`.
      * @param right a parser to execute if `either` returns a `Right`.
      * @return a parser that will parse one of `left` or `right` depending on `either`'s result.
      * @group cond
      */
    final def branch[A, B, C](either: Parsley[Either[A, B]], left: =>Parsley[A => C], right: =>Parsley[B => C]): Parsley[C] = {
        new Parsley(new frontend.Branch(either.internal, left.internal, right.internal))
    }
    /** This combinator parses its first argument `p`, then parses `q` only if `p` returns a `Left`.
      *
      * First, `select(p, q)` parses `p`, which, if successful, will produce either a `Left(x)` or
      * a `Right(y)`. If a `Left(x)` is produced, then the parser `q` is executed to produce a function
      * `f`, and `f(x)` is returned. Otherwise, if a `Right(y)` is produced, `y` is returned unmodified
      * and `q` is not parsed. If either `p` or `q` fails, the entire combinator fails. This is a special
      * case of `branch` where the right branch is `pure(identity[B])`.
      *
      * ''First introduced in [[https://www.staff.ncl.ac.uk/andrey.mokhov/selective-functors.pdf Selective Applicative Functors]] (Mokhov et al. 2019)''.
      *
      * @example {{{
      * def filter(pred: A => Boolean): Parsley[A] = {
      *     val p = this.map(x => if (pred(x)) Right(x) else Left(()))
      *     select(p, empty)
      * }
      * }}}
      *
      * @param p the first parser to execute, its result decides whether `q` is executed or not.
      * @param q a parser to execute when `p` returns a `Left`.
      * @return a parser that will parse `p` then possibly parse `q` to transform `p`'s result into a `B`.
      * @group cond
      */
    final def select[A, B](p: Parsley[Either[A, B]], q: =>Parsley[A => B]): Parsley[B] = branch(p, q, transPure(identity[B](_))).uo("select")
    /** This combinator parses its argument `p`, but rolls back any consumed input on failure.
      *
      * If the parser `p` succeeds, then `atomic(p)` has no effect. However, if `p` failed,
      * then any input that it consumed is rolled back. This ensures that
      * the parser `p` is all-or-nothing when consuming input. While there are many legimate
      * uses for all-or-nothing behaviour, one notable, if discouraged, use is to allow the
      * `<|>` combinator to backtrack -- recall it can only parse its alternative if the
      * first failed ''without'' consuming input. This is discouraged, however, as it can
      * affect the complexity of the parser and harm error messages.
      *
      * @example {{{
      * scala> import parsley.character.string, parsley.Parsley.atomic
      * scala> (string("abc") <|> string("abd")).parse("abd")
      * val res0 = Failure(..) // first parser consumed a, so no backtrack
      * scala> (atomic(string("abc")) <|> string("abd")).parse("abd")
      * val res1 = Success("abd") // first parser does not consume input on failure now
      * }}}
      *
      * @param p the parser to execute, if it fails, it will not have consumed input.
      * @return a parser that tries `p`, but never consumes input if it fails.
      * @since 4.4.0
      * @group prim
      */
    final def atomic[A](p: Parsley[A]): Parsley[A] = new Parsley(new frontend.Atomic(p.internal))
    /** This combinator parses its argument `p`, but does not consume input if it succeeds.
      *
      * If the parser `p` succeeds, then `lookAhead(p)` will roll back any input consumed
      * whilst parsing `p`. If `p` fails, however, then the whole combinator fails and
      * any input consumed '''remains consumed'''. If this behaviour is not desirable,
      * consider pairing `lookAhead` with `atomic`.
      *
      * @example {{{
      * scala> import parsley.Parsley.lookAhead, parsley.character.string
      * scala> (lookAhead(string("aaa")) *> string("aaa")).parse("aaa")
      * val res0 = Success("aaa")
      * scala> (lookAhead(string("abc")) <|> string("abd")).parse("abd")
      * val res1 = Failure(..) // lookAhead does not roll back input consumed on failure
      * }}}
      *
      * @param p the parser to execute, if it succeeds, it will not have consumed input.
      * @return a parser that parses `p` and never consumes input if it succeeds.
      * @group prim
      */
    final def lookAhead[A](p: Parsley[A]): Parsley[A] = new Parsley(new frontend.Look(p.internal))
    /** This combinator parses its argument `p`, and succeeds when `p` fails and vice-versa, never consuming input.
      *
      * If the parser `p` succeeds, then `notFollowedBy(p)` will fail, consuming no input.
      * Otherwise, should `p` fail, then `notFollowedBy(p)` will succeed, consuming no input
      * and returning `()`.
      *
      * @example one use for this combinator is to allow for "longest-match" behaviour.
      * For instance, keywords are normally only considered keywords if they are not
      * part of some larger valid identifier (i.e. the keyword "if" should not parse
      * successfully given "ifp"). This can be accomplished as follows:
      * {{{
      * import parsley.character.{string, letterOrDigit}
      * import parsley.Parsley.notFollowedBy
      * def keyword(kw: String): Parsley[Unit] = atomic {
      *     string(kw) *> notFollowedBy(letterOrDigit)
      * }
      * }}}
      *
      * @param p the parser to execute, it should fail in order for this combinator to succeed.
      * @return a parser which fails when `p` succeeds and succeeds otherwise, never consuming input.
      * @group prim
      */
    final def notFollowedBy(p: Parsley[_]): Parsley[Unit] = new Parsley(new frontend.NotFollowedBy(p.internal))
    /** This combinator fails immediately, with a caret of the given width and no other information.
      *
      * By producing basically no information, this combinator is principally for adjusting the
      * caret-width of another error, rather than the value `empty`, which is used to fail with
      * no effect on error content.
      *
      * @param caretWidth the width of the caret for the error produced by this combinator.
      * @return a parser that fails.
      * @since 4.4.0
      * @group basic
      */
    final def empty(caretWidth: Int): Parsley[Nothing] = new Parsley(singletons.Empty(caretWidth))
    /** This parser fails immediately, with an unknown parse error.
      *
      * @example {{{
      * scala> import parsley.Parsley.empty
      * scala> empty.parse("")
      * val res0 = Failure(..)
      * }}}
      *
      * @return a parser that fails.
      * @note equivalent to `empty(0)`
      * @group basic
      */
    final val empty: Parsley[Nothing] = empty(0)
    /** This parser produces `()` without having any other effect.
      *
      * When this parser is ran, no input is required, nor consumed, and
      * the given value will always be successfully returned. It has no other
      * effect on the state of the parser.
      *
      * @example {{{
      * scala> import parsley.Parsley.unit
      * scala> unit.parse("")
      * val res0 = Success(())
      * scala> unit.parse("a")
      * val res0 = Success(())
      * }}}
      *
      * @param x the value to be returned.
      * @return a parser which consumes no input and produces `()`.
      * @note defined as `pure(())` as a simple convenience.
      * @group basic
      */
    final val unit: Parsley[Unit] = new Parsley(new singletons.Pure((), "unit"))

    /** This parser only succeeds at the end of the input.
      *
      * Equivalent to `notFollowedBy(item)`.
      *
      * @example {{{
      * scala> import parsley.combinator.eof
      * scala> eof.parse("a")
      * val res0 = Failure(..)
      * scala> eof.parse("")
      * val res1 = Success(())
      * }}}
      *
      * @group item
      * @since 4.5.0
      */
    final val eof: Parsley[Unit] = new Parsley(singletons.Eof)

    /** This combinator repeatedly parses a given parser '''zero''' or more times, collecting the results into a list.
      *
      * Parses a given parser, `p`, repeatedly until it fails. If `p` failed having consumed input,
      * this combinator fails. Otherwise when `p` fails '''without consuming input''', this combinator
      * will return all of the results, `x,,1,,` through `x,,n,,` (with `n >= 0`), in a list: `List(x,,1,,, .., x,,n,,)`.
      * If `p` was never successful, the empty list is returned.
      *
      * @example {{{
      * scala> import parsley.character.string
      * scala> import parsley.Parsley.many
      * scala> val p = many(string("ab"))
      * scala> p.parse("")
      * val res0 = Success(Nil)
      * scala> p.parse("ab")
      * val res1 = Success(List("ab"))
      * scala> p.parse("abababab")
      * val res2 = Success(List("ab", "ab", "ab", "ab"))
      * scala> p.parse("aba")
      * val res3 = Failure(..)
      * }}}
      *
      * @param p the parser to execute multiple times.
      * @return a parser that parses `p` until it fails, returning the list of all the successful results.
      * @since 4.5.0
      * @group iter
      */
    final def many[A](p: Parsley[A]): Parsley[List[A]] = many(p, List)
    private [parsley] final def many[A, C](p: Parsley[A], factory: Factory[A, C]): Parsley[C] = {
        new Parsley(new frontend.Many(p.internal, factory))
    }

    /** This combinator repeatedly parses a given parser '''one''' or more times, collecting the results into a list.
      *
      * Parses a given parser, `p`, repeatedly until it fails. If `p` failed having consumed input,
      * this combinator fails. Otherwise when `p` fails '''without consuming input''', this combinator
      * will return all of the results, `x,,1,,` through `x,,n,,` (with `n >= 1`), in a list: `List(x,,1,,, .., x,,n,,)`.
      * If `p` was not successful at least one time, this combinator fails.
      *
      * @example {{{
      * scala> import parsley.character.string
      * scala> import parsley.Parsley.some
      * scala> val p = some(string("ab"))
      * scala> p.parse("")
      * val res0 = Failure(..)
      * scala> p.parse("ab")
      * val res1 = Success(List("ab"))
      * scala> p.parse("abababab")
      * val res2 = Success(List("ab", "ab", "ab", "ab"))
      * scala> p.parse("aba")
      * val res3 = Failure(..)
      * }}}
      *
      * @param p the parser to execute multiple times.
      * @return a parser that parses `p` until it fails, returning the list of all the successful results.
      * @since 4.5.0
      * @group iter
      */
    final def some[A](p: Parsley[A]): Parsley[List[A]] = (p <::> many(p).ut()).uo("some")
    private [parsley] final def some[A, C](p: Parsley[A], factory: Factory[A, C]): Parsley[C] = secretSome(p, p, factory).uo("some")
    // This could be generalised to be the new many, where many(p, factory) = secretSome(fresh(factory.newBuilder), p, factory)
    private [parsley] final def secretSome[A, C](init: Parsley[A], p: Parsley[A], factory: Factory[A, C]): Parsley[C] = {
        secretSome(init.map(factory.newBuilder += _).ut(), p)
    }
    private [parsley] final def secretSome[A, C](init: Parsley[mutable.Builder[A, C]], p: Parsley[A]): Parsley[C] = {
        val pf = transPure[(mutable.Builder[A, C], A) => mutable.Builder[A, C]](_ += _)
        // Can't use the regular foldLeft1 here, because we need a fresh Builder each time.
        expr.infix.secretLeft1(init, p, pf, null).map(_.result())
    }
}
