/*
 * Copyright 2020 Parsley Contributors <https://github.com/j-mie6/Parsley/graphs/contributors>
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package parsley.token.text

import parsley.Parsley, Parsley.{atomic, fresh, pure}
import parsley.character.{char, string}
import parsley.combinator.{choice, skipManyUntil}
import parsley.errors.combinator.ErrorMethods
import parsley.syntax.zipped._
import parsley.token.errors.{ErrorConfig, LabelConfig, LabelWithExplainConfig}
import parsley.token.CharPred

private [token] final class ConcreteString(ends: Set[(String, String)], stringChar: StringCharacter, isGraphic: CharPred,
                                           allowsAllSpace: Boolean, err: ErrorConfig) extends StringParsers {

    private def stringLiteral(valid: Parsley[StringBuilder] => Parsley[StringBuilder],
                              openLabel: (Boolean, Boolean) => LabelWithExplainConfig, closeLabel: (Boolean, Boolean) => LabelConfig) = {
        choice(ends.view.map(makeStringParser(valid, openLabel, closeLabel)).toSeq: _*) *> sbReg.gets(_.toString)
    }
    override lazy val fullUtf16: Parsley[String] = stringLiteral(identity, err.labelStringUtf16, err.labelStringUtf16End)
    override lazy val ascii: Parsley[String] = stringLiteral(StringParsers.ensureAscii(err), err.labelStringAscii, err.labelStringAsciiEnd)
    override lazy val latin1: Parsley[String] = stringLiteral(StringParsers.ensureExtendedAscii(err), err.labelStringLatin1, err.labelStringLatin1End)

    private val sbReg = parsley.state.Ref.make[StringBuilder]

    private def makeStringParser(valid: Parsley[StringBuilder] => Parsley[StringBuilder],
                                 openLabel: (Boolean, Boolean) => LabelWithExplainConfig, closeLabel: (Boolean, Boolean) => LabelConfig)
                                (terminalStr: (String, String)) = {
        val (begin, end) = terminalStr
        val terminalInit = end.charAt(0)
        val strChar = stringChar(CharacterParsers.letter(terminalInit, allowsAllSpace, isGraphic))
        val pf = (sb: StringBuilder, cpo: Option[Int]) => {
            for (cp <- cpo) parsley.unicode.addCodepoint(sb, cp)
            sb
        }
        // `content` is in a dropped position, so needs the unsafe to avoid the mutation
        // TODO: this could be fixed better with registers and skipMany?
        val content = valid(parsley.expr.infix.secretLeft1((sbReg.get, strChar).zipped(pf), strChar, pure(pf), null).impure)
        // open should be first, to allow for a jump table on the main choice
        openLabel(allowsAllSpace, stringChar.isRaw)(string(begin)) *>
        // then only one string builder needs allocation
        sbReg.set(fresh(new StringBuilder)) *>
        skipManyUntil(sbReg.update(char(terminalInit).hide.as((sb: StringBuilder) => sb += terminalInit)) <|> content,
                      closeLabel(allowsAllSpace, stringChar.isRaw)(atomic(string(end)))) // atomic needed because ambiguity with init
    }
}
