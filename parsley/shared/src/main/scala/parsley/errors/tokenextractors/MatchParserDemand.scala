/*
 * Copyright 2020 Parsley Contributors <https://github.com/j-mie6/Parsley/graphs/contributors>
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package parsley.errors.tokenextractors

import scala.collection.immutable.WrappedString

import parsley.errors.{helpers, ErrorBuilder, Token}

import org.typelevel.scalaccompat.annotation.unused

/** This extractor mixin provides an implementation for
  * [[parsley.errors.ErrorBuilder.unexpectedToken `ErrorBuilder.unexpectedToken`]] when mixed into
  * an error builder: it will make a token as wide as the amount of input the parser tried to
  * consume when it failed.
  * @since 4.0.0
  * @note In the case of unprintable characters or whitespace, this extractor will favour reporting
  *       a more meaningful name.
  */
trait MatchParserDemand { this: ErrorBuilder[_] =>
    /** @see [[parsley.errors.ErrorBuilder.unexpectedToken `ErrorBuilder.unexpectedToken`]] */
    override final def unexpectedToken(cs: Iterable[Char], amountOfInputParserWanted: Int, @unused lexicalError: Boolean): Token = {
        MatchParserDemand.unexpectedToken(cs, amountOfInputParserWanted)
    }
}

/** Contains the functionality of `MatchParserDemand` as a function.
  * @since 4.0.0
  */
object MatchParserDemand {
    /** The implementation of `unexpectedToken` as done by `MatchParserDemand`, with redundant arguments removed.
      * @since 4.0.0
      */
    def unexpectedToken(cs: Iterable[Char], amountOfInputParserWanted: Int): Token = cs match {
        case helpers.WhitespaceOrUnprintable(name) => Token.Named(name, 1)
        case _ => Token.Raw(substring(cs, amountOfInputParserWanted))
    }

    // the default case will build a new string, if the underlying was already a string
    // this is redundant.
    private def substring(cs: Iterable[Char], upto: Int): String = cs match {
        case cs: WrappedString => helpers.takeCodePoints(cs, upto)
        case _ => helpers.takeCodePoints(cs, upto)
    }
}
