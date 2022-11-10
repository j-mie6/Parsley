/* SPDX-FileCopyrightText: © 2022 Parsley Contributors <https://github.com/j-mie6/Parsley/graphs/contributors>
 * SPDX-License-Identifier: BSD-3-Clause
 */
package parsley.token.text

import parsley.Parsley
import parsley.character.satisfy
import parsley.token.predicate.{Basic, CharPredicate, NotRequired, Unicode}

/** This class defines a uniform interface for defining parsers for character
  * literals, independent of how whitespace should be handled after the literal.
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
abstract class Character private[token] {
    /** This parser will parse a single character literal, which may contain
      * any unicode graphic character as defined by up to two UTF-16 codepoints.
      * It may also contain escape sequences.
      *
      * @example {{{
      * scala> unicode.parse("'a'")
      * val res0 = Success(97)
      * scala> unicode.parse("'£'")
      * val res1 = Success(163)
      * scala> unicode.parse("'λ'")
      * val res2 = Success(0x03BB)
      * scala> unicode.parse("'🙂'")
      * val res3 = Success(0x1F642)
      * }}}
      *
      * @since 4.0.0
      * @note $disclaimer
      */
    def unicode: Parsley[Int]
    /** This parser will parse a single character literal, which may contain
      * any graphic character that falls within the "Basic Multilingual Plane" (BMP).
      * This is defined as any UTF-16 character that fits into 16 bits. A Scala `Char`
      * is exactly large enough to hold any BMP character. It may also contain escape sequences,
      * but only those which result in BMP characters.
      *
      * @example {{{
      * scala> basicMultilingualPlane.parse("'a'")
      * val res0 = Success('a')
      * scala> basicMultilingualPlane.parse("'£'")
      * val res1 = Success('£')
      * scala> basicMultilingualPlane.parse("'λ'")
      * val res2 = Success('λ')
      * scala> basicMultilingualPlane.parse("'🙂'")
      * val res3 = Failure(...) // 🙂 has a 32-bit codepoint of larger than 0xffff
      * }}}
      *
      * @since 4.0.0
      * @note $disclaimer
      */
    def basicMultilingualPlane: Parsley[Char]
    /** This parser will parse a single character literal, which may contain
      * any graphic ASCII character. These are characters with ordinals in range
      * 0 to 127 inclusive. It may also contain escape sequences, but only
      * those which result in ASCII characters.
      *
      * @example {{{
      * scala> ascii.parse("'a'")
      * val res0 = Success('a')
      * scala> ascii.parse("'£'")
      * val res1 = Failure(...) // £'s ordinal is not less than 127
      * scala> ascii.parse("'λ'")
      * val res2 = Failure(...) // λ's ordinal is not less than 127
      * scala> ascii.parse("'🙂'")
      * val res3 = Failure(...) // 🙂's ordinal is not less than 127
      * }}}
      *
      * @since 4.0.0
      * @note $disclaimer
      */
    def ascii: Parsley[Char]
    /** This parser will parse a single character literal, which may contain
      * any graphic extended ASCII character. These are characters with ordinals in range
      * 0 to 255 inclusive. It may also contain escape sequences, but only
      * those which result in extended ASCII characters.
      *
      * @example {{{
      * scala> extendedAscii.parse("'a'")
      * val res0 = Success('a')
      * scala> extendedAscii.parse("'£'")
      * val res1 = Success('£')
      * scala> extendedAscii.parse("'λ'")
      * val res2 = Failure(...) // λ's ordinal is not less than 255
      * scala> extendedAscii.parse("'🙂'")
      * val res3 = Failure(...) // 🙂's ordinal is not less than 255
      * }}}
      *
      * @since 4.0.0
      * @note $disclaimer
      */
    def extendedAscii: Parsley[Char]
}

private [text] object Character {
    final val MaxAscii: Int = 0x7f
    final val MaxExtendedAscii: Int = 0xff

    def letter(terminalLead: Char, allowsAllSpace: Boolean, isGraphic: CharPredicate): CharPredicate = isGraphic match {
        case Unicode(g) if allowsAllSpace => Unicode(c => c != terminalLead.toInt && (g(c) || parsley.character.isWhitespace(c.toChar)))
        case Unicode(g)                   => Unicode(c => c != terminalLead.toInt && g(c))
        case Basic(g) if allowsAllSpace   => Basic(c => c != terminalLead && (g(c) || parsley.character.isWhitespace(c)))
        case Basic(g)                     => Basic(c => c != terminalLead && g(c))
        case NotRequired                  => NotRequired
    }

    def letter(terminalLead: Char, escapeLead: Char, allowsAllSpace: Boolean, isGraphic: CharPredicate): CharPredicate = {
        letter(terminalLead, allowsAllSpace, isGraphic) match {
            case Unicode(g)  => Unicode(c => c != escapeLead.toInt && g(c))
            case Basic(g)    => Basic(c => c != escapeLead && g(c))
            case NotRequired => NotRequired
        }
    }

    @inline def isBmpCodePoint(codepoint: Int): Boolean = java.lang.Character.isBmpCodePoint(codepoint)
    @inline def isValidCodePoint(codepoint: Int): Boolean = java.lang.Character.isValidCodePoint(codepoint)
}
