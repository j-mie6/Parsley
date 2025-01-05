/*
 * Copyright 2020 Parsley Contributors <https://github.com/j-mie6/Parsley/graphs/contributors>
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package parsley.errors

import parsley.Parsley

import parsley.internal.deepembedding.{frontend, singletons}
import parsley.internal.errors.{CaretWidth, FlexibleCaret, RigidCaret}

/** This module contains combinators that can be used to directly influence error messages of parsers.
  *
  * Error messages are, by default, not ''particularly'' descriptive. However, the combinators in this
  * module can be used to improve the generation of error messages by providing labels for expected
  * items, explanations for why things went wrong, custom error messages, custom unexpected error messages,
  * as well as correcting the offsets that error messages actually occurred at.
  *
  * @since 3.0.0
  *
  * @group combinators
  *
  * @groupprio fail 0
  * @groupname fail Failure Combinators
  * @groupdesc fail
  *     These combinator immediately fail the parser, with a more bespoke message.
  *
  * @groupprio adj 10
  * @groupname adj Error Adjustment Combinators
  * @groupdesc adj
  *     These combinators can affect at what position an error is caused at. They are
  *     opposites: where `amend` will ensure an error message is said to have generated
  *     at the position on entry to the combinator, `entrench` will resist these changes.
  *
  * @groupprio ext 5
  * @groupname ext Error Extension Combinators
  * @groupdesc ext
  *     These are implicit classes that, when in scope, enable additional combinators on
  *     parsers that interact with the error system in some way.
  */
object combinator {
    /** This combinator consumes no input and fails immediately with the given error messages.
      *
      * Produces a ''specialised'' error message where all the lines of the error are the
      * given `msgs` in order of appearance.
      *
      * @example {{{
      * val failing = fail("hello,", "this is an error message", "broken across multiple lines")
      * }}}
      *
      * @param msg0 the first message in the error message.
      * @param msgs the remaining messages that will make up the error message.
      * @return a parser that fails producing an error message consisting of all the given messages.
      * @since 3.0.0
      * @group fail
      */
    def fail(msg0: String, msgs: String*): Parsley[Nothing] = fail(new FlexibleCaret(1), msg0, msgs: _*)

    /** This combinator consumes no input and fails immediately with the given error messages.
      *
      * Produces a ''specialised'' error message where all the lines of the error are the
      * given `msgs` in order of appearance.
      *
      * @example {{{
      * val failing = fail("hello,", "this is an error message", "broken across multiple lines")
      * }}}
      *
      * @param caretWidth the size of the caret for this error: should ideally match the width of the cause of the error.
      * @param msg0 the first message in the error message.
      * @param msgs the remaining messages that will make up the error message.
      * @return a parser that fails producing an error message consisting of all the given messages.
      * @since 4.0.0
      * @group fail
      */
    def fail(caretWidth: Int, msg0: String, msgs: String*): Parsley[Nothing] = fail(new RigidCaret(caretWidth), msg0, msgs: _*)
    private def fail(caretWidth: CaretWidth, msg0: String, msgs: String*): Parsley[Nothing] = new Parsley(new singletons.Fail(caretWidth, (msg0 +: msgs): _*))

    /** This combinator consumes no input and fails immediately, setting the unexpected component
      * to the given item.
      *
      * Produces a ''trivial'' error message where the unexpected component of the error is
      * replaced with the given item `item`.
      *
      * @since 3.0.0
      * @param item the unexpected message for the error generated.
      * @return a parser that fails producing an error with `item` as the unexpected token.
      * @group fail
      */
    def unexpected(item: String): Parsley[Nothing] = unexpected(new FlexibleCaret(1), item)

    /** This combinator consumes no input and fails immediately, setting the unexpected component
      * to the given item.
      *
      * Produces a ''trivial'' error message where the unexpected component of the error is
      * replaced with the given item `item`.
      *
      * @since 4.0.0
      * @param caretWidth the size of the caret for this error: should ideally match the width of the cause of the error (the unexpected item).
      * @param item the unexpected message for the error generated.
      * @return a parser that fails producing an error with `item` as the unexpected token.
      * @group fail
      */
    def unexpected(caretWidth: Int, item: String): Parsley[Nothing] = unexpected(new RigidCaret(caretWidth), item)
    private def unexpected(caretWidth: CaretWidth, item: String): Parsley[Nothing] = new Parsley(new singletons.Unexpected(item, caretWidth))

