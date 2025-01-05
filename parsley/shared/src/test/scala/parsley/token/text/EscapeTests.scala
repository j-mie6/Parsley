/*
 * Copyright 2020 Parsley Contributors <https://github.com/j-mie6/Parsley/graphs/contributors>
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package parsley.token.text

import scala.Predef.{String => SString, ArrowAssoc => _, _}
import parsley.ParsleyTest

import parsley.token.descriptions._
import parsley.token.errors.ErrorConfig
import org.scalactic.source.Position

class EscapeTests extends ParsleyTest {
    val errConfig = new ErrorConfig
    val generic = new parsley.token.numeric.Generic(errConfig)
    def cases(desc: EscapeDesc)(tests: (SString, Option[Int], Position)*): Unit = cases(new Escape(desc, errConfig, generic).escapeChar)(tests: _*)

    val plain = EscapeDesc.plain

    "escape characters" should "handle regular literal escapes" in cases(plain.copy(literals = Set('\\', '\"', '\'')))(
        "\\\\" -> Some('\\'),
        "\\\'" -> Some('\''),
        "\\\"" -> Some('\"'),
        "\\n" -> None,
    )

    it should "handle a change in opening character" in cases(plain.copy(escBegin = '@'))(
        "@\\" -> Some('\\'),
        "@@" -> None,
    )

    it should "handle single-character mappings" in cases(plain.copy(mapping = Map(("n", '\n'), ("t", '\t'), ("b", '\b'))))(
        "\\n" -> Some('\n'),
        "\\b" -> Some('\b'),
        "\\t" -> Some('\t'),
        "\\\'" -> None,
    )

    it should "handle multi-character mappings" in cases(plain.copy(mapping = Map(("ab", '0'), ("aa", '1'), ("aaa", '2'), ("x", '3'))))(
        "\\ab" -> Some('0'),
        "\\aa" -> Some('1'),
        "\\aaa" -> Some('2'),
        "\\x" -> Some('3'),
        "\\abb" -> None,
        "\\y" -> None,
    )

    it should "not deal with empty escapes or gaps" in cases(plain.copy(emptyEscape = Some('&'), gapsSupported = true))(
        "\\ \\" -> None,
        "\\&" -> None,
    )

    it should "handle decimal escapes" in cases(plain.copy(decimalEscape = NumericEscape.Supported(prefix = None, numDigits = NumberOfDigits.Unbounded, maxValue = 16)))(
        "\\123" -> None,
        "\\12" -> Some(12),
        "\\4" -> Some(4),
        "\\16" -> Some(16),
        "\\17" -> None,
    )

    it should "handle octal escapes" in cases(plain.copy(octalEscape = NumericEscape.Supported(prefix = None, numDigits = NumberOfDigits.Exactly(3), maxValue = 100)))(
        "\\100" -> Some(64),
        "\\050" -> Some(40),
        "\\003" -> Some(3),
        "\\000" -> Some(0),
        "\\00" -> None,
        "\\10" -> None,
        "\\2" -> None,
        "\\0100" -> None,
    )

    it should "handle hexadecimal escapes" in cases(plain.copy(hexadecimalEscape = NumericEscape.Supported(prefix = Some('x'), numDigits = NumberOfDigits.AtMost(4), maxValue = 0x1ffff)))(
        "\\xffff" -> Some(0xffff),
        "\\x1fffe" -> None,
        "\\xa3f" -> Some(0xa3f),
        "\\xa" -> Some(0xa),
        "\\34" -> None,
    )

    it should "handle binary escapes" in cases(plain.copy(binaryEscape = NumericEscape.Supported(prefix = Some('b'), numDigits = NumberOfDigits.Exactly(1, 8, 4, 2), maxValue = 0x80)))(
        "\\b0" -> Some(0),
        "\\b1" -> Some(1),
        "\\b2" -> None,
        "\\b01" -> Some(1),
        "\\b10" -> Some(2),
        "\\b101" -> None,
        "\\b0100" -> Some(4),
        "\\b1010" -> Some(10),
        "\\b1111" -> Some(15),
        "\\b10000" -> None,
        "\\b100000" -> None,
        "\\b1000000" -> None,
        "\\b10000000" -> Some(128),
        "\\b10000001" -> None,
        "\\b01111111" -> Some(127),
        "\\b011111110" -> None,
    )
}
