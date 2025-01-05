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
  * an error builder: it will construct a token that extends to the next available whitespace
  * in the remaining input. It can be configured to constrict this token to the minimum of the
  * next whitespace or whatever the parser demanded (see [[MatchParserDemand `MatchParserDemand`]]).
  * @since 4.0.0
  * @note In the case of unprintable characters or whitespace, this extractor will favour reporting
  *       a more meaningful name.
  */
trait TillNextWhitespace { this: ErrorBuilder[_] =>
    /** Should tokens be trimed to only be as wide as ''either'' the next whitespace or the
      * amount of input the parser tried to consumed, whichever is smaller?
      * @since 4.0.0
      */
    def trimToParserDemand: Boolean

    /** Describes what characters are considered whitespace.
      *
      * Defaults to `_.isWhitespace`.
      *
      * @since 4.4.0
      */
    def isWhitespace(c: Char): Boolean = c.isWhitespace

    /** @see [[parsley.errors.ErrorBuilder.unexpectedToken `ErrorBuilder.unexpectedToken`]] */
    override final def unexpectedToken(cs: Iterable[Char], amountOfInputParserWanted: Int, @unused lexicalError: Boolean): Token = {
        if (trimToParserDemand) TillNextWhitespace.unexpectedToken(cs, amountOfInputParserWanted, isWhitespace(_))
        else TillNextWhitespace.unexpectedToken(cs, isWhitespace(_))
    }
}

/** Contains the functionality of `TillNextWhitespace` as a function.
  * @since 4.0.0
  */
object TillNextWhitespace {
    // TODO: better factoring of this code
    /** The implementation of `unexpectedToken` as done by `TillNextWhitespace`, with redundant arguments removed.
      *
      * This function will not trim the token to parser demand
      *
      * @since 4.0.0
      */
    def unexpectedToken(cs: Iterable[Char]): Token = unexpectedToken(cs, _.isWhitespace)

    /** The implementation of `unexpectedToken` as done by `TillNextWhitespace`, with redundant arguments removed.
      *
      * This function will not trim the token to parser demand
      *
      * @since 4.4.0
      */
    def unexpectedToken(cs: Iterable[Char], isWhitespace: Char => Boolean): Token = cs match {
        case helpers.WhitespaceOrUnprintable(name) => Token.Named(name, 1)
        // these cases automatically handle the utf-16 surrogate pairs
        case cs => Token.Raw(extractTillNextWhitespace(cs, isWhitespace))
    }

    /** The implementation of `unexpectedToken` as done by `TillNextWhitespace`, with redundant arguments removed.
      *
      * This function will not trim the token to parser demand
      *
      * @since 4.0.0
      */
    def unexpectedToken(cs: Iterable[Char], amountOfInputParserWanted: Int): Token = unexpectedToken(cs, amountOfInputParserWanted, _.isWhitespace)

    /** The implementation of `unexpectedToken` as done by `TillNextWhitespace`, with redundant arguments removed.
      *
      * This function will not trim the token to parser demand
      *
      * @since 4.4.0
      */
    def unexpectedToken(cs: Iterable[Char], amountOfInputParserWanted: Int, isWhitespace: Char => Boolean): Token = cs match {
        case helpers.WhitespaceOrUnprintable(name) => Token.Named(name, 1)
        // these cases automatically handle the utf-16 surrogate pairs
        case cs => Token.Raw(helpers.takeCodePoints(extractTillNextWhitespace(cs, isWhitespace), amountOfInputParserWanted))
    }

    // TODO: we should take to minimum of parser demand and next whitespace, this would potentially be much much cheaper
    // Assumption: there are no non-BMP whitespace characters
    private def extractTillNextWhitespace(cs: Iterable[Char], isWhitespace: Char => Boolean): String = cs match {
        case cs: WrappedString =>
            // These do not require allocation on the string
            val idx = {
                val idx = cs.indexWhere(isWhitespace)
                if (idx != -1) idx else cs.length
            }
            cs.slice(0, idx).toString
        case cs => cs.takeWhile(!isWhitespace(_)).mkString
    }
}
