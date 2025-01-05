/*
 * Copyright 2020 Parsley Contributors <https://github.com/j-mie6/Parsley/graphs/contributors>
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package parsley.errors

import parsley.Parsley, Parsley.{atomic, select, unit}
import parsley.errors.combinator.{amend, ErrorMethods}
import parsley.position.withWidth

/** This module contains combinators that help facilitate the error message generational patterns ''Verified Errors'' and ''Preventative Errors''.
  *
  * In particular, exposes an extension class `VerifiedErrors` that facilitates creating verified errors in many different formats.
  *
  * @group combinators
  * @since 4.2.0
  */
object patterns {
    /** This class exposes combinators related to the ''Verified Errors'' parser design pattern.
      *
      * This extension class operates on values that are convertible to parsers. The combinators it enables
      * allow for the parsing of known illegal values, providing richer error messages in case they succeed.
      *
      * @constructor This constructor should not be called manually, it is designed to be used via Scala's implicit resolution.
      * @param p the value that this class is enabling methods on.
      * @param con a conversion that allows values convertible to parsers to be used.
      * @tparam P the type of base value that this class is used on (the conversion to `Parsley`) is summoned automatically.
      * @since 4.2.0
      *
      * @define autoAmend
      *     when this combinator fails (and not this parser itself), it will generate errors rooted at the start of the
      *     parse (as if [[parsley.errors.combinator$.amend `amend`]] had been used) and the caret will span the entire
      *     successful parse of this parser.
      *
      * @define atomicNonTerminal
      *     when this parser is not to be considered as a terminal error, use `atomic` around the ''entire'' combinator to
      *     allow for backtracking if this parser succeeds (and therefore fails).
      */
    implicit final class VerifiedErrors[P, A](p: P)(implicit con: P => Parsley[A]) {
        /** Ensures this parser does not succeed, failing with a specialised error based on this parsers result if it does.
          *
          * If this parser succeeds, input is consumed and this combinator will fail, producing an error message
          * based on the parsed result. However, if this parser fails, no input is consumed and an empty error is generated.
          * This parser will produce no labels if it fails.
          *
          * @param msggen the function that generates the error messages from the parsed value.
          * @since 4.2.0
          * @note $autoAmend
          * @note $atomicNonTerminal
          */
        def verifiedFail(msggen: A => Seq[String]): Parsley[Nothing] = this.verifiedWith {
            new SpecializedGen[A] {
                override def messages(x: A) = msggen(x)
            }
        }

        /** Ensures this parser does not succeed, failing with a specialised error if it does.
          *
          * If this parser succeeds, input is consumed and this combinator will fail, producing an error message
          * based on the given messages. However, if this parser fails, no input is consumed and an empty error is generated.
          * This parser will produce no labels if it fails.
          *
          * @param msg0 the first message in the error message.
          * @param msgs the remaining messages that will make up the error message.
          * @since 4.2.0
          * @note $autoAmend
          * @note $atomicNonTerminal
          */
        def verifiedFail(msg0: String, msgs: String*): Parsley[Nothing] = this.verifiedFail(_ => msg0 +: msgs)

        /** Ensures this parser does not succeed, failing with a vanilla error with an unexpected message and caret spanning the parse.
          *
          * If this parser succeeds, input is consumed and this combinator will fail, producing an unexpected message the same width as
          * the parse. However, if this parser fails, no input is consumed and an empty error is generated.
          * This parser will produce no labels if it fails.
          *
          * @since 4.2.0
          * @note $autoAmend
          * @note $atomicNonTerminal
          */
        def verifiedUnexpected: Parsley[Nothing] = this.verifiedWithVanillaRaw(_ => None)

        /** Ensures this parser does not succeed, failing with a vanilla error with an unexpected message and caret spanning the parse and a given reason.
          *
          * If this parser succeeds, input is consumed and this combinator will fail, producing an unexpected message the same width as
          * the parse along with the given reason. However, if this parser fails, no input is consumed and an empty error is generated.
          * This parser will produce no labels if it fails.
          *
          * @param reason the reason that this parser is illegal.
          * @since 4.5.0
          * @note $autoAmend
          * @note $atomicNonTerminal
          */
        def verifiedExplain(reason: String): Parsley[Nothing] = this.verifiedWithVanillaRaw(_ => Some(reason))

        /** Ensures this parser does not succeed, failing with a vanilla error with an unexpected message and caret spanning the parse and a reason generated
          * from this parser's result.
          *
          * If this parser succeeds, input is consumed and this combinator will fail, producing an unexpected message the same width as
          * the parse along with a reason generated from the successful parse. However, if this parser fails, no input is consumed and an empty error
          * is generated. This parser will produce no labels if it fails.
          *
          * @param reason a function that produces a reason for the error given the parsed result.
          * @since 4.5.0
          * @note $autoAmend
          * @note $atomicNonTerminal
          */
        def verifiedExplain(reason: A => String): Parsley[Nothing] = this.verifiedWithVanillaRaw(x => Some(reason(x)))

        /** Ensures this parser does not succeed, failing with an error as described by the given `ErrorGen` object.
          *
          * If this parser succeeds, input is consumed and this combinator will fail, producing an error message using
          * the given `errGen` with width the same as the parsed data. However, if this parser fails, no input is consumed
          * and an empty error is generated. This parser will produce no labels if it fails.
          *
          * @param err the generator that produces the error message.
          * @since 4.4.0
          * @note $autoAmend
          * @note $atomicNonTerminal
          */
        def verifiedWith(err: ErrorGen[A]): Parsley[Nothing] = amend(err(withWidth(atomic(con(p)).hide)))

        @inline private def verifiedWithVanilla(unexGen: A => VanillaGen.UnexpectedItem, reasonGen: A => Option[String]) = verifiedWith {
            new VanillaGen[A] {
                override def unexpected(x: A) = unexGen(x)
                override def reason(x: A) = reasonGen(x)
            }
        }

        @inline private def verifiedWithVanillaRaw(reasonGen: A => Option[String]) = verifiedWithVanilla(_ => VanillaGen.RawItem, reasonGen)
    }

