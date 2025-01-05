/*
 * Copyright 2020 Parsley Contributors <https://github.com/j-mie6/Parsley/graphs/contributors>
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package parsley.token

import Predef.{ArrowAssoc => _, _}

import parsley._
import parsley.Parsley.eof
import parsley.character.string

import token.{descriptions => desc}

class TokeniserTests extends ParsleyTest {
    val scala =
        desc.LexicalDesc(
            desc.NameDesc(identifierStart = Basic(('a' to 'z').toSet ++ ('A' to 'Z').toSet + '_'),
                          identifierLetter = Basic(('a' to 'z').toSet ++ ('A' to 'Z').toSet ++ ('0' to '9').toSet + '_'),
                          operatorStart = Basic(Set('+', '-', ':', '/', '*', '=')),
                          operatorLetter = Basic(Set('+', '-', '/', '*'))),
            desc.SymbolDesc(hardKeywords = Set("if", "else", "for", "yield", "while", "def", "class",
                                               "trait", "abstract", "override", "val", "var", "lazy"),
                            hardOperators = Set(":", "=", "::", ":="),
                            caseSensitive = true),
            desc.NumericDesc.plain,
            desc.TextDesc.plain,
            desc.SpaceDesc(multiLineCommentStart = "/*",
                           multiLineCommentEnd = "*/",
                           lineCommentStart = "//",
                           lineCommentAllowsEOF = true,
                           multiLineNestedComments = true,
                           space = Basic(_.isWhitespace),
                           whitespaceIsContextDependent = false))
    val scala_ =
        scala.copy(
            spaceDesc = scala.spaceDesc.copy(multiLineNestedComments = false)
        )
    val tokeniser = new token.Lexer(scala)
    val tokeniser_ = new token.Lexer(scala_)

    "semiSep" should "parse semi-colon separated values" in cases(tokeniser.lexeme.semiSep(string("aa"))) (
        "" -> Some(Nil),
        "aa" -> Some(List("aa")),
        "aa; aa;aa" -> Some(List("aa", "aa", "aa")),
        "aa;" -> None,
    )

    "semiSep1" should "parse semi-colon separated values" in cases(tokeniser.lexeme.semiSep1(string("aa"))) (
        "" -> None,
        "aa" -> Some(List("aa")),
        "aa; aa;aa" -> Some(List("aa", "aa", "aa")),
        "aa;" -> None,
    )

    "commaSep" should "parse comma separated values" in cases(tokeniser.lexeme.commaSep(string("aa"))) (
        "" -> Some(Nil),
        "aa" -> Some(List("aa")),
        "aa, aa,aa" -> Some(List("aa", "aa", "aa")),
        "aa," -> None,
    )

    "commaSep1" should "parse comma separated values" in cases(tokeniser.lexeme.commaSep1(string("aa"))) (
        "" -> None,
        "aa" -> Some(List("aa")),
        "aa, aa,aa" -> Some(List("aa", "aa", "aa")),
        "aa," -> None,
    )

    "parens" should "parse values within parentheses" in cases(tokeniser.lexeme.parens(string("aa"))) (
        "" -> None,
        "( aa)" -> Some("aa"),
        "(aa)  " -> Some("aa"),
        "(aa" -> None,
        "aa)" -> None,
        "((aa)" -> None,
        "{aa}" -> None,
    )

    "braces" should "parse values within braces" in cases(tokeniser.lexeme.braces(string("aa"))) (
        "" -> None,
        "{ aa}" -> Some("aa"),
        "{aa}  " -> Some("aa"),
        "{aa" -> None,
        "aa}" -> None,
        "{{aa}" -> None,
        "(aa)" -> None,
    )

    "angles" should "parse values within angle brackets" in cases(tokeniser.lexeme.angles(string("aa"))) (
        "" -> None,
        "< aa>" -> Some("aa"),
        "<aa>  " -> Some("aa"),
        "<aa" -> None,
        "aa>" -> None,
        "<<aa>" -> None,
        "(aa)" -> None,
    )

    "brackets" should "parse values within square brackets" in cases(tokeniser.lexeme.brackets(string("aa"))) (
        "" -> None,
        "[ aa]" -> Some("aa"),
        "[aa]  " -> Some("aa"),
        "[aa" -> None,
        "aa]" -> None,
        "[[aa]" -> None,
        "(aa)" -> None,
    )

    "naturalOrFloat" should "parse either naturals or unsigned floats" in {
        tokeniser.lexeme.unsignedCombined.number.parse("3.142  /*what a sick number am I right*/") should be (Success(Right(3.142)))
        tokeniser.lexeme.unsignedCombined.number.parse("0.23") should be (Success(Right(0.23)))
        tokeniser.lexeme.unsignedCombined.number.parse("10.0\n") should be (Success(Right(10.0)))
        tokeniser.lexeme.unsignedCombined.number.parse("3e10") should be (Success(Right(3e10)))
        tokeniser.lexeme.unsignedCombined.number.parse("5E-4") should be (Success(Right(5e-4)))
        tokeniser.lexeme.unsignedCombined.number.parse("3.142e2\t ") should be (Success(Right(3.142e2)))
        tokeniser.lexeme.unsignedCombined.number.parse("0.23e1") should be (Success(Right(0.23e1)))
        tokeniser.lexeme.unsignedCombined.number.parse("10.0e-5") should be (Success(Right(10.0e-5)))
        tokeniser.lexeme.unsignedCombined.number.parse("1024") should be (Success(Left(1024)))
        tokeniser.lexeme.unsignedCombined.number.parse("0x340") should be (Success(Left(0x340)))
        tokeniser.lexeme.unsignedCombined.number.parse("0xFF") should be (Success(Left(0xFF)))
        tokeniser.lexeme.unsignedCombined.number.parse("0o201 //ooh, octal") should be (Success(Left(129)))
    }
    // Now they do :)
    it should "not allow hexadecimal floats without the exponent" in {
        (tokeniser.lexeme.unsignedCombined.number <* eof).parse("0x340.0") shouldBe a [Failure[_]]
    }
    it should "not allow octal floats without the exponent" in {
        (tokeniser.lexeme.unsignedCombined.number <* eof).parse("0o201.0") shouldBe a [Failure[_]]
    }

    "number" should "parse integers or floats" in {
        tokeniser.lexeme.signedCombined.number.parse("3.142  /*what a sick number am I right*/") should be (Success(Right(3.142)))
        tokeniser.lexeme.signedCombined.number.parse("-0.23") should be (Success(Right(-0.23)))
        tokeniser.lexeme.signedCombined.number.parse("10.0\n") should be (Success(Right(10.0)))
        tokeniser.lexeme.signedCombined.number.parse("+3e10") should be (Success(Right(3e10)))
        tokeniser.lexeme.signedCombined.number.parse("5E-4") should be (Success(Right(5e-4)))
        tokeniser.lexeme.signedCombined.number.parse("3.142e2\t ") should be (Success(Right(3.142e2)))
        tokeniser.lexeme.signedCombined.number.parse("+0.23e1") should be (Success(Right(0.23e1)))
        tokeniser.lexeme.signedCombined.number.parse("10.0e-5") should be (Success(Right(10.0e-5)))
        tokeniser.lexeme.signedCombined.number.parse("-1024") should be (Success(Left(-1024)))
        tokeniser.lexeme.signedCombined.number.parse("0x340") should be (Success(Left(0x340)))
        tokeniser.lexeme.signedCombined.number.parse("0xFF") should be (Success(Left(0xFF)))
        tokeniser.lexeme.signedCombined.number.parse("0o201 //ooh, octal") should be (Success(Left(129)))
    }
}
