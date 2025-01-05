/*
 * Copyright 2020 Parsley Contributors <https://github.com/j-mie6/Parsley/graphs/contributors>
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package parsley.token.names

import parsley.Parsley, Parsley.{atomic, empty, pure}
import parsley.character.{satisfy, stringOfMany}
import parsley.errors.combinator.ErrorMethods
import parsley.syntax.zipped._
import parsley.token.descriptions.{NameDesc, SymbolDesc}
import parsley.token.errors.ErrorConfig
import parsley.token.{Basic, CharPred, NotRequired, Unicode}
import parsley.unicode.{satisfy => satisfyUtf16, stringOfMany => stringOfManyUtf16}

// $COVERAGE-OFF$
private [token] class OriginalNames(nameDesc: NameDesc, symbolDesc: SymbolDesc, err: ErrorConfig) extends Names {
    private def keyOrOp(startImpl: CharPred, letterImpl: CharPred, illegal: String => Boolean,
                        name: String, unexpectedIllegal: String => String) = {
        atomic {
            complete(startImpl, letterImpl).unexpectedWhen {
                case x if illegal(x) => unexpectedIllegal(x)
            }
        }.label(name)
    }
    private def trailer(impl: CharPred) = impl match {
        case Basic(letter) => stringOfMany(letter)
        case Unicode(letter) => stringOfManyUtf16(letter)
        case NotRequired => pure("")
    }
    private def complete(start: CharPred, letter: CharPred) = start match {
        case Basic(start) => (satisfy(start), trailer(letter)).zipped((c, cs) => s"$c$cs")
        case Unicode(start) => (satisfyUtf16(start), trailer(letter)).zipped { (c, cs) =>
            if (Character.isSupplementaryCodePoint(c)) s"${Character.highSurrogate(c)}${Character.lowSurrogate(c)}$cs"
            else s"${c.toChar}$cs"
        }
        case NotRequired => empty
    }
    override lazy val identifier: Parsley[String] =
        keyOrOp(nameDesc.identifierStart, nameDesc.identifierLetter, symbolDesc.isReservedName,
                err.labelNameIdentifier, err.unexpectedNameIllegalIdentifier)
    override def identifier(startChar: CharPred): Parsley[String] = atomic {
        err.filterNameIllFormedIdentifier.filter(identifier)(startChar.startsWith)
    }

    override lazy val userDefinedOperator: Parsley[String] =
        keyOrOp(nameDesc.operatorStart, nameDesc.operatorLetter, symbolDesc.isReservedOp, err.labelNameOperator, err.unexpectedNameIllegalOperator)

    def userDefinedOperator(startChar: CharPred, endChar: CharPred): Parsley[String] = atomic {
        err.filterNameIllFormedOperator.filter(userDefinedOperator)(x => startChar.startsWith(x) && endChar.endsWith(x))
    }
}
// $COVERAGE-ON$
