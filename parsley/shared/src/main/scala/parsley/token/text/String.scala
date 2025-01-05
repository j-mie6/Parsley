/*
 * Copyright 2020 Parsley Contributors <https://github.com/j-mie6/Parsley/graphs/contributors>
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package parsley.token.text

import parsley.Parsley
import parsley.token.errors.ErrorConfig

/** This class defines a uniform interface for defining parsers for string
  * literals, independent of whether the string is raw, multi-line, or should
  * consume whitespace after the literal.
  *
  * @since 4.0.0
  * @note implementations of this class found within `Lexer` may employ sharing
  *       and refine the `def`s in this class into `val` or `lazy val` when overriding.
  *
  * @define disclaimer
  *   the exact behaviour of this parser is decided by the implementations given in
  *   `Lexer`, which will depend on user-defined configuration. Please see the
  *   relevant documentation of these specific objects.
  */
abstract class StringParsers private[text] {
    /** This parser will parse a single string literal, which may contain any
      * number of graphical UTF-16 unicode characters; including those that span multiple
      * 32-bit codepoints. It may contain escape sequences, and potentially
      * support string gaps and zero-width characters depending on the
      * configuration.
      *
      * @example {{{
      * scala> fullUtf16.parse("\"μαϊντανός!\"")
      * val res0 = Success("μαϊντανός!")
      * scala> fullUtf16.parse("\"hello world\"")
      * val res1 = Success("hello world")
      * scala> fullUtf16.parse("\"🙂\"")
      * val res2 = Success("🙂")
      * scala> fullUtf16.parse("\"£10\"")
      * val res3 = Success("£10")
      * }}}
      *
      * @since 4.0.0
      * @note $disclaimer
      */
    def fullUtf16: Parsley[String]
    /** This parser will parse a single string literal, which may contain any
      * number of graphic extended ascii characters (known as latin1). It may contain escape
      * sequences, and potentially support string gaps and zero-width characters
      * depending on the configuration.
      *
      * @example {{{
      * scala> latin1.parse("\"μαϊντανός!\"")
      * val res0 = Failure(...) // Greek is not part of latin1
      * scala> latin1.parse("\"hello world\"")
      * val res1 = Success("hello world")
      * scala> latin1.parse("\"🙂\"")
      * val res2 = Failure(...) // Emoji are not part of latin1
      * scala> latin1.parse("\"£10\"")
      * val res3 = Success("£10")
      * }}}
      *
      * @since 4.0.0
      * @note $disclaimer
      */
    def latin1: Parsley[String]
    /** This parser will parse a single string literal, which may contain any
      * number of graphic ascii characters. It may contain escape
      * sequences, and potentially support string gaps and zero-width characters
      * depending on the configuration.
      *
      * @example {{{
      * scala> ascii.parse("\"μαϊντανός!\"")
      * val res0 = Failure(...) // Greek is not part of ascii
      * scala> ascii.parse("\"hello world\"")
      * val res1 = Success("hello world")
      * scala> ascii.parse("\"🙂\"")
      * val res2 = Failure(...) // Emoji are not part of ascii
      * scala> ascii.parse("\"£10\"")
      * val res3 = Failure(...) // £ is not part of ascii
      * }}}
      *
      * @since 4.0.0
      * @note $disclaimer
      */
    def ascii: Parsley[String]
}

private [text] object StringParsers {
    // don't need to use code points, high-surrogates are already out of range
    private def allCharsWithin(str: StringBuilder, bound: Int) = str.forall(_ <= bound)
    private def isAscii(str: StringBuilder): Boolean = allCharsWithin(str, CharacterParsers.MaxAscii)
    private def isExtendedAscii(str: StringBuilder): Boolean = allCharsWithin(str, CharacterParsers.MaxLatin1)

    def ensureAscii(err: ErrorConfig)(p: Parsley[StringBuilder]): Parsley[StringBuilder] = err.filterStringNonAscii.filter(p)(isAscii(_))
    def ensureExtendedAscii(err: ErrorConfig)(p: Parsley[StringBuilder]): Parsley[StringBuilder] = err.filterStringNonLatin1.filter(p)(isExtendedAscii(_))
}
