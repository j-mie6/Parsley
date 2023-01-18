/* SPDX-FileCopyrightText: © 2022 Parsley Contributors <https://github.com/j-mie6/Parsley/graphs/contributors>
 * SPDX-License-Identifier: BSD-3-Clause
 */
package parsley.token.text

import parsley.Parsley, Parsley.empty
import parsley.character.{char, satisfy, satisfyUtf16}
import parsley.combinator.skipSome
import parsley.implicits.character.charLift
import parsley.token.descriptions.text.EscapeDesc
import parsley.token.errors.ErrorConfig
import parsley.token.predicate.{Basic, CharPredicate, NotRequired, Unicode}

private [token] abstract class StringCharacter {
    def apply(isLetter: CharPredicate): Parsley[Option[Int]]
    def isRaw: Boolean

    protected def _checkBadChar(err: ErrorConfig) = err.verifiedStringBadCharsUsedInLiteral.checkBadChar
}

private [token] class RawCharacter(err: ErrorConfig) extends StringCharacter {
    override def isRaw: Boolean = true
    override def apply(isLetter: CharPredicate): Parsley[Option[Int]] = isLetter match {
        case Basic(isLetter) => err.labelStringCharacter(satisfy(isLetter).map(c => Some(c.toInt))) <|> _checkBadChar(err)
        case Unicode(isLetter) => err.labelStringCharacter(satisfyUtf16(isLetter).map(Some(_))) <|> _checkBadChar(err)
        case NotRequired => empty
    }
}

private [token] class EscapableCharacter(desc: EscapeDesc, escapes: Escape, space: Parsley[_], err: ErrorConfig) extends StringCharacter {
    override def isRaw: Boolean = false
    private lazy val escapeEmpty = err.labelStringEscapeEmpty(desc.emptyEscape.fold[Parsley[Char]](empty)(char))
    private lazy val escapeGap = {
        if (desc.gapsSupported) skipSome(err.labelStringEscapeGap(space)) *> err.labelStringEscapeGapEnd(desc.escBegin)
        else empty
    }
    private lazy val stringEscape: Parsley[Option[Int]] =
        escapes.escapeBegin *> (escapeGap #> None
                            <|> escapeEmpty #> None
                            <|> escapes.escapeCode.map(Some(_)))

    override def apply(isLetter: CharPredicate): Parsley[Option[Int]] = {
        isLetter match {
            case Basic(isLetter) => err.labelStringCharacter(
                stringEscape <|> err.labelGraphicCharacter(satisfy(c => isLetter(c) && c != desc.escBegin).map(c => Some(c.toInt)))
                             <|> _checkBadChar(err)
            )
            case Unicode(isLetter) => err.labelStringCharacter(
                stringEscape <|> err.labelGraphicCharacter(satisfyUtf16(c => isLetter(c) && c != desc.escBegin.toInt).map(Some(_)))
                             <|> _checkBadChar(err)
            )
            case NotRequired => stringEscape
        }
    }
}
