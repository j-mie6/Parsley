/*
 * Copyright 2020 Parsley Contributors <https://github.com/j-mie6/Parsley/graphs/contributors>
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package parsley.token.numeric

import Predef.{ArrowAssoc => _, _}

import parsley.ParsleyTest
import parsley.token.LexemeImpl
import parsley.token.descriptions._
import parsley.token.errors.ErrorConfig
import org.scalactic.source.Position

class UnsignedIntegerTests extends ParsleyTest {
    val errConfig = new ErrorConfig
    val generic = new Generic(errConfig)
    private def makeInteger(desc: NumericDesc) = new LexemeInteger(new UnsignedInteger(desc, errConfig, generic), LexemeImpl.empty)

    val plain = NumericDesc.plain
    val withLeadingZero = makeInteger(plain)
    val withoutLeadingZero = makeInteger(plain.copy(leadingZerosAllowed = false, integerNumbersCanBeBinary = true))
    val withLeadingZeroAndBreak = makeInteger(plain.copy(literalBreakChar = BreakCharDesc.Supported('_', true)))
    val withoutLeadingZeroAndBreak = makeInteger(plain.copy(leadingZerosAllowed = false, literalBreakChar = BreakCharDesc.Supported('_', true)))
    val withLeadingZeroAndBreakNotAfterPrefix = makeInteger(plain.copy(literalBreakChar = BreakCharDesc.Supported('_', false)))
    val withoutLeadingZeroAndBreakNotAfterPrefix = makeInteger(plain.copy(leadingZerosAllowed = false, literalBreakChar = BreakCharDesc.Supported('_', false)))

    private def decimalCases(int: IntegerParsers)(tests: (String, Option[BigInt], Position)*): Unit = cases(int.decimal)(tests: _*)
    private def hexadecimalCases(int: IntegerParsers)(tests: (String, Option[BigInt], Position)*): Unit = cases(int.hexadecimal)(tests: _*)
    private def octalCases(int: IntegerParsers)(tests: (String, Option[BigInt], Position)*): Unit = cases(int.octal)(tests: _*)
    private def binaryCases(int: IntegerParsers)(tests: (String, Option[BigInt], Position)*): Unit = cases(int.binary)(tests: _*)
    private def numberCases(int: IntegerParsers)(tests: (String, Option[BigInt], Position)*): Unit = cases(int.number)(tests: _*)

    //private def decimalCases(desc: NumericDesc)(tests: (String, Option[BigInt], Position)*): Unit = decimalCases(makeInteger(desc))(tests: _*)
    //private def hexadecimalCases(desc: NumericDesc)(tests: (String, Option[BigInt], Position)*): Unit = hexadecimalCases(makeInteger(desc))(tests: _*)
    //private def octalCases(desc: NumericDesc)(tests: (String, Option[BigInt], Position)*): Unit = octalCases(makeInteger(desc))(tests: _*)
    //private def binaryCases(desc: NumericDesc)(tests: (String, Option[BigInt], Position)*): Unit = binaryCases(makeInteger(desc))(tests: _*)
    //private def numberCases(desc: NumericDesc)(tests: (String, Option[BigInt], Position)*): Unit = numberCases(makeInteger(desc))(tests: _*)

    "unsigned decimal" should "parse valid decimal numbers of any size" in decimalCases(withLeadingZero)(
        "0" -> Some(0),
        "0123" -> Some(123),
        "1230980485029" -> Some(1230980485029L),
        "123098048502992634339" -> Some(BigInt("123098048502992634339")),
    )

    it should "not allow for leading zeros when configured" in decimalCases(withoutLeadingZero)(
        "0" -> Some(0),
        "01" -> None,
        "1230980485029" -> Some(1230980485029L),
    )

    it should "allow for literal break characters when configured" in decimalCases(withLeadingZeroAndBreak)(
        "0" -> Some(0),
        "0123" -> Some(123),
        "1230980485029" -> Some(1230980485029L),
        "0_123" -> Some(123),
        "123_0_980_485029" -> Some(1230980485029L),
        "0_" -> None,
        "_9" -> None,
        "123_0_980__485029" -> None,
    )

    it should "allow for literal breaks without leading zeros when configured" in decimalCases(withoutLeadingZeroAndBreak)(
        "0" -> Some(0),
        "0123" -> None,
        "1230980485029" -> Some(1230980485029L),
        "0_123" -> None,
        "1_123" -> Some(1123),
        "123_0_980_485029" -> Some(1230980485029L),
        "1_" -> None,
        "_9" -> None,
        "123_0_980__485029" -> None,
    )

    it should "require at least one digit" in decimalCases(withoutLeadingZeroAndBreak)("" -> None)

    "unsigned hexadecimal" should "parse valid decimal numbers of any size" in hexadecimalCases(withLeadingZero)(
        "0x0" -> Some(0),
        "0x0123" -> Some(0x123),
        "0x1230980485029" -> Some(0x1230980485029L),
        "0x1230f8048502992634339" -> Some(BigInt("1230f8048502992634339", 16)),
    )

    it should "not allow for leading zeros when configured" in hexadecimalCases(withoutLeadingZero)(
        "0x0" -> Some(0),
        "0x01" -> None,
        "0x12f0980485029" -> Some(0x12f0980485029L),
    )

    it should "allow for literal break characters when configured" in hexadecimalCases(withLeadingZeroAndBreak)(
        "0x0" -> Some(0),
        "0x0123" -> Some(0x123),
        "0x1230980485029" -> Some(0x1230980485029L),
        "0x0_123" -> Some(0x123),
        "0x123_0_980_485029" -> Some(0x1230980485029L),
        "0x0_" -> None,
        "0_x9" -> None,
        "0x_9" -> Some(9),
        "0x123_0_980__485029" -> None,
    )

    it should "allow for literal breaks without leading zeros when configured" in hexadecimalCases(withoutLeadingZeroAndBreak)(
        "0x0" -> Some(0),
        "0x0123" -> None,
        "0x1230980485029" -> Some(0x1230980485029L),
        "0x0_123" -> None,
        "0x1_123" -> Some(0x1123),
        "0x123_0_980_485029" -> Some(0x1230980485029L),
        "0x1_" -> None,
        "0_x9" -> None,
        "0x_9" -> Some(9),
        "0x123_0_980__485029" -> None,
    )

    it should "not allow for literal break cases after prefix when configured" in {
        hexadecimalCases(withLeadingZeroAndBreakNotAfterPrefix)("0x_9" -> None)
        hexadecimalCases(withoutLeadingZeroAndBreakNotAfterPrefix)("0x_9" -> None)
    }

    it should "require at least one digit" in hexadecimalCases(withoutLeadingZeroAndBreak)("0x" -> None)

    "unsigned octal" should "parse valid decimal numbers of any size" in octalCases(withLeadingZero)(
        "0o0" -> Some(0),
        "0o0123" -> Some(BigInt("123", 8)),
        "0o1230760465027" -> Some(BigInt("1230760465027", 8)),
        "0o123036046502772634337" -> Some(BigInt("123036046502772634337", 8)),
    )

    it should "not allow for leading zeros when configured" in octalCases(withoutLeadingZero)(
        "0o0" -> Some(0),
        "0o01" -> None,
        "0o1230760465027" -> Some(BigInt("1230760465027", 8)),
    )

    it should "allow for literal break characters when configured" in octalCases(withLeadingZeroAndBreak)(
        "0o0" -> Some(0),
        "0o0123" -> Some(BigInt("123", 8)),
        "0o1230760465027" -> Some(BigInt("1230760465027", 8)),
        "0o0_123" -> Some(BigInt("123", 8)),
        "0o123_0_760_465027" -> Some(BigInt("1230760465027", 8)),
        "0o0_" -> None,
        "0_o7" -> None,
        "0o_7" -> Some(7),
        "0o123_0_760__465027" -> None,
    )

    it should "allow for literal breaks without leading zeros when configured" in octalCases(withoutLeadingZeroAndBreak)(
        "0o0" -> Some(0),
        "0o0123" -> None,
        "0o1230760465027" -> Some(BigInt("1230760465027", 8)),
        "0o0_123" -> None,
        "0o1_123" -> Some(BigInt("1123", 8)),
        "0o123_0_760_465027" -> Some(BigInt("1230760465027", 8)),
        "0o1_" -> None,
        "0_o7" -> None,
        "0o_7" -> Some(7),
        "0o123_0_760__465027" -> None,
    )

    it should "not allow for literal break cases after prefix when configured" in {
        octalCases(withLeadingZeroAndBreakNotAfterPrefix)("0o_7" -> None)
        octalCases(withoutLeadingZeroAndBreakNotAfterPrefix)("0o_7" -> None)
    }

    it should "require at least one digit" in octalCases(withoutLeadingZeroAndBreak)("0o" -> None)

    "unsigned binary" should "parse valid decimal numbers of any size" in binaryCases(withLeadingZero)(
        "0b0" -> Some(0),
        "0b01010" -> Some(BigInt("1010", 2)),
        "0b10111" -> Some(BigInt("10111", 2)),
    )

    it should "not allow for leading zeros when configured" in binaryCases(withoutLeadingZero)(
        "0b0" -> Some(0),
        "0b01" -> None,
        "0b10111" -> Some(BigInt("10111", 2)),
    )

    it should "allow for literal break characters when configured" in binaryCases(withLeadingZeroAndBreak)(
        "0b0" -> Some(0),
        "0b01010" -> Some(BigInt("1010", 2)),
        "0b10111" -> Some(BigInt("10111", 2)),
        "0b0_101" -> Some(BigInt("101", 2)),
        "0b1010_0101_0001_1111" -> Some(0xa51f),
        "0b0_" -> None,
        "0_b1" -> None,
        "0b_1" -> Some(1),
        "0b1010_0101__0001_1111" -> None,
    )

    it should "allow for literal breaks without leading zeros when configured" in binaryCases(withoutLeadingZeroAndBreak)(
        "0b0" -> Some(0),
        "0b01010" -> None,
        "0b10111" -> Some(BigInt("10111", 2)),
        "0b0_101" -> None,
        "0b1_101" -> Some(BigInt("1101", 2)),
        "0b1010_0101_0001_1111" -> Some(0xa51f),
        "0b1_" -> None,
        "0_b1" -> None,
        "0b_1" -> Some(1),
        "0b1010_0101__0001_1111" -> None,
    )

    it should "not allow for literal break cases after prefix when configured" in {
        binaryCases(withLeadingZeroAndBreakNotAfterPrefix)("0b_1" -> None)
        binaryCases(withoutLeadingZeroAndBreakNotAfterPrefix)("0b_1" -> None)
    }

    it should "require at least one digit" in binaryCases(withoutLeadingZeroAndBreak)("0b" -> None)

    "prefixless numbers" should "be supported with a leading 0" in {
        val lex = makeInteger(plain.copy(leadingZerosAllowed = false, octalLeads = Set.empty))
        octalCases(lex)("012" -> Some(BigInt("12", 8)))
        numberCases(lex)("130" -> Some(130), "012" -> Some(BigInt("12", 8)))
    }

    "number" should "support any of the different bases" in numberCases(withoutLeadingZero)(
        "123" -> Some(123),
        "0xff" -> Some(0xff),
        "0" -> Some(0),
        "0o17" -> Some(15),
        "0b1010" -> Some(10),
    )

    "bounded numbers" should "also work across any different base" in {
        cases(withoutLeadingZero.binary8)("0b11010" -> Some(26))
        cases(withoutLeadingZeroAndBreak.binary8[Long])("0b1_0000_0000" -> None)
        cases(withoutLeadingZero.octal16)("0o404" -> Some(260), "0o303240" -> None)
        cases(withoutLeadingZero.decimal32)("1239874" -> Some(1239874))
        cases(withoutLeadingZero.number32)("0xffffffff" -> Some(0xffffffff))
        cases(withoutLeadingZeroAndBreak.number32)("0x1_0000_0000" -> None)
        cases(withoutLeadingZeroAndBreak.hexadecimal64)(
            "0x1_0000_0000" -> Some(0x100000000L),
            "0xffff_ffff_ffff_ffff" -> Some(0xffffffffffffffffL),
            "0x1_0000_0000_0000_0000" -> None
        )
    }
}
