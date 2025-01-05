/*
 * Copyright 2020 Parsley Contributors <https://github.com/j-mie6/Parsley/graphs/contributors>
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package parsley.token.names

import Predef.{ArrowAssoc => _, _}

import parsley.{ParsleyTest, Failure}
import parsley.token.LexemeImpl._
import parsley.token.errors.ErrorConfig

import parsley.token.descriptions._
import parsley.character.spaces
import parsley.{TestError, VanillaError, Named}
import org.scalactic.source.Position
import parsley.token.{Unicode, NotRequired, CharPred, Basic}

class NamesTests extends ParsleyTest {
    val errConfig = new ErrorConfig
    def makeSymbol(nameDesc: NameDesc, symDesc: SymbolDesc): Names = new LexemeNames(new ConcreteNames(nameDesc, symDesc, errConfig), spaces)

    val plainName = NameDesc.plain.copy(identifierLetter = Basic(_.isLetterOrDigit), identifierStart = Basic(_.isLetter))
    val plainSym = SymbolDesc.plain.copy(hardKeywords = Set("keyword", "HARD"), hardOperators = Set("+", "<", "<="))

    val plainNames = makeSymbol(plainName, plainSym)

    def identCases(start: CharPred, letter: CharPred, sensitive: Boolean = true)(tests: (String, Option[String], Position)*): Unit = {
        cases(makeSymbol(plainName.copy(identifierStart = start, identifierLetter = letter), plainSym.copy(caseSensitive = sensitive)).identifier)(tests: _*)
    }

    def opCases(start: CharPred, letter: CharPred)(tests: (String, Option[String], Position)*): Unit = {
        cases(makeSymbol(plainName.copy(operatorStart = start, operatorLetter = letter), plainSym).userDefinedOperator)(tests: _*)
    }

    def identCases(start: CharPred, letter: CharPred, refStart: CharPred)(tests: (String, Option[String], Position)*): Unit = {
        cases(makeSymbol(plainName.copy(identifierStart = start, identifierLetter = letter), plainSym).identifier(refStart))(tests: _*)
    }

    def opCases(start: CharPred, letter: CharPred, refStart: CharPred, refEnd: CharPred)(tests: (String, Option[String], Position)*): Unit = {
        cases(makeSymbol(plainName.copy(operatorStart = start, operatorLetter = letter), plainSym).userDefinedOperator(refStart, refEnd))(tests: _*)
    }

    "identifier" should "parse valid identifiers" in {
        identCases(Basic(_.isLetter), Basic(_.isLetterOrDigit))(
            "hello1" -> Some("hello1"),
            "7f" -> None,
            "hi" -> Some("hi"),
            "x7" -> Some("x7"),
        )
        identCases(Unicode(Character.isAlphabetic(_)), Unicode(Character.isLetterOrDigit(_)))(
            "hello1" -> Some("hello1"),
            "7f" -> None,
            "hi" -> Some("hi"),
            "x7" -> Some("x7"),
        )
        identCases(Basic(_.isLetter), Unicode(Character.isDigit(_)))(
            "hello1" -> None,
            "7f" -> None,
            "7" -> None,
            "x73" -> Some("x73"),
        )
        identCases(Unicode(Set(0x1F642)), Basic(_.isLetterOrDigit))(
            "🙂ello1" -> Some("🙂ello1"),
            "df" -> None,
            "🙂i" -> Some("🙂i"),
            "🙂7" -> Some("🙂7"),
        )
        identCases(Unicode(Character.isAlphabetic(_)), NotRequired)(
            "x" -> Some("x"),
            "y" -> Some("y"),
            "hi" -> None,
            "x7" -> None,
        )
    }

    it should "fail to parse valid keywords" in {
        identCases(Basic(_.isLetter), Basic(_.isLetterOrDigit))(
            "keyword" -> None,
            "keyword1" -> Some("keyword1"),
        )
    }

    it should "point at the correct place for the error" in {
        plainNames.identifier.parseAll("keyword") should matchPattern {
            case Failure(TestError((1, 1), _)) =>
        }
    }

    it should "report the correct label" in {
        inside(plainNames.identifier.parseAll("HARD")) {
            case Failure(TestError(_, VanillaError(unexpected, expecteds, reasons, 4))) =>
                unexpected should contain (Named("keyword HARD"))
                expecteds should contain only (Named("identifier"))
                reasons shouldBe empty
        }
    }

    it should "work in the presence of case insensitivity with respect to keywords" in identCases(Basic(_.isLetter), Basic(_.isLetterOrDigit), false) (
        "HARD" -> None,
        "hard" -> None,
        "HArd" -> None,
        "harD" -> None,
        "keyword" -> None,
        "Keyword" -> None,
        "keyWORD" -> None,
        "KEYword" -> None,
    )

    "user defined operator" should "parse valid operators that are not keywords" in {
        opCases(Basic(Set('+', '-')), Basic(Set('=')))(
            "+=" -> Some("+="),
            "-" -> Some("-"),
            "-=" -> Some("-="),
            "+==" -> Some("+=="),
            "*" -> None,
        )
        opCases(Unicode(Set('+'.toInt, '-'.toInt)), Unicode(Set('='.toInt)))(
            "+=" -> Some("+="),
            "-" -> Some("-"),
            "-=" -> Some("-="),
            "+==" -> Some("+=="),
            "*" -> None,
        )
    }

    it should "fail to parse hard operators" in {
        opCases(Basic(Set('+', '-', '<')), Basic(Set('=')))(
            "+" -> None,
            "<" -> None,
            "<=" -> None,
        )
        opCases(Unicode(Set('+'.toInt, '-'.toInt, '<'.toInt)), Unicode(Set('='.toInt)))(
            "+" -> None,
            "<" -> None,
            "<=" -> None,
        )
    }

    "refined identifier" should "only allow identifiers with the given start characters" in identCases(Basic(_.isLetter), Basic(_.isLetterOrDigit), Basic(_.isUpper)) (
        "Hello" -> Some("Hello"),
        "hello" -> None,
        "X7" -> Some("X7"),
        "7" -> None,
        "Y" -> Some("Y"),
    )

    "refined operator" should "only allow operators with the given start characters" in opCases(Basic(Set('+', '-')), Basic(Set('=')), Basic(Set('+')), NotRequired) (
        "+=" -> Some("+="),
        "-" -> None,
        "-=" -> None,
        "+==" -> Some("+=="),
        "+" -> None,
    )

    it should "only allow operators with the given end characters" in opCases(Basic(Set('+', '-', '<')), Basic(Set('=', '\'')), NotRequired, Basic(Set('\''))) (
        "+'" -> Some("+'"),
        "-" -> None,
        "'" -> None,
        "<='" -> Some("<='"),
        "-''" -> Some("-''"),
    )

    it should "be able to constrain both ends" in opCases(Unicode(Set('+'.toInt, '-'.toInt, '<'.toInt)), Unicode(Set('='.toInt, '\''.toInt)), Unicode(Set('+'.toInt)), Unicode(Set('\''.toInt))) (
        "+'" -> Some("+'"),
        "-" -> None,
        "'" -> None,
        "<='" -> None,
        "+''" -> Some("+''"),
    )
}