    /** This combinator adjusts any error messages generated by the given parser so that they
      * occur at the position recorded on entry to this combinator (effectively as if no
      * input were consumed).
      *
      * This is useful if validation work is done
      * on the output of a parser that may render it invalid, but the error should point to the
      * beginning of the structure. This combinators effect can be cancelled with [[entrench `entrench`]].
      *
      * @example {{{
      * scala> val greeting = string("hello world") <* char('!')
      * scala> greeting.label("greeting").parse("hello world.")
      * val res0 = Failure((line 1, column 12):
      *   unexpected "."
      *   expected "!"
      *   >hello world.
      *               ^)
      * scala> amend(greeting).label("greeting").parse("hello world.")
      * val res1 = Failure((line 1, column 1):
      *   unexpected "h"
      *   expected greeting
      *   >hello world.
      *    ^)
      * }}}
      *
      * @param p a parser whose error messages should be adjusted.
      * @return a parser that parses `p` but ensures any errors generated occur as if no input were consumed.
      * @since 3.1.0
      * @group adj
      */
    def amend[A](p: Parsley[A]): Parsley[A] = new Parsley(new frontend.ErrorAmend(p.internal, partial = false))

    /** This combinator adjusts any error messages generated by the given parser so that they
      * occur at the position recorded on entry to this combinator, but retains the original offset.
      *
      * Similar to [[amend `amend`]], but retains the original offset the error occurred at. This is known
      * as its ''underlying offset'' as opposed to the visual ''presentation offset''. To the reader, the
      * error messages appears as if no input was consumed, but for the purposes of error message merging
      * the error is still deeper. A key thing to note is that two errors can only merge if they are at
      * the same presentation ''and'' underlying offsets: if they are not the deeper of the two ''dominates''.
      *
      * The ability for an error to still dominate others after partial amendment can be useful for allowing
      * it to avoid being lost when merging with errors that are deeper than the presentation offset but
      * shallower than the underlying.
      *
      * @example {{{
      * scala> val greeting = string("hello world") <* char('!')
      * scala> val shortGreeting = string("h") <* (char('i') | string("ey")) <* char('!')
      * // here, the shortGreeting, despite not getting as far into the input is dominating the amended long greeting
      * scala> (amend(atomic(greeting)).label("hello world!") | shortGreeting).parse("hello world.")
      * val res0 = Failure((line 1, column 2):
      *   unexpected "el"
      *   expected "ey" or "i"
      *   >hello world.
      *     ^^)
      * // here it appears to start at the `h` point, but notably dominates the short greeting
      * scala> (amend(atomic(greeting)).label("hello world!") | shortGreeting).parse("hello world.")
      * val res1= Failure((line 1, column 1):
      *   unexpected "h"
      *   expected hello world!
      *   >hello world.
      *    ^)
      * }}}
      *
      * @param p a parser whose error messages should be adjusted.
      * @return a parser that parses `p` but ensures any errors generated occur as if no input were consumed.
      * @since 4.4.0
      * @group adj
      */
    def partialAmend[A](p: Parsley[A]): Parsley[A] = new Parsley(new frontend.ErrorAmend(p.internal, partial = true))

    /** This combinator prevents the action of any enclosing `amend` on the errors generated by the given
      * parser.
      *
      * Sometimes, the error adjustments performed by [[amend `amend`]] should only affect errors generated
      * within a certain part of a parser and not the whole thing; in this case, `entrench` can be used
      * to protect sub-parsers from having their errors adjusted, providing a much more fine-grained
      * scope for error adjustment.
      *
      * @example In this example, the `ident` parser should not allow keywords, and these error messages
      * should be generated from the start of the identifier, not the end. However any errors generated
      * ''within'' the identifier itself should remain at their regular offsets.
      *
      * {{{
      * val ident = amend {
      *     entrench(stringOfSome(letter)).filterOut {
      *         case v if keywords.contains(v) => s"keyword &#36;v cannot be an identifier"
      *     }
      * }
      * }}}
      *
      * '''In reality though, `filterOut` has an `amend` and `entrench` built into it.'''
      *
      * @param p a parser whose error messages should not be adjusted by any surrounding [[amend `amend`]].
      * @return a parser that parses `p` but ensures any error messages are generated normally.
      * @since 3.1.0
      * @group adj
      */
    def entrench[A](p: Parsley[A]): Parsley[A] = new Parsley(new frontend.ErrorEntrench(p.internal))

