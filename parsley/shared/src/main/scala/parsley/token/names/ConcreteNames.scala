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

import parsley.internal.deepembedding.singletons

/** This class' implementation has been optimised for performance. If you've stumbled on
  * this file hoping to learn about how it works, this is the wrong place. The original,
  * unoptimised implementation is preserved for testing in the corresponding place in the
  * "test" folder. This is because a requirement of optimisation is that the semantics can
  * still be implemented by plain combinators. Go look there!
  */
private [token] class ConcreteNames(nameDesc: NameDesc, symbolDesc: SymbolDesc, err: ErrorConfig) extends Names {
    private def keyOrOp(debugName: String, startImpl: CharPred, letterImpl: CharPred, illegal: String => Boolean,
                        name: String, unexpectedIllegal: String => String) = {
        (startImpl, letterImpl) match {
            case (Basic(start), Basic(letter)) => new Parsley(new singletons.NonSpecific(name, unexpectedIllegal, debugName, start, letter, illegal))
            case _ => atomic {
                complete(startImpl, letterImpl).unexpectedWhen {
                    case x if illegal(x) => unexpectedIllegal(x)
                }
            }.label(name)
        }
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
        keyOrOp("identifier", nameDesc.identifierStart, nameDesc.identifierLetter, symbolDesc.isReservedName,
                err.labelNameIdentifier, err.unexpectedNameIllegalIdentifier)
    override def identifier(startChar: CharPred): Parsley[String] = atomic {
        err.filterNameIllFormedIdentifier.filter(identifier)(startChar.startsWith)
    }

    override lazy val userDefinedOperator: Parsley[String] =
        keyOrOp("userDefinedOperator", nameDesc.operatorStart, nameDesc.operatorLetter, symbolDesc.isReservedOp,
                err.labelNameOperator, err.unexpectedNameIllegalOperator)

    def userDefinedOperator(startChar: CharPred, endChar: CharPred): Parsley[String] = atomic {
        err.filterNameIllFormedOperator.filter(userDefinedOperator)(x => startChar.startsWith(x) && endChar.endsWith(x))
    }
}
