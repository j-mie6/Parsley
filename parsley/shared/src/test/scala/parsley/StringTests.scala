/*
 * Copyright 2020 Parsley Contributors <https://github.com/j-mie6/Parsley/graphs/contributors>
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package parsley

import Predef.{ArrowAssoc => _, _}

import parsley.character.{string, strings, stringOfMany, stringOfSome}
import parsley.syntax.character.stringLift
import parsley.Parsley._
import parsley.position.pos

class StringTests extends ParsleyTest {
    private def stringPositionCheck(initialCol: Int, str: String) = {
        ((if (initialCol == 0) pure("") else string("." * initialCol)) *> string(str) *> pos).parse("." * initialCol + str)
    }

    "string" should "consume succeed if it is found at head" in {
        "abc".parse("abc") should not be a [Failure[_]]
    }
    it should "not consume input if it fails on first character" in {
        ("abc" <|> "b").parse("b") should not be a [Failure[_]]
    }
    it should "consume input if it fails mid-string" in {
        ("abc" <|> "ab").parse("ab") shouldBe a [Failure[_]]
    }
    it should "not consume input if it fails mid-string when combined with atomic" in {
        (atomic("abc") <|> "ab").parse("ab") should not be a [Failure[_]]
    }
    it should "update positions correctly" in {
        stringPositionCheck(0, "abc") shouldBe Success((1, 4))
        stringPositionCheck(1, "\na") shouldBe Success((2, 2))
        stringPositionCheck(0, "a\t") shouldBe Success((1, 5))
        stringPositionCheck(0, "ab\t") shouldBe Success((1, 5))
        stringPositionCheck(0, "abc\t") shouldBe Success((1, 5))
        stringPositionCheck(0, "abcd\t") shouldBe Success((1, 9))
        stringPositionCheck(0, "\na\tb") shouldBe Success((2, 6))
        stringPositionCheck(2, "\t") shouldBe Success((1, 5))
    }
    it should "respect multiple tabs" in {
        stringPositionCheck(2, "\t\t") shouldBe Success((1, 9))
        stringPositionCheck(2, "\t\t\t") shouldBe Success((1, 13))
        stringPositionCheck(2, "\taaa\t") shouldBe Success((1, 9))
        stringPositionCheck(2, "\taa\taaa\t") shouldBe Success((1, 13))
        stringPositionCheck(2, "a\t\t") shouldBe Success((1, 9))
        stringPositionCheck(2, "aa\t") shouldBe Success((1, 9))
    }
    it should "reject the empty string" in {
        an [IllegalArgumentException] shouldBe thrownBy (string(""))
    }

    "strings" should "have longest match behaviour" in cases(strings("hell", "hello", "h"), noEof = true)(
        "hello" -> Some("hello"),
        "hell"  -> Some("hell"),
        "he"    -> Some("h"),
    )
    it should "be extrinsically the same as a manual equivalent" in {
        val p = strings("hell", "hello", "abc", "g", "goodbye")
        val q = string("abc") <|> atomic("goodbye") <|> string("g") <|> atomic(string("hello")) <|> string("hell")
        info("parsing \"hello\"")
        p.parse("hello") shouldBe q.parse("hello")
        info("parsing \"hell\"")
        p.parse("hell") shouldBe q.parse("hell")
        info("parsing \"h\"")
        p.parse("h") shouldBe q.parse("h")
        info("parsing \"a\"")
        p.parse("a") shouldBe q.parse("a")
        info("parsing \"b\"")
        p.parse("b") shouldBe q.parse("b")
    }

    "character.stringOfMany" should "allow for no letters" in cases(stringOfMany(_.isLetter))("" -> Some(""))
    it should "consume as many letters as it can" in cases(stringOfMany(_.isLetter), noEof = true)(
        "abc" -> Some("abc"),
        "ab4c" -> Some("ab"),
    )
    it should "fail if pc fails having consumed input" in cases(stringOfMany(string("ab").as('a')))(
        "ababab" -> Some("aaa"),
        "aba" -> None,
    )

    "character.stringOfSome" should "not allow for no letters" in cases(stringOfSome(_.isLetter))("" -> None)
    it should "consume as many letters as it can" in cases(stringOfSome(_.isLetter), noEof = true)(
        "abc" -> Some("abc"),
        "ab4c" -> Some("ab"),
    )
    it should "fail if pc fails having consumed input" in cases(stringOfSome(string("ab").as('a')))(
        "ababab" -> Some("aaa"),
        "aba" -> None,
    )

    private def isEmoji(c: Int) = c >= 0x1f600 && c <= 0x1f64f || c >= 0x1f900 && c <= 0x1f970
    "unicode.stringOfMany" should "allow for no letters" in cases(unicode.stringOfMany(isEmoji(_)))("" -> Some(""))
    it should "consume as many letters as it can" in cases(unicode.stringOfMany(isEmoji(_)), noEof = true)(
        "😀😅😂😇🥰😍🤩" -> Some("😀😅😂😇🥰😍🤩"),
        "😀😅🌿😂😇🥰😍🤩" -> Some("😀😅"),
    )
    it should "fail if pc fails having consumed input" in cases(unicode.stringOfMany(string("🌿🌿").as(0x1f33f)))(
        "🌿🌿🌿🌿🌿🌿" -> Some("🌿🌿🌿"),
        "🌿🌿🌿" -> None,
    )

    "unicode.stringOfSome" should "not allow for no letters" in cases(unicode.stringOfSome(isEmoji(_)))("" -> None)
    it should "consume as many letters as it can" in cases(unicode.stringOfSome(isEmoji(_)), noEof = true)(
        "😀😅😂😇🥰😍🤩" -> Some("😀😅😂😇🥰😍🤩"),
        "😀😅🌿😂😇🥰😍🤩" -> Some("😀😅"),
    )
    it should "fail if pc fails having consumed input" in cases(unicode.stringOfSome(string("🌿🌿").as(0x1f33f)))(
        "🌿🌿🌿🌿🌿🌿" -> Some("🌿🌿🌿"),
        "🌿🌿🌿" -> None,
    )
}