    /** This combinator undoes the action of any `entrench` combinators on the given parser.
      *
      * Entrenchment is important for preventing the incorrect amendment of certain parts of sub-errors
      * for a parser, but it may be then undesireable to block further amendments from elsewhere in the
      * parser. This combinator can be used to cancel all entrenchment after the critical section has
      * passed.
      *
      * @param p a parser that should no longer be under the affect of an `entrench` combinator
      * @return a parser that parses `p` and allows its error messages to be amended.
      * @since 4.2.0
      * @group adj
      */
    def dislodge[A](p: Parsley[A]): Parsley[A] = dislodge(Int.MaxValue)(p)
    /** This combinator undoes the action of `by` many `entrench` combinators on the given parser.
      *
      * Entrenchment is important for preventing the incorrect amendment of certain parts of sub-errors
      * for a parser, but it may be then undesireable to block further amendments from elsewhere in the
      * parser. This combinator can be used to cancel several, but potentially not all entrenchments after the
      * critical section has passed.
      *
      * @param by the number of entrenchments to undo
      * @param p a parser that should no longer be under the affect of an `entrench` combinator
      * @return a parser that parses `p` and ''may'' allows its error messages to be amended if all entrenchments are undone
      * @since 4.4.0
      * @group adj
      */
    def dislodge[A](by: Int)(p: Parsley[A]): Parsley[A] = new Parsley(new frontend.ErrorDislodge(by, p.internal))

    /** This combinator first tries to amend the position of any error generated by the given parser,
      * and if the error was entrenched will dislodge it instead.
      *
      * @param p a parser whose error messages should be amended unless its been entrenched.
      * @return a parser that parses `p` but ensures any errors generated occur as if no input were consumed.
      * @since 4.2.0
      * @see [[amend `amend`]] and [[dislodge[A](p:parsley\.Parsley[A])* `dislodge`]]
      * @group adj
      */
    def amendThenDislodge[A](p: Parsley[A]): Parsley[A] = amendThenDislodge(Int.MaxValue)(p)
    /** This combinator first tries to amend the position of any error generated by the given parser,
      * and if the error was entrenched will dislodge it `by` many times instead.
      *
      * @param p a parser whose error messages should be amended unless its been entrenched.
      * @return a parser that parses `p` but ensures any errors generated occur as if no input were consumed.
      * @since 4.4.0
      * @see [[amend `amend`]] and [[dislodge[A](by:Int)* `dislodge`]]
      * @group adj
      */
    def amendThenDislodge[A](by: Int)(p: Parsley[A]): Parsley[A] = dislodge(by)(amend(p))

    // These don't need coverage really, they are basically the same as the ones above
    // $COVERAGE-OFF$
    /** This combinator first tries to partially amend the position of any error generated by the given parser,
      * and if the error was entrenched will dislodge it instead.
      *
      * @param p a parser whose error messages should be amended unless its been entrenched.
      * @return a parser that parses `p` but ensures any errors generated occur as if no input were consumed.
      * @since 4.4.0
      * @see [[partialAmend `partialAmend`]] and [[dislodge[A](p:parsley\.Parsley[A])* `dislodge`]]
      * @group adj
      */
    def partialAmendThenDislodge[A](p: Parsley[A]): Parsley[A] = partialAmendThenDislodge(Int.MaxValue)(p)
    /** This combinator first tries to partially amend the position of any error generated by the given parser,
      * and if the error was entrenched will dislodge it `by` many times instead.
      *
      * @param p a parser whose error messages should be amended unless its been entrenched.
      * @return a parser that parses `p` but ensures any errors generated occur as if no input were consumed.
      * @since 4.4.0
      * @see [[partialAmend `partialAmend`]] and [[dislodge[A](by:Int)* `dislodge`]]
      * @group adj
      */
    def partialAmendThenDislodge[A](by: Int)(p: Parsley[A]): Parsley[A] = dislodge(by)(partialAmend(p))
    // $COVERAGE-ON$

    /** This combinator marks any errors within the given parser as being ''lexical errors''.
      *
      * When an error is marked as a ''lexical error'', it sets a flag within the error that is
      * passed to [[parsley.errors.ErrorBuilder.unexpectedToken `ErrorBuilder.unexpectedToken`]]: this
      * should be used to prevent `Lexer`-based token extraction from being performed on an error,
      * since lexing errors cannot be the result of unexpected tokens.
      *
      * @param p the parser that serves as a token.
      * @return a parser that parses `p` but ensures any error messages are marked as lexical errors.
      * @since 4.0.0
      * @group adj
      */
    def markAsToken[A](p: Parsley[A]): Parsley[A] = new Parsley(new frontend.ErrorLexical(p.internal))

