/*
 * Copyright 2020 Parsley Contributors <https://github.com/j-mie6/Parsley/graphs/contributors>
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package parsley.errors

import parsley.{ParsleyTest, TestErrorBuilder}
import parsley.Parsley.{many, some}
import parsley.character.{string, digit, letter, char, whitespace}
import parsley.errors.tokenextractors._

class TokenExtractorTests extends ParsleyTest {
    val singleChar = new TestErrorBuilder with SingleChar
    "SingleChar" should "return a single printable ascii character" in {
        singleChar.unexpectedToken("abc", 1, false) shouldBe Token.Raw("a")
        singleChar.unexpectedToken("1", 1, false) shouldBe Token.Raw("1")
        singleChar.unexpectedToken(";", 2, true) shouldBe Token.Raw(";")
    }
    it should "handle supplementary unicode characters" in {
        singleChar.unexpectedToken("😀", 1, true) shouldBe Token.Raw("😀")
        singleChar.unexpectedToken("😀😀😀", 1, false) shouldBe Token.Raw("😀")
    }
    it should "deal with whitespace characters by naming them" in {
        singleChar.unexpectedToken(" ", 1, true) shouldBe Token.Named("space", 1)
        singleChar.unexpectedToken("\n", 1, true) shouldBe Token.Named("newline", 1)
        singleChar.unexpectedToken("\t", 1, true) shouldBe Token.Named("tab", 1)
        singleChar.unexpectedToken("\r", 1, true) shouldBe Token.Named("carriage return", 1)
        singleChar.unexpectedToken("\f", 1, true) shouldBe Token.Named("whitespace character", 1)
    }
    it should "refuse to print control characters" in {
        singleChar.unexpectedToken("\u0000", 1, true) shouldBe Token.Named("non-printable character (\\u0000)", 1)
        singleChar.unexpectedToken("\u0001", 1, true) shouldBe Token.Named("non-printable character (\\u0001)", 1)
        singleChar.unexpectedToken("\ud83d", 1, true) shouldBe Token.Named("non-printable character (\\ud83d)", 1)
    }
    it should "refuse to print non-printable supplementary characters" in {
        singleChar.unexpectedToken(Character.toChars(0x0f0000), 1, true) shouldBe Token.Named("non-printable codepoint (\\udb80\\udc00, or 0x0f0000)", 1)
        singleChar.unexpectedToken(Character.toChars(0x10ffff), 1, true) shouldBe Token.Named("non-printable codepoint (\\udbff\\udfff, or 0x10ffff)", 1)
    }

    val matchParserDemand = new TestErrorBuilder with MatchParserDemand
    "MatchParserDemand" should "return a single printable ascii character" in {
        matchParserDemand.unexpectedToken("abc", 1, false) shouldBe Token.Raw("a")
        matchParserDemand.unexpectedToken("abc".toList, 2, false) shouldBe Token.Raw("ab")
        matchParserDemand.unexpectedToken("1", 4, false) shouldBe Token.Raw("1")
        matchParserDemand.unexpectedToken(";", 2, true) shouldBe Token.Raw(";")
        matchParserDemand.unexpectedToken(";.,", 3, true) shouldBe Token.Raw(";.,")
    }
    it should "handle supplementary unicode characters" in {
        matchParserDemand.unexpectedToken("😀", 2, true) shouldBe Token.Raw("😀")
        matchParserDemand.unexpectedToken("😀😀😀", 2, false) shouldBe Token.Raw("😀😀")
    }
    it should "deal with whitespace characters by naming them" in {
        matchParserDemand.unexpectedToken(" aa", 2, true) shouldBe Token.Named("space", 1)
        matchParserDemand.unexpectedToken("\naa", 2, true) shouldBe Token.Named("newline", 1)
        matchParserDemand.unexpectedToken("\taa".toList, 2, true) shouldBe Token.Named("tab", 1)
        matchParserDemand.unexpectedToken("\raa", 3, true) shouldBe Token.Named("carriage return", 1)
        matchParserDemand.unexpectedToken("\faa", 1, true) shouldBe Token.Named("whitespace character", 1)
    }
    it should "refuse to print control characters" in {
        matchParserDemand.unexpectedToken("\u0000aaa", 3, true) shouldBe Token.Named("non-printable character (\\u0000)", 1)
        matchParserDemand.unexpectedToken("\u0001aaa", 3, true) shouldBe Token.Named("non-printable character (\\u0001)", 1)
        matchParserDemand.unexpectedToken("\ud83daaa".toList, 2, true) shouldBe Token.Named("non-printable character (\\ud83d)", 1)
    }
    it should "refuse to print non-printable supplementary characters" in {
        matchParserDemand.unexpectedToken(Character.toChars(0x0f0000), 1, true) shouldBe Token.Named("non-printable codepoint (\\udb80\\udc00, or 0x0f0000)", 1)
        matchParserDemand.unexpectedToken(Character.toChars(0x10ffff), 1, true) shouldBe Token.Named("non-printable codepoint (\\udbff\\udfff, or 0x10ffff)", 1)
    }

    val tillNextWhitespaceTrimmed = new TestErrorBuilder with TillNextWhitespace {
        def trimToParserDemand: Boolean = true
    }
    val tillNextWhitespaceRaw = new TestErrorBuilder with TillNextWhitespace {
        def trimToParserDemand: Boolean = false
    }
    "TillNextWhitespace" should "return a single printable ascii character" in {
        tillNextWhitespaceTrimmed.unexpectedToken("abc", 1, false) shouldBe Token.Raw("a")
        tillNextWhitespaceTrimmed.unexpectedToken("abc ", 2, false) shouldBe Token.Raw("ab")
        tillNextWhitespaceTrimmed.unexpectedToken("a  bc".toList, 2, false) shouldBe Token.Raw("a")
        tillNextWhitespaceTrimmed.unexpectedToken("1\n23", 4, false) shouldBe Token.Raw("1")
        tillNextWhitespaceTrimmed.unexpectedToken("; ", 2, true) shouldBe Token.Raw(";")
        tillNextWhitespaceTrimmed.unexpectedToken(";. ,".toList, 3, true) shouldBe Token.Raw(";.")
        tillNextWhitespaceRaw.unexpectedToken("abc", 1, false) shouldBe Token.Raw("abc")
        tillNextWhitespaceRaw.unexpectedToken("abc ".toList, 2, false) shouldBe Token.Raw("abc")
        tillNextWhitespaceRaw.unexpectedToken("a  bc", 2, false) shouldBe Token.Raw("a")
        tillNextWhitespaceRaw.unexpectedToken("1\n23", 4, false) shouldBe Token.Raw("1")
        tillNextWhitespaceRaw.unexpectedToken("; ".toList, 2, true) shouldBe Token.Raw(";")
        tillNextWhitespaceRaw.unexpectedToken(";. ,", 3, true) shouldBe Token.Raw(";.")
    }
    it should "handle supplementary unicode characters" in {
        tillNextWhitespaceTrimmed.unexpectedToken("😀", 2, true) shouldBe Token.Raw("😀")
        tillNextWhitespaceTrimmed.unexpectedToken("😀😀😀 😀".toList, 2, false) shouldBe Token.Raw("😀😀")
        tillNextWhitespaceRaw.unexpectedToken("😀 😀", 2, true) shouldBe Token.Raw("😀")
        tillNextWhitespaceRaw.unexpectedToken("😀😀 😀", 1, false) shouldBe Token.Raw("😀😀")
    }
    it should "deal with whitespace characters by naming them" in {
        tillNextWhitespaceTrimmed.unexpectedToken(" aa", 2, true) shouldBe Token.Named("space", 1)
        tillNextWhitespaceTrimmed.unexpectedToken("\naa", 2, true) shouldBe Token.Named("newline", 1)
        tillNextWhitespaceTrimmed.unexpectedToken("\taa", 2, true) shouldBe Token.Named("tab", 1)
        tillNextWhitespaceTrimmed.unexpectedToken("\raa", 3, true) shouldBe Token.Named("carriage return", 1)
        tillNextWhitespaceTrimmed.unexpectedToken("\faa".toList, 1, true) shouldBe Token.Named("whitespace character", 1)
        tillNextWhitespaceRaw.unexpectedToken(" aa", 2, true) shouldBe Token.Named("space", 1)
        tillNextWhitespaceRaw.unexpectedToken("\naa", 2, true) shouldBe Token.Named("newline", 1)
        tillNextWhitespaceRaw.unexpectedToken("\taa".toList, 2, true) shouldBe Token.Named("tab", 1)
        tillNextWhitespaceRaw.unexpectedToken("\raa", 3, true) shouldBe Token.Named("carriage return", 1)
        tillNextWhitespaceRaw.unexpectedToken("\faa", 1, true) shouldBe Token.Named("whitespace character", 1)
    }
    it should "refuse to print control characters" in {
        tillNextWhitespaceTrimmed.unexpectedToken("\u0000aaa", 3, true) shouldBe Token.Named("non-printable character (\\u0000)", 1)
        tillNextWhitespaceTrimmed.unexpectedToken("\u0001aaa", 3, true) shouldBe Token.Named("non-printable character (\\u0001)", 1)
        tillNextWhitespaceTrimmed.unexpectedToken("\ud83daaa", 2, true) shouldBe Token.Named("non-printable character (\\ud83d)", 1)
        tillNextWhitespaceRaw.unexpectedToken("\u0000aaa", 3, true) shouldBe Token.Named("non-printable character (\\u0000)", 1)
        tillNextWhitespaceRaw.unexpectedToken("\u0001aaa", 3, true) shouldBe Token.Named("non-printable character (\\u0001)", 1)
        tillNextWhitespaceRaw.unexpectedToken("\ud83daaa", 2, true) shouldBe Token.Named("non-printable character (\\ud83d)", 1)
    }
    it should "refuse to print non-printable supplementary characters" in {
        tillNextWhitespaceTrimmed.unexpectedToken(Character.toChars(0x0f0000), 1, true) shouldBe Token.Named("non-printable codepoint (\\udb80\\udc00, or 0x0f0000)", 1)
        tillNextWhitespaceTrimmed.unexpectedToken(Character.toChars(0x10ffff), 1, true) shouldBe Token.Named("non-printable codepoint (\\udbff\\udfff, or 0x10ffff)", 1)
        tillNextWhitespaceRaw.unexpectedToken(Character.toChars(0x0f0000), 1, true) shouldBe Token.Named("non-printable codepoint (\\udb80\\udc00, or 0x0f0000)", 1)
        tillNextWhitespaceRaw.unexpectedToken(Character.toChars(0x10ffff), 1, true) shouldBe Token.Named("non-printable codepoint (\\udbff\\udfff, or 0x10ffff)", 1)
    }

    val lexToken = new TestErrorBuilder with LexToken {
        def tokens = LexToken.constantSymbols(
            (whitespace, "whitespace"),
            (string("whi"), "keyword whi"),
            (string("while"), "keyword while"),
            (string("if"), "keyword if"),
            (some(letter), "identifier"),
            (string("for"), "keyword for"),
            (some(digit), "number"),
            (char('"') ~> many(parsley.character.noneOf('"')) <~ char('"'), "string"),
        )
    }
    "LexToken" should "handle raw input when no token can be matched" in {
        lexToken.unexpectedToken("...", 2, false) shouldBe Token.Raw("...")
        lexToken.unexpectedToken("...".toList, 2, false) shouldBe Token.Raw("...")
    }
    it should "stop making raw input when a real token is encountered" in {
        lexToken.unexpectedToken("... ...", 2, false) shouldBe Token.Raw("...")
        lexToken.unexpectedToken("...123...", 2, false) shouldBe Token.Raw("...")
    }
    it should "never crash when given an incomplete token" in {
        lexToken.unexpectedToken("\"hello?", 2, false) shouldBe Token.Raw("\"")
    }
    it should "not try and parse tokens when in lexical mode" in {
        lexToken.unexpectedToken("123", 2, true) shouldBe Token.Raw("1")
        lexToken.unexpectedToken(";.,", 3, true) shouldBe Token.Raw(";")
    }
    it should "parse tokens when possible" in {
        lexToken.unexpectedToken("123", 1, false) shouldBe Token.Named("number", 3)
        lexToken.unexpectedToken("\"hello\nworld!\"", 1, false) shouldBe Token.Named("string", 14)
        lexToken.unexpectedToken("whi", 1, false) shouldBe Token.Named("keyword whi", 3)
        // This one loses out to the earlier identifier token!
        lexToken.unexpectedToken("for", 1, false) shouldBe Token.Named("identifier", 3)
        lexToken.unexpectedToken(" for", 1, false) shouldBe Token.Named("whitespace", 1)
    }
    it should "always pick the longest token" in {
        lexToken.unexpectedToken("while ", 1, false) shouldBe Token.Named("keyword while", 5)
        lexToken.unexpectedToken("whiled ", 1, false) shouldBe Token.Named("identifier", 6)
    }
}
