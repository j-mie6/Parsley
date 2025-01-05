/*
 * Copyright 2020 Parsley Contributors <https://github.com/j-mie6/Parsley/graphs/contributors>
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package parsley.token.text

import scala.Predef.{ArrowAssoc => _, _}
import parsley.ParsleyTest
import parsley.token.LexemeImpl

import parsley.token.descriptions._
import parsley.token.errors.ErrorConfig
import parsley.character.space
import org.scalactic.source.Position
import parsley.token.{Basic, Unicode, NotRequired}

class StringTests extends ParsleyTest {
    val errConfig = new ErrorConfig
    val generic = new parsley.token.numeric.Generic(errConfig)
    private def makeString(desc: TextDesc, char: StringCharacter, spaceAllowed: Boolean) =
        new LexemeString(new ConcreteString(desc.stringEnds, char, desc.graphicCharacter, spaceAllowed, errConfig), LexemeImpl.empty)
    private def makeString(desc: TextDesc): StringParsers =
        makeString(desc, new EscapableCharacter(desc.escapeSequences, new Escape(desc.escapeSequences, errConfig, generic), space, errConfig), false)
    private def makeMultiString(desc: TextDesc): StringParsers =
        makeString(desc, new EscapableCharacter(desc.escapeSequences, new Escape(desc.escapeSequences, errConfig, generic), space, errConfig), true)
    private def makeRawString(desc: TextDesc): StringParsers =
        makeString(desc, new RawCharacter(errConfig), false)
    private def makeRawMultiString(desc: TextDesc): StringParsers =
        makeString(desc, new RawCharacter(errConfig), true)

    def unicodeCases(str: StringParsers)(tests: (String, Option[String], Position)*): Unit = cases(str.fullUtf16)(tests: _*)
    def asciiCases(str: StringParsers)(tests: (String, Option[String], Position)*): Unit = cases(str.ascii)(tests: _*)
    def extAsciiCases(str: StringParsers)(tests: (String, Option[String], Position)*): Unit = cases(str.latin1)(tests: _*)

    val plain = TextDesc.plain.copy(
        graphicCharacter = Unicode(_ >= ' '),
        escapeSequences = EscapeDesc.plain.copy(mapping = Map(("lf", '\n'), ("lam", 'λ'), ("pound", '£'), ("smile", 0x1F642 /*🙂*/))),
        multiStringEnds = Set(("\"", "\"")),
    )
    val plainStr = makeString(plain)
    val plainMultiStr = makeMultiString(plain)
    val rawStr = makeRawString(plain)
    val rawMultiStr = makeRawMultiString(plain)

    "string literals" should "be allowed to be empty" in unicodeCases(plainStr)(
        "\"\"" -> Some(""),
    )

    they should "be allowed to be non-empty" in unicodeCases(plainStr)(
        "\"hello world!\"" -> Some("hello world!"),
        "hello" -> None,
        "\"" -> None,
        "\"🙂🙂\"" -> Some("🙂🙂"),
        "\"🇬🇧\"" -> Some("🇬🇧"),
    )

    they should "allow for escape sequences" in unicodeCases(plainStr)(
        "\"\\smilea\"" -> Some("🙂a"),
    )

    they should "allow for different graphic characters" in asciiCases(makeString(plain.copy(graphicCharacter = Basic(Set('a', 'b')))))(
        "\"aab\"" -> Some("aab"),
        "\"abc\"" -> None,
    )

    they should "allow for change in literal end" in unicodeCases(makeString(plain.copy(stringEnds = Set(("@-", "-@")))))(
        "@--@" -> Some(""),
        "@-a-c-@" -> Some("a-c"),
        "@-" -> None,
        "\"\"" -> None,
    )

    they should "allow for string gaps when configured" in unicodeCases(makeString(plain.copy(escapeSequences = plain.escapeSequences.copy(gapsSupported = true))))(
        "\"hello \\          \\world!\"" -> Some("hello world!"),
        "\"hello \\ world!\"" -> None,
    )

    they should "allow for empty characters when configured" in unicodeCases(makeString(plain.copy(escapeSequences = plain.escapeSequences.copy(emptyEscape = Some('&')))))(
        "\"hello\\& world!\"" -> Some("hello world!"),
    )

    they should "allow for no graphical character" in unicodeCases(makeString(plain.copy(graphicCharacter = NotRequired)))(
        "\"a\"" -> None,
        "\"\\smile\\lf\"" -> Some("🙂\n"),
    )

    "plain string literals" should "not allow for newlines in the string" in unicodeCases(plainStr)(
        "\"hello \n world\"" -> None,
    )

    "plain utf-16 string literals" should "parse valid utf-16 strings, even with multiple codepoints" in unicodeCases(plainStr)(
        "\"\"" -> Some(""),
        "\"hello world!\"" -> Some("hello world!"),
        "\"🙂🙂\"" -> Some("🙂🙂"),
        "\"🇬🇧\"" -> Some("🇬🇧"),
    )

    "plain latin string literals" should "parse valid latin" in extAsciiCases(plainStr)(
        "\"hello world\"" -> Some("hello world"),
        "\"£\\pound\"" -> Some("££"),
        "\"\"" -> Some(""),
    )

    they should "not accept bmp or other unicode, including in escapes" in extAsciiCases(plainStr)(
        "\"λ\"" -> None,
        "\"\\lam\"" -> None,
        "\"🙂\"" -> None,
        "\"\\smile\"" -> None,
    )

    "plain ascii string literals" should "parse only valid ascii" in asciiCases(plainStr)(
        "\"Hey\"" -> Some("Hey"),
        "\"$$$\"" -> Some("$$$"),
        "\"\\lf\"" -> Some("\n"),
    )

    they should "not accept latin or unicode, including from escapes" in asciiCases(plainStr)(
        "\"£\"" -> None,
        "\"\\pound\"" -> None,
        "\"\\smile\"" -> None,
    )

    "plain multi-line string literals" should "parse regular strings" in unicodeCases(plainMultiStr)(
        "\"hello world\"" -> Some("hello world"),
        "\"\\smile\"" -> Some("🙂"),
    )

    they should "also allow (and preserve) line breaks in the string" in unicodeCases(plainMultiStr)(
        "\"hello \n world\"" -> Some("hello \n world"),
    )

    "raw string literals" should "parse regular strings without escapes" in unicodeCases(rawStr)(
        "\"hello world\"" -> Some("hello world"),
        "\"hello \\n world\"" -> Some("hello \\n world"),
    )

    they should "not allow for escaped string ends" in unicodeCases(rawStr)(
        "\"\\\"\"" -> None,
    )

    they should "not allow for line breaks" in unicodeCases(rawStr)(
        "\"hello \n world\"" -> None,
    )

    they should "allow only for a empty-string when no graphic character allowed" in asciiCases(makeRawString(plain.copy(graphicCharacter = NotRequired)))(
        "\"\"" -> Some(""),
        "\"h\"" -> None,
        "\"\\n\"" -> None,
        "\"\n\"" -> None,
    )

    "raw multi-line string literals" should "preserve line breaks in text and not admit escapes" in asciiCases(rawMultiStr)(
        "\"hello \n world\"" -> Some("hello \n world"),
        "\"hello \\lam\n\"" -> Some("hello \\lam\n"),
    )

    they should "allow for the graphic char to be swapped" in asciiCases(makeRawMultiString(plain.copy(graphicCharacter = Basic(Set('a', 'b', 'c')))))(
        "\"abc\"" -> Some("abc"),
        "\"abc\nc\"" -> Some("abc\nc"),
        "\"d\"" -> None,
    )
}