    /** This class exposes helpful combinators that are specialised for generating more helpful errors messages.
      *
      * This extension class operates on values that are convertible to parsers. It enables the use of
      * error combinators, which can be used for data validation, error annotation, or immediate failing.
      *
      * @constructor This constructor should not be called manually, it is designed to be used via Scala's implicit resolution.
      * @param p the value that this class is enabling methods on.
      * @param con a conversion that allows values convertible to parsers to be used.
      * @tparam P the type of base value that this class is used on (the conversion to `Parsley`) is summoned automatically.
      * @version 3.0.0
      * @group ext
      *
      * @groupprio rich 0
      * @groupname rich Error Enrichment Combinators
      * @groupdesc rich
      *     These combinators add additional information - or refine the existing information within - to
      *     an error message that has been generated within the scope of the parser they have been called on.
      *     These are a very basic, but effective, way of improving the quality of error messages generated
      *     by Parsley.
      *
      * @groupprio filter 10
      * @groupname filter Filtering Combinators
      * @groupdesc filter
      *     These combinators perform filtering on a parser, with particular emphasis on generating meaningful
      *     error messages if the filtering fails. This is particularly useful for data validation within the
      *     parser, as very instructive error messages describing what went wrong can be generated. These combinators
      *     often filter using a `PartialFunction`: this may be because they combine filtering with mapping (in which
      *     case, the error message is provided separately), or the function may produce a `String`.
      *
      *     In these cases, the partial function is producing the error messages: if the input to the function is
      *     defined, this means that it is invalid and the filtering will fail using the message obtained from the
      *     succesful partial function invocation.
      *
      * @groupprio genFilter 15
      * @groupname genFilter Generic Filtering Combinators
      * @groupdesc genFilter
      *     This combinators generalise the combinators from above, which are all special cases of them. Each of these
      *     takes the characteristic predicate or function of the regular variants, but takes an `errGen` object that
      *     can be used to fine-tune the error messages. These offer some flexiblity not offered by the specialised
      *     filtering combinators, but are a little more verbose to use.
      *
      * @groupprio fail 20
      *
      * @define observably
      *     *a parser is said to ''observably'' consume input when error messages generated by a parser `p` occur at a deeper
      *     offset than `p` originally started at. While this sounds like it is the same as "having consumed input" for the
      *     purposes of backtracking, they are disjoint concepts:
      *
      *       1. in `atomic(p)`, `p` can ''observably'' consume input even though the wider parser does not consume input due to the `atomic`.
      *       1. in `amend(p)`, `p` can consume input and may not backtrack even though the consumption is not ''observable'' in the error
      *          message due to the `amend`.
      *
      * @define autoAmend
      *     when this combinator fails (and not this parser itself), it will generate errors rooted at the start of the
      *     parse (as if [[parsley.errors.combinator$.amend `amend`]] had been used) and the caret will span the entire
      *     successful parse of this parser.
      *
      * @define partialAmend
      *     this combinator will generate error messages rooted at the start of the previously successful parse of this
      *     parser, but only in terms of their position: the actual error is generated at the end of the parse, which
      *     means it takes priority over sibling errors. This is because the error concerns the whole parse (for caret)
      *     and morally starts where this parser started (as it caused the failure), however, if it had full `amend`-like
      *     behaviour these errors would often disappear.
      */
    implicit final class ErrorMethods[P, +A](p: P)(implicit con: P => Parsley[A]) {
        /** This combinator filters the result of this parser using the given partial-predicate, succeeding only when the predicate is undefined.
          *
          * First, parse this parser. If it succeeds then take its result `x` and test if `pred.isDefinedAt(x)` is true. If it is
          * false, the parser succeeds, returning `x`. Otherwise, `pred(x)` will yield a reason `reason` and the parser will
          * fail with `reason` provided to the generated error message à la [[explain `explain`]].
          *
          * This is useful for performing data validation, but where a definitive reason can be given for the failure. In this instance,
          * the rest of the error message is generated as normal, with the expected and unexpected components still given, along with
          * any other generated reasons.
          *
          * @example {{{
          * scala> import parsley.character.letter
          * scala> val keywords = Set("if", "then", "else")
          * scala> val ident = stringOfSome(letter).filterOut {
          *     case v if keywords.contains(v) => s"keyword &#36;v cannot be an identifier"
          * }
          * scala> ident.parse("hello")
          * val res0 = Success("hello")
          * scala> ident.parse("if")
          * val res1 = Failure(..)
          * }}}
          *
          * @since 3.0.0
          * @param pred the predicate that is tested against the parser result, which also generates errors.
          * @return a parser that returns the result of this parser if it fails the predicate.
          * @see [[parsley.Parsley.filterNot `filterNot`]], which is a basic version of this same combinator with no customised reason.
          * @see [[guardAgainst `guardAgainst`]], which is similar to `filterOut`, except it generates a ''specialised'' error as opposed to just a reason.
          * @note implemented in terms of [[filterWith `filterWith`]].
          * @note $autoAmend
          * @group filter
          */
        def filterOut(pred: PartialFunction[A, String]): Parsley[A] = {
            this.filterWith(new VanillaGen[A] {
                override def reason(x: A) = Some(pred(x))
            })(!pred.isDefinedAt(_))
        }

        /** This combinator filters the result of this parser using the given partial-predicate, succeeding only when the predicate is undefined.
          *
          * First, parse this parser. If it succeeds then take its result `x` and test of `pred.isDefinedAt(x)` is true. If it is false,
          * the parser succeeds, returning `x`. Otherwise `pred(x)` will yield an error message `msg` and the parser will fail, producing
          * a ''specialised'' error only consisting of the message `msg` à la [[fail(caretWidth:Int,msg0:String,msgs:String*)*  `fail`]].
          *
          * This is useful for performing data validation, but where failure is not tied to the grammar but some other property of
          * the results. For instance, with the identifier example given for `filterOut`, it is reasonable to suggest that an identifier
          * was expected, and a keyword is not a valid identifier: i.e. these components still make sense. Where `guardAgainst` shines,
          * however, is in scenarios where the expected alternatives, or the unexpected component itself distract from the cause of the
          * error, or are irrelevant in some way. This might be because `guardAgainst` is checking some property of the data that is
          * ''possible'' to encode in the grammar, but otherwise ''impractical'', either because it is hard to maintain or generates
          * poor error messages for the user.
          *
          * @example Suppose we are parsing a data-format for graphs, and a restriction has been placed that ensures that the
          *          numeric identifiers of each declared node must be ordered. This has, for whatever reason, been specified
          *          as a syntactic property of the data. This is possible to encode using context-sensitive parsing (since each
          *          new node can only be parsed according to the previous one), but is fairly difficult and impractical. Instead,
          *          when all the declarations have been read, a `guardAgainst` can be used to prevent mis-ordering:
          * {{{
          * val node = integer
          * val nodes = many(node).guardAgainst {
          *     case ns if ns.nonEmpty
          *             && ns.zip(ns.tail).exists { case (x, y) => x == y } =>
          *         val Some((x, _)) = ns.zip(ns.tail).find { case (x, y) => x == y }
          *         Seq(s"node &#36;x has been declared twice")
          *     case ns if ns.nonEmpty
          *             && ns.zip(ns.tail).exists { case (x, y) => x > y } =>
          *         val Some((x, y)) = ns.zip(ns.tail).find { case (x, y) => x > y }
          *         Seq(s"nodes &#36;x and &#36;y are declared in the wrong order", "all nodes should be ordered")
          * }
          * }}}
          *
          * @since 4.0.0
          * @param pred the predicate that is tested against the parser result, which also generates errors.
          * @return a parser that returns the result of this parser if it fails the predicate.
          * @see [[parsley.Parsley.filterNot `filterNot`]], which is a basic version of this same combinator with no customised error message.
          * @see [[filterOut `filterOut`]], which is similar to `guardAgainst`, except it generates a reason for failure and not a ''specialised'' error.
          * @see [[[collectMsg[B](msggen:A=>Seq[String])*  `collectMsg`]]], which is similar to `guardAgainst`, but can also transform the data on success.
          * @note $autoAmend
          * @note implemented in terms of [[filterWith `filterWith`]].
          * @group filter
          */
        def guardAgainst(pred: PartialFunction[A, Seq[String]]): Parsley[A] = {
            this.filterWith(new SpecializedGen[A] {
                override def messages(x: A) = pred(x)
            })(!pred.isDefinedAt(_))
        }

        /** This combinator applies a partial function `pf` to the result of this parser if its result is defined for `pf`, failing if it is not.
          *
          * First, parse this parser. If it succeeds, test whether its result `x` is in the domain of the partial function `pf`. If it is defined for
          * `pf`, return `pf(x)`. Otherwise, if the result was undefined then fail producing a ''specialised'' error message with `msg`. Equivalent
          * to a `guardAgainst` (whose `msggen` ignores its argument) followed by a `map`.
          *
          * @example A good example of this combinator in use is for handling overflow in numeric literals.
          * {{{
          * val integer: Parsley[BigInt] = ...
          * // this should be amended/entrenched for best results
          * val int16: Parsley[Short] =
          *     integer.collectMsg("integer literal should within the range -2^16 to +2^16-1") {
          *         case x if x >= Short.MinValue
          *                && x <= Short.MaxValue => x.toShort
          *     }
          * }}}
          *
          * @since 3.0.0
          * @param msg0 the first error message to use if the filtering fails.
          * @param msgs the remaining error messages to use if the filtering fails.
          * @param pf the partial function used to both filter the result of this parser and transform it.
          * @return a parser which returns the result of this parser applied to pf, if possible.
          * @see [[parsley.Parsley.collect `collect`]], which is a basic version of this same combinator with no customised error message.
          * @see [[guardAgainst `guardAgainst`]], which is similar to `collectMsg`, except it does not transform the data.
          * @note $autoAmend
          * @note implemented in terms of [[collectWith `collectWith`]].
          * @group filter
          */
        def collectMsg[B](msg0: String, msgs: String*)(pf: PartialFunction[A, B]): Parsley[B] = this.collectMsg(_ => msg0 +: msgs)(pf)

        /** This combinator applies a partial function `pf` to the result of this parser if its result is defined for `pf`, failing if it is not.
          *
          * First, parse this parser. If it succeeds, test whether its result `x` is in the domain of the partial function `pf`. If it is defined for
          * `pf`, return `pf(x)`. Otherwise, if the result was undefined then fail producing a ''specialised'' error message with `msggen(x)`. Equivalent
          * to a `guardAgainst` followed by a `map`.
          *
          * @example A good example of this combinator in use is for handling overflow in numeric literals.
          * {{{
          * val integer: Parsley[BigInt] = ...
          * // this should be amended/entrenched for best results
          * val int16: Parsley[Short] =
          *     integer.collectMsg(n => Seq(s"integer literal &#36;n is not within the range -2^16 to +2^16-1")) {
          *         case x if x >= Short.MinValue
          *                && x <= Short.MaxValue => x.toShort
          *     }
          * }}}
          *
          * @since 4.0.0
          * @param msggen a function that generates the error messages to use if the filtering fails.
          * @param pf the partial function used to both filter the result of this parser and transform it.
          * @return a parser which returns the result of this parser applied to pf, if possible.
          * @see [[parsley.Parsley.collect `collect`]], which is a basic version of this same combinator with no customised error message.
          * @see [[guardAgainst `guardAgainst`]], which is similar to `collectMsg`, except it does not transform the data.
          * @see [[mapFilterMsg `mapFilterMsg`]], which is similar to `collectMsg`, except uses a `A => Either[Seq[String], B]` function.
          * @note $autoAmend
          * @note implemented in terms of [[collectWith `collectWith`]].
          * @group filter
          */
        def collectMsg[B](msggen: A => Seq[String])(pf: PartialFunction[A, B]): Parsley[B] = {
            this.collectWith(new SpecializedGen[A] {
                override def messages(x: A) = msggen(x)
            })(pf)
        }

        /** This combinator conditionally transforms the result of this parser with a given function, if a `Left` is
          * returned generates an error with its contexts, otherwise results the result inside the `Right`.
          *
          * Like [[Parsley.mapFilter `mapFilter`]], except allows for the error message generated to be
          * specified for invalid parses.
          *
          * @example A good example of this combinator in use is for handling overflow in numeric literals.
          * {{{
          * val integer: Parsley[BigInt] = ...
          * // this should be amended/entrenched for best results
          * val int16: Parsley[Short] =
          *     integer.filterWithMsg {
          *         case x if x >= Short.MinValue
          *                && x <= Short.MaxValue => Right(x.toShort)
          *         case x => Left(Seq(s"integer literal &#36;n is not within the range -2^16 to +2^16-1"))
          *     }
          * }}}
          *
          * @since 5.0.0
          * @param f the predicate that is tested against the parser result.
          * @return a parser which returns the result of this parser applied to pf, if possible.
          * @see [[parsley.Parsley.mapFilter `mapFilter`]], which is a basic version of this same combinator with no customised error message.
          * @note $autoAmend
          * @note implemented in terms of [[mapFilterWith `mapFilterWith`]].
          * @group filter
          */
        def mapFilterMsg[B](f: A => Either[Seq[String], B]): Parsley[B] = {
            this.mapFilterWith(new SpecializedGen[A] {
                override def messages(x: A) = {
                    val Left(errs) = f(x): @unchecked
                    errs
                }
            })(x => f(x).toOption)
        }

        /** This combinator filters the result of this parser using the given partial-predicate, succeeding only when the predicate is undefined.
          *
          * First, parse this parser. If it succeeds then take its result `x` and test if `pred.isDefinedAt(x)` is true. If it is
          * false, the parser succeeds, returning `x`. Otherwise, `pred(x)` will yield a unexpected label and the parser will
          * fail using [[combinator.unexpected(caretWidth:Int,item:String)* `unexpected`]] and that label.
          *
          * This is useful for performing data validation, but where a the failure results in the entire token being unexpected. In this instance,
          * the rest of the error message is generated as normal, with the expected components still given, along with
          * any generated reasons.
          *
          * @example {{{
          * scala> import parsley.character.letter
          * scala> val keywords = Set("if", "then", "else")
          * scala> val ident = stringOfSome(letter).unexpectedWhen {
          *     case v if keywords.contains(v) => s"keyword &#36;v"
          * }
          * scala> ident.parse("hello")
          * val res0 = Success("hello")
          * scala> ident.parse("if")
          * val res1 = Failure(..)
          * }}}
          *
          * @since 3.0.0
          * @param pred the predicate that is tested against the parser result, which also generates errors.
          * @return a parser that returns the result of this parser if it fails the predicate.
          * @see [[parsley.Parsley.filterNot `filterNot`]], which is a basic version of this same combinator with no unexpected message.
          * @see [[filterOut `filterOut`]], which is a variant that produces a reason for failure as opposed to an unexpected message.
          * @see [[guardAgainst `guardAgainst`]], which is similar to `unexpectedWhen`, except it generates a ''specialised'' error instead.
          * @see [[unexpectedWithReasonWhen `unexpectedWithReasonWhen`]], which is similar, but also has a reason associated.
          * @note $autoAmend
          * @note implemented in terms of [[filterWith `filterWith`]].
          * @group filter
          */
        def unexpectedWhen(pred: PartialFunction[A, String]): Parsley[A] = {
            this.filterWith(new VanillaGen[A] {
                override def unexpected(x: A) = VanillaGen.NamedItem(pred(x))
            })(!pred.isDefinedAt(_))
        }

        /** This combinator filters the result of this parser using the given partial-predicate, succeeding only when the predicate is undefined.
          *
          * First, parse this parser. If it succeeds then take its result `x` and test if `pred.isDefinedAt(x)` is true. If it is
          * false, the parser succeeds, returning `x`. Otherwise, `pred(x)` will yield a unexpected label and the parser will
          * fail using [[combinator.unexpected(caretWidth:Int,item:String)* `unexpected`]] and that label as well as a reason.
          *
          * This is useful for performing data validation, but where a the failure results in the entire token being unexpected. In this instance,
          * the rest of the error message is generated as normal, with the expected components still given, along with
          * any generated reasons.
          *
          * @example {{{
          * scala> import parsley.character.letter
          * scala> val keywords = Set("if", "then", "else")
          * scala> val ident = stringOfSome(letter).unexpectedWhenWithReason {
          *     case v if keywords.contains(v) => (s"keyword &#36;v", "keywords cannot be identifiers")
          * }
          * scala> ident.parse("hello")
          * val res0 = Success("hello")
          * scala> ident.parse("if")
          * val res1 = Failure(..)
          * }}}
          *
          * @param pred the predicate that is tested against the parser result, which also generates errors.
          * @return a parser that returns the result of this parser if it fails the predicate.
          * @see [[parsley.Parsley.filterNot `filterNot`]], which is a basic version of this same combinator with no unexpected message or reason.
          * @see [[filterOut `filterOut`]], which is a variant that just produces a reason for failure with no unexpected message.
          * @see [[guardAgainst `guardAgainst`]], which is similar to `unexpectedWhen`, except it generates a ''specialised'' error instead.
          * @see [[unexpectedWhen `unexpectedWhen`]], which is similar, but with no associated reason.
          * @since 4.2.0
          * @note implemented in terms of [[filterWith `filterWith`]].
          * @group filter
          */
        def unexpectedWithReasonWhen(pred: PartialFunction[A, (String, String)]): Parsley[A] = {
            this.filterWith(new VanillaGen[A] {
                override def unexpected(x: A) = VanillaGen.NamedItem(pred(x)._1)
                override def reason(x: A) = Some(pred(x)._2)
            })(!pred.isDefinedAt(_))
        }

        /** This combinator changes the expected component of any errors generated by this parser.
          *
          * When this parser fails having not ''observably''* consumed input, the expected component of the generated
          * error message is set to be the given `item`.
          *
          * $observably
          * @param item the name to give to the expected component of any qualifying errors.
          * @param items any further labels to assign to this parser.
          * @return a parser that expects `item` on failure.
          * @since 3.0.0
          * @group rich
          */
        def label(item: String, items: String*): Parsley[A] = {
            require(item.nonEmpty && items.forall(_.nonEmpty), "labels cannot be empty strings")
            new Parsley(new frontend.ErrorLabel(con(p).internal, item, items))
        }

        /** This combinator changes the expected component of any errors generated by this parser.
          *
          * This is just an alias for the `label` combinator.
          *
          * ''Known as `&lt;?&gt;` in Haskell.''
          *
          * @since 3.0.0
          * @see [[label `label`]]
          * @group rich
          */
        def ?(item: String): Parsley[A] = this.label(item)

        /** This combinator adds a reason to error messages generated by this parser.
          *
          * When this parser fails having not ''observably''* consumed input, this combinator adds
          * `reason` to the error message, which should justify why the error occured. Unlike error
          * labels, which may persist if more progress is made having not consumed input, reasons
          * are not carried forward in the error message, and are lost.
          *
          * $observably
          * @param reason the reason why a parser failed.
          * @return a parser that produces the given reason for failure if it fails.
          * @since 3.0.0
          * @group rich
          */
        def explain(reason: String): Parsley[A] = {
            require(reason.nonEmpty, "reasons cannot be empty strings")
            new Parsley(new frontend.ErrorExplain(con(p).internal, reason))
        }

        // TODO: check this documentation, I'm not sure it's correct
        /** This combinator hides the expected component of errors generated by this parser.
          *
          * When this parser fails having not ''observably''* consumed input, this combinator
          * hides any error labels assigned to the expected item by any `label` combinators,
          * or indeed the base raw labels produced by the input consuming combinators themselves.
          *
          * This can be useful, say, for hiding whitespace labels, which are not normally useful
          * information to include in an error message for whitespace insensitive grammars.
          *
          * $observably
          * @since 3.0.0
          * @return a parser that does not produce an expected component on failure.
          * @group rich
          */
        def hide: Parsley[A] = new Parsley(new frontend.ErrorHide(con(p).internal))

        /** This combinator filters the result of this parser with the given predicate, generating an error with the
          * given error generator if the function returned `false`.
          *
          * Like [[Parsley.filter `filter`]], except allows for the error message generated to be fine-tuned with
          * respect to the parsers result and width of input consumed using an [[ErrorGen `ErrorGen`]] object.
          *
          * @param errGen how to generate error messages based on the result of this parser.
          * @param pred the predicate that is tested against the parser result.
          * @since 4.4.0
          * @group genFilter
          */
        def filterWith(errGen: ErrorGen[A])(pred: A => Boolean): Parsley[A] = combinator.filterWith(con(p))(pred, errGen)

        /** This combinator conditionally transforms the result of this parser with a given partial function, generating an error with the
          * given error generator if the function is not defined on the result of this parser.
          *
          * Like [[Parsley.collect `collect`]], except allows for the error message generated to be fine-tuned with
          * respect to the parsers result and width of input consumed using an [[ErrorGen `ErrorGen`]] object.
          *
          * @param pf the partial function used to both filter the result of this parser and transform it.
          * @param errGen how to generate error messages based on the result of this parser.
          * @since 4.4.0
          * @group genFilter
          */
        def collectWith[B](errGen: ErrorGen[A])(pf: PartialFunction[A, B]): Parsley[B] = combinator.collectWith(con(p))(pf, errGen)

        /** This combinator conditionally transforms the result of this parser with a given function, generating an error with the
          * given error generator if the function returns `None` given the result of this parser.
          *
          * Like [[Parsley.mapFilter `mapFilter`]], except allows for the error message generated to be fine-tuned with
          * respect to the parsers result and width of input consumed using an [[ErrorGen `ErrorGen`]] object.
          *
          * @param f the function used to both filter the result of this parser and transform it.
          * @param errGen how to generate error messages based on the result of this parser.
          * @since 4.4.0
          * @group genFilter
          */
        def mapFilterWith[B](errGen: ErrorGen[A])(f: A => Option[B]): Parsley[B] = combinator.mapFilterWith(con(p))(f, errGen)
    }

    @inline private [parsley] def filterWith[A](p: Parsley[A])(f: A => Boolean, err: ErrorGen[A]): Parsley[A] = {
        new Parsley(new frontend.Filter(p.internal, f, err.internal))
    }

    @inline private [parsley] def collectWith[A, B](p: Parsley[A])(f: PartialFunction[A, B], err: ErrorGen[A]): Parsley[B] = {
        mapFilterWith(p)(f.lift, err)
    }

    @inline private [parsley] def mapFilterWith[A, B](p: Parsley[A])(f: A => Option[B], err: ErrorGen[A]): Parsley[B] = {
        new Parsley(new frontend.MapFilter(p.internal, f, err.internal))
    }
}