    /** This class exposes combinators related to the ''Preventative Errors'' parser design pattern.
      *
      * This extension class operates on values that are convertible to parsers. The combinators it enables
      * allow for the parsing of known illegal values, providing richer error messages in case they succeed.
      *
      * @constructor This constructor should not be called manually, it is designed to be used via Scala's implicit resolution.
      * @param p the value that this class is enabling methods on.
      * @param con a conversion that allows values convertible to parsers to be used.
      * @tparam P the type of base value that this class is used on (the conversion to `Parsley`) is summoned automatically.
      * @since 4.4.0
      *
      * @define autoAmend
      *     when this combinator fails (and not this parser itself), it will generate errors rooted at the start of the
      *     parse (as if [[parsley.errors.combinator$.amend `amend`]] had been used) and the caret will span the entire
      *     successful parse of this parser.
      *
      * @define atomicNonTerminal
      *     when this parser is not to be considered as a terminal error, use `atomic` around the ''entire'' combinator to
      *     allow for backtracking if this parser succeeds (and therefore fails).
      */
    implicit final class PreventativeErrors[P, A](p: P)(implicit con: P => Parsley[A]) {
        /** Ensures this parser does not succeed, failing with a specialised error based on this parsers result if it does.
          *
          * If this parser succeeds, input is consumed and this combinator will fail, producing an error message
          * based on the parsed result. However, if this parser fails, no input is consumed and this combinator succeeds.
          * This parser will produce no evidence of running if it succeeds.
          *
          * @param msggen the function that generates the error messages from the parsed value.
          * @since 4.4.0
          * @note $autoAmend
          * @note $atomicNonTerminal
          */
        def preventativeFail(msggen: A => Seq[String]): Parsley[Unit] = this.preventWith(new SpecializedGen[A] {
            override def messages(x: A) = msggen(x)
        })

        /** Ensures this parser does not succeed, failing with a fixed specialised error if it does.
          *
          * If this parser succeeds, input is consumed and this combinator will fail, producing an error message with the
          * given messages. However, if this parser fails, no input is consumed and this combinator succeeds.
          * This parser will produce no evidence of running if it succeeds.
          *
          * @param msg0 the first message in the error message.
          * @param msgs the remaining messages that will make up the error message.
          * @since 4.4.0
          * @note $autoAmend
          * @note $atomicNonTerminal
          */
        def preventativeFail(msg0: String, msgs: String*): Parsley[Unit] = this.preventativeFail(_ => msg0 +: msgs)

        /** Ensures this parser does not succeed, failing with a vanilla error with an unexpected message and caret spanning the parse and a reason generated
          * from this parser's result.
          *
          * If this parser succeeds, input is consumed and this combinator will fail, producing an unexpected message the same width as
          * the parse along with a reason generated from the successful parse along with the given labels. However, if this parser fails, no input is
          * consumed and this combinator succeeds. This parser will produce no evidence of running if it succeeds.
          *
          * @param reason a function that produces a reason for the error given the parsed result.
          * @param labels the labels that should be expected if this parser hadn't succeeded.
          * @since 4.4.0
          * @note $autoAmend
          * @note $atomicNonTerminal
          */
        def preventativeExplain(reason: A => String, labels: String*): Parsley[Unit] = this.preventWithVanillaRaw(x => Some(reason(x)), labels: _*)

        /** Ensures this parser does not succeed, failing with a vanilla error with an unexpected message and caret spanning the parse and a given reason.
          *
          * If this parser succeeds, input is consumed and this combinator will fail, producing an unexpected message the same width as
          * the parse along with the given reason and given labels. However, if this parser fails, no input is consumed and this combinator succeeds.
          * This parser will produce no evidence of running if it succeeds.
          *
          * @param reason the reason that this parser is illegal.
          * @param labels the labels that should be expected if this parser hadn't succeeded.
          * @since 4.4.0
          * @note $autoAmend
          * @note $atomicNonTerminal
          */
        def preventativeExplain(reason: String, labels: String*): Parsley[Unit] = this.preventativeExplain(_ => reason, labels: _*)

        /** Ensures this parser does not succeed, failing with an error as described by the given `ErrorGen` object.
          *
          * If this parser succeeds, input is consumed and this combinator will fail, producing an error message using
          * the given `errGen` with width the same as the parsed data along with the given labels. However, if this parser
          * fails, no input is consumed and this combinator succeeds. This parser will produce no evidence of running if it succeeds.
          *
          * @param err the generator that produces the error message.
          * @param labels the labels that should be expected if this parser hadn't succeeded.
          * @since 4.4.0
          * @note $autoAmend
          * @note $atomicNonTerminal
          */
        def preventWith(err: ErrorGen[A], labels: String*): Parsley[Unit] = {
            val inner: Parsley[Either[(A, Int), Unit]] = withWidth(atomic(con(p)).hide) <+> unit
            val labelledErr = labels match {
                case l1 +: ls       => err.parser.label(l1, ls: _*)
                case _              => err.parser
            }
            amend(select(inner, labelledErr))
        }

        @inline private def preventWithVanilla(unexGen: A => VanillaGen.UnexpectedItem, reasonGen: A => Option[String], labels: String*) = {
            this.preventWith(new VanillaGen[A] {
                override def unexpected(x: A) = unexGen(x)
                override def reason(x: A) = reasonGen(x)
            }, labels: _*)
        }

        @inline private def preventWithVanillaRaw(reasonGen: A => Option[String], labels: String*) = {
            this.preventWithVanilla(_ => VanillaGen.RawItem, reasonGen, labels: _*)
        }
    }
}
