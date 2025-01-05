/*
 * Copyright 2020 Parsley Contributors <https://github.com/j-mie6/Parsley/graphs/contributors>
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package parsley.token.numeric

import Predef.{ArrowAssoc => _, _}

import parsley.ParsleyTest
import parsley.token.LexemeImpl
import parsley.token.descriptions._, ExponentDesc.NoExponents
import parsley.token.errors.ErrorConfig
import org.scalactic.source.Position

class RealTests extends ParsleyTest {
    val errConfig = new ErrorConfig
    val generic = new Generic(errConfig)
    private def makeReal(desc: NumericDesc) = new LexemeReal(new SignedReal(desc, new UnsignedReal(desc, errConfig, generic), errConfig), LexemeImpl.empty, errConfig)

    val plain = NumericDesc.plain.copy(decimalExponentDesc = NoExponents, hexadecimalExponentDesc = NoExponents,
                                       octalExponentDesc = NoExponents, binaryExponentDesc = NoExponents)
    val withLeadingDotDesc = plain.copy(leadingDotAllowed = true)
    val withTrailingDotDesc = plain.copy(trailingDotAllowed = true)
    val withExtremeDotDesc = plain.copy(leadingDotAllowed = true, trailingDotAllowed = true)
    val withoutExtremeDotBreakDesc = plain.copy(literalBreakChar = BreakCharDesc.Supported('_', true))
    val withLeadingDotBreakDesc = plain.copy(leadingDotAllowed = true, literalBreakChar = BreakCharDesc.Supported('_', true))
    val withTrailingDotBreakDesc = plain.copy(trailingDotAllowed = true, literalBreakChar = BreakCharDesc.Supported('_', true))
    val withExtremeDotBreakDesc = plain.copy(leadingDotAllowed = true, trailingDotAllowed = true, literalBreakChar = BreakCharDesc.Supported('_', true))

    val withoutExtremeDot = makeReal(plain)
    val withLeadingDot = makeReal(withLeadingDotDesc)
    val withTrailingDot = makeReal(withTrailingDotDesc)
    val withExtremeDot = makeReal(withExtremeDotDesc)
    val withoutExtremeDotBreak = makeReal(withoutExtremeDotBreakDesc)
    val withLeadingDotBreak = makeReal(withLeadingDotBreakDesc)
    val withTrailingDotBreak = makeReal(withTrailingDotBreakDesc)
    val withExtremeDotBreak = makeReal(withExtremeDotBreakDesc)

    private def decimalCases(real: RealParsers)(tests: (String, Option[BigDecimal], Position)*): Unit = cases(real.decimal)(tests: _*)
    private def hexadecimalCases(real: RealParsers)(tests: (String, Option[BigDecimal], Position)*): Unit = cases(real.hexadecimal)(tests: _*)
    private def octalCases(real: RealParsers)(tests: (String, Option[BigDecimal], Position)*): Unit = cases(real.octal)(tests: _*)
    private def binaryCases(real: RealParsers)(tests: (String, Option[BigDecimal], Position)*): Unit = cases(real.binary)(tests: _*)
    private def numberCases(real: RealParsers)(tests: (String, Option[BigDecimal], Position)*): Unit = cases(real.number)(tests: _*)

    private def decimalCases(desc: NumericDesc)(tests: (String, Option[BigDecimal], Position)*): Unit = decimalCases(makeReal(desc))(tests: _*)
    private def hexadecimalCases(desc: NumericDesc)(tests: (String, Option[BigDecimal], Position)*): Unit = hexadecimalCases(makeReal(desc))(tests: _*)
    private def octalCases(desc: NumericDesc)(tests: (String, Option[BigDecimal], Position)*): Unit = octalCases(makeReal(desc))(tests: _*)
    private def binaryCases(desc: NumericDesc)(tests: (String, Option[BigDecimal], Position)*): Unit = binaryCases(makeReal(desc))(tests: _*)
    private def numberCases(desc: NumericDesc)(tests: (String, Option[BigDecimal], Position)*): Unit = numberCases(makeReal(desc))(tests: _*)

    "decimal reals" should "parse unbounded real numbers" in {
        decimalCases(withoutExtremeDot)(
            "0.0" -> Some(BigDecimal("0")),
            "0.123" -> Some(BigDecimal("0.123")),
            "123456789.987654321" -> Some(BigDecimal("123456789.987654321")),
            "-3.142" -> Some(BigDecimal("-3.142")),
        )
        decimalCases(withoutExtremeDotBreak)(
            "0.0" -> Some(BigDecimal("0")),
            "0.1_23" -> Some(BigDecimal("0.123")),
            "123456789.98_7654321" -> Some(BigDecimal("123456789.987654321")),
            "-3.1_42" -> Some(BigDecimal("-3.142")),
        )
    }
    it should "permit leading and trailing dots when applicable" in {
        decimalCases(withoutExtremeDot)("1." -> None, ".234" -> None)
        decimalCases(withLeadingDot)("1." -> None, ".234" -> Some(BigDecimal("0.234")), "-.2" -> Some(BigDecimal("-0.2")))
        decimalCases(withTrailingDot)("1." -> Some(BigDecimal("1.0")), ".234" -> None)
        decimalCases(withExtremeDot)("0." -> Some(BigDecimal("0")), ".0" -> Some(BigDecimal("0")), "-.1" -> Some(BigDecimal("-0.1")))
    }
    it should "not allow for both leading and trailing dots" in decimalCases(withExtremeDot)("." -> None)
    it should "not allow for a break past a dot" in {
        decimalCases(withoutExtremeDotBreak)("0._1" -> None)
        decimalCases(withExtremeDotBreak)("0._1" -> None)
        decimalCases(withLeadingDotBreak)("0._1" -> None)
        decimalCases(withTrailingDotBreak)("0._1" -> None)
    }
    it should "allow for scientific notation when configured" in {
        val plainExp = plain.copy(decimalExponentDesc = ExponentDesc.Supported(false, Set('e', 'E'), 10, PlusSignPresence.Optional, true))
        val withExtremeDotExpDesc = plainExp.copy(leadingDotAllowed = true, trailingDotAllowed = true)
        decimalCases(plainExp)(
            "3.0e3" -> Some(BigDecimal("3000.0")),
            "3.0E+2" -> Some(BigDecimal("300.0")),
            "100e-1" -> Some(BigDecimal("10.0")),
        )
        decimalCases(withExtremeDotExpDesc)(
            "3.e3" -> Some(BigDecimal("3000.0")),
            "3.E+2" -> Some(BigDecimal("300.0")),
            "100e-1" -> Some(BigDecimal("10.0")),
            ".125e1" -> Some(BigDecimal("1.25")),
        )
    }
    it should "not allow for integer numbers" in {
        decimalCases(withoutExtremeDot)("1" -> None, "0" -> None)
        decimalCases(withoutExtremeDotBreak)("1" -> None, "0" -> None)
        decimalCases(withExtremeDot)("1" -> None, "0" -> None)
        decimalCases(withExtremeDotBreak)("1" -> None, "0" -> None)
    }

    "hexadecimal reals" should "parse unbounded real numbers" in {
        hexadecimalCases(withoutExtremeDot)(
            "0x0.0" -> Some(BigDecimal("0")),
            "0x0.f" -> Some(BigDecimal("0.9375")),
            "-0xa.c7" -> Some(BigDecimal("-10.77734375")),
        )
        hexadecimalCases(withoutExtremeDotBreak)(
            "0x_0.0" -> Some(BigDecimal("0")),
            "0x0.f" -> Some(BigDecimal("0.9375")),
            "-0xa.c_7" -> Some(BigDecimal("-10.77734375")),
        )
    }
    it should "allow for scientific notation when configured" in {
        val plainExp = plain.copy(hexadecimalExponentDesc = ExponentDesc.Supported(true, Set('p', 'P'), 2, PlusSignPresence.Optional, false))
        val withExtremeDotExpDesc = plainExp.copy(leadingDotAllowed = true, trailingDotAllowed = true)
        hexadecimalCases(plainExp)(
            "0x0.f" -> None,
            "0x0.fp0002" -> None,
            "0x0.fp0" -> Some(BigDecimal("0.9375")),
            "0x0.fP1" -> Some(BigDecimal("1.875")),
            "0xa.c7p-2" -> Some(BigDecimal("10.77734375")/4),
        )
        hexadecimalCases(withExtremeDotExpDesc)(
            "0x.f" -> None,
            "0x0.fp0" -> Some(BigDecimal("0.9375")),
            "0x0.fP1" -> Some(BigDecimal("1.875")),
            "0xa.c7p-2" -> Some(BigDecimal("10.77734375")/4),
            "0xf.p+1" -> Some(BigDecimal("30")),
        )
    }
    it should "not allow for integer numbers" in {
        hexadecimalCases(withoutExtremeDot)("0x1" -> None, "0x0" -> None)
        hexadecimalCases(withoutExtremeDotBreak)("0x1" -> None, "0x0" -> None)
        hexadecimalCases(withExtremeDot)("0x1" -> None, "0x0" -> None)
        hexadecimalCases(withExtremeDotBreak)("0x1" -> None, "0x0" -> None)
    }

    "octal reals" should "parse unbounded real numbers" in {
        octalCases(withoutExtremeDot)(
            "0o0.0" -> Some(BigDecimal("0")),
            "0o0.6" -> Some(BigDecimal("0.75")),
            "-0o5.42" -> Some(BigDecimal("-5.53125")),
        )
        octalCases(withoutExtremeDotBreak)(
            "0o_0.0" -> Some(BigDecimal("0")),
            "0o0.6" -> Some(BigDecimal("0.75")),
            "-0o5.4_2" -> Some(BigDecimal("-5.53125")),
        )
    }
    it should "allow for scientific notation when configured" in {
        val plainExp = plain.copy(octalExponentDesc = ExponentDesc.Supported(true, Set('e', 'E'), 10, PlusSignPresence.Required, true))
        val withExtremeDotExpDesc = plainExp.copy(leadingDotAllowed = true, trailingDotAllowed = true)
        octalCases(plainExp)(
            "0o0.6" -> None,
            "0o0.4e+000" -> Some(BigDecimal("0.5")),
            "0o0.4E+1" -> Some(BigDecimal("5.0")),
            "0o5.42e-2" -> Some(BigDecimal("5.53125")/100),
        )
        octalCases(withExtremeDotExpDesc)(
            "0o.4" -> None,
            "0o0.4e+0" -> Some(BigDecimal("0.5")),
            "0o0.4E+1" -> Some(BigDecimal("5.0")),
            "0o5.42e-2" -> Some(BigDecimal("5.53125")/100),
            "0o5.e+1" -> Some(BigDecimal("50")),
        )
    }
    it should "not allow for integer numbers" in {
        octalCases(withoutExtremeDot)("0o1" -> None, "0o0" -> None)
        octalCases(withoutExtremeDotBreak)("0o1" -> None, "0o0" -> None)
        octalCases(withExtremeDot)("0o1" -> None, "0o0" -> None)
        octalCases(withExtremeDotBreak)("0o1" -> None, "0o0" -> None)
    }

    "binary reals" should "parse unbounded real numbers" in {
        binaryCases(withoutExtremeDot)(
            "0b0000.0000" -> Some(BigDecimal("0")),
            "0b0000.1111" -> Some(BigDecimal("0.9375")),
            "-0b1010.11000111" -> Some(BigDecimal("-10.77734375")),
        )
        binaryCases(withoutExtremeDotBreak)(
            "0b_0000.0000" -> Some(BigDecimal("0")),
            "0b0000.1111" -> Some(BigDecimal("0.9375")),
            "-0b1010.1100_0111" -> Some(BigDecimal("-10.77734375")),
        )
    }
    it should "allow for scientific notation when configured" in {
        val plainExp = plain.copy(binaryExponentDesc = ExponentDesc.Supported(true, Set('p', 'P'), 2, PlusSignPresence.Illegal, true),
                                  literalBreakChar = BreakCharDesc.Supported('_', false))
        val withExtremeDotExpDesc = plainExp.copy(leadingDotAllowed = true, trailingDotAllowed = true)
        binaryCases(plainExp)(
            "0b0000.1111" -> None,
            "0b0000.1111p0" -> Some(BigDecimal("0.9375")),
            "0b0000.1111P1" -> Some(BigDecimal("1.875")),
            "0b1010.1100_0111p-2" -> Some(BigDecimal("10.77734375")/4),
        )
        binaryCases(withExtremeDotExpDesc)(
            "0b.1111" -> None,
            "0b0000.1111p0" -> Some(BigDecimal("0.9375")),
            "0b0000.1111P1" -> Some(BigDecimal("1.875")),
            "0b1010.1100_0111p-2" -> Some(BigDecimal("10.77734375")/4),
            "0b1111.p1" -> Some(BigDecimal("30")),
            "0b1111.p+1" -> None,
        )
    }
    it should "not allow for integer numbers" in {
        binaryCases(withoutExtremeDot)("0b1" -> None, "0b0" -> None)
        binaryCases(withoutExtremeDotBreak)("0b1" -> None, "0b0" -> None)
        binaryCases(withExtremeDot)("0b1" -> None, "0b0" -> None)
        binaryCases(withExtremeDotBreak)("0b1" -> None, "0b0" -> None)
    }

    // number
    "generic reals" should "parse in any base" in {
        val plainAll = plain.copy(realNumbersCanBeHexadecimal = true, realNumbersCanBeOctal = true, realNumbersCanBeBinary = true)
        numberCases(plainAll)(
            "0.4" -> Some(BigDecimal("0.4")),
            "0xf.8" -> Some(BigDecimal("15.5")),
            "0o0.4" -> Some(BigDecimal("0.5")),
            "0b1.11" -> Some(BigDecimal("1.75")),
        )
    }

    // bounded things (only decimal)
    "bounded reals" should "not permit illegal numbers" in {
        val represent = makeReal(plain.copy(decimalExponentDesc = ExponentDesc.Supported(false, Set('e', 'E'), 10, PlusSignPresence.Optional, true),
                                            hexadecimalExponentDesc = ExponentDesc.Supported(true, Set('p'), 2, PlusSignPresence.Required, true),
                                            binaryExponentDesc = ExponentDesc.Supported(true, Set('p'), 2, PlusSignPresence.Required, true)))
        cases(represent.decimalDouble)(
            "0.4" -> Some(0.4),
            "0.33333333333333333333333" -> Some(0.33333333333333333333333),
            "0.3333333333333333" -> Some(0.3333333333333333),
            "-1.0" -> Some(-1.0),
            "1e-5000" -> None,
            "1e5000" -> None,
            "0.0" -> Some(0.0),
            "-0.0" -> Some(-0.0),
            Double.MaxValue.toString -> Some(Double.MaxValue),
            Double.MinPositiveValue.toString -> Some(Double.MinPositiveValue),
            Double.MinValue.toString -> Some(Double.MinValue)
        )
        cases(represent.float)(
            "0.4000000059604644775390625" -> Some(0.4f),
            "0.3333333333333333" -> Some(0.3333333333333333f),
            "0.33333334" -> Some(0.33333334f),
            "0.3333333432674407958984375" -> Some(0.33333334f),
            "0.3333333" -> Some(0.3333333f),
            "-4.5" -> Some(-4.5f),
            "1e300" -> None,
            "1e-300" -> None,
            "0.0" -> Some(0.0f),
            "-0.0" -> Some(-0.0f),
            Float.MaxValue.toString -> Some(Float.MaxValue),
            Float.MinPositiveValue.toString -> Some(Float.MinPositiveValue),
            Float.MinValue.toString -> Some(Float.MinValue)
        )
        cases(represent.octalDouble)(
            "0o0.6" -> Some(0.75),
        )

        info("trying known valid and invalid exact doubles")
        // these can each have 13 hexdigits after the dot and between -1022 and +1023 as the exponent
        cases(represent.hexadecimalExactDouble)(
            "+0x1.0000000000000p+0000" -> Some(double(0, 0x0000000000000L, 0)),
            // FIXME: Not sure why this isn't exact? (I think we need to write our own exactness check)
            //        This is because BigDecimal.exact(x.toDouble) != x as it has way more numbers, but
            //        using toDouble on the exact one reveals they have the same binary. It's probably
            //        not safe to roundtrip via double though, so I'm not sure what the best option is?
            //"+0x1.65ab0f00e0809p-0456" -> Some(double(0, 0x65ab0f00e0809L, -456)),
            "-0x1.1111000000000p+0002" -> Some(double(1, 0x1111000000000L, 2)),
            "+0x1.0000000000000p-1023" -> None, // 0
            "-0x1.0000000000000p-1023" -> None, // -0
            "+0x1.0000000000000p+1024" -> None, // inf
            "-0x1.0000000000000p+1024" -> None, // -inf
        )
        info("trying known valid and invalid exact floats")
        // these can each have 23 bits after the dot and between -126 and +127 as the exponent
        cases(represent.binaryExactFloat)(
            "+0b1.00000000000000000000000p+000" -> Some(float(0, 0x00000000, 0)),
            "+0b1.01000000000000000000000p-003" -> Some(float(0, 0x00200000, -3)),
            "-0b1.11110000000000000000000p+002" -> Some(float(1, 0x00780000, 2)),
            "+0b1.00000000000000000000000p-127" -> None, // 0
            "-0b1.00000000000000000000000p-127" -> None, // -0
            "+0b1.00000000000000000000000p+128" -> None, // inf
            "-0b1.00000000000000000000000p+128" -> None, // -inf
            "+0b1.00000000001110000000000p+128" -> None, // NaN
        )
    }

    private def double(sign: Long, mantissa: Long, exponent: Long) = {
        val e = (exponent + 1023) << 52
        val s = sign << 63
        val x = s | e | mantissa
        java.lang.Double.longBitsToDouble(x)
    }

    private def float(sign: Int, mantissa: Int, exponent: Int) = {
        val e = (exponent + 127) << 23
        val s = sign << 31
        val x = s | e | mantissa
        java.lang.Float.intBitsToFloat(x)
    }
}
