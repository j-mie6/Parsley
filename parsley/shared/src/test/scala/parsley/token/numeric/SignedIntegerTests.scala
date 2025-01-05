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

class SignedIntegerTests extends ParsleyTest {
    val errConfig = new ErrorConfig
    val generic = new Generic(errConfig)
    private def makeInteger(desc: NumericDesc) = new LexemeInteger(new SignedInteger(desc, new UnsignedInteger(desc, errConfig, generic), errConfig), LexemeImpl.empty)

    val plain = NumericDesc.plain.copy(integerNumbersCanBeBinary = true, literalBreakChar = BreakCharDesc.Supported('_', false))
    val optionalPlus = makeInteger(plain.copy(positiveSign = PlusSignPresence.Optional))
    val noPlus = makeInteger(plain.copy(positiveSign = PlusSignPresence.Illegal))
    val alwaysPlus = makeInteger(plain.copy(positiveSign = PlusSignPresence.Required))

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

    "signed decimal" should "parse valid decimal numbers of any size" in decimalCases(optionalPlus)(
        "0"                     -> Some(0),
        "+0123"                 -> Some(123),
        "-1230980485029"        -> Some(-1230980485029L),
        "123098048502992634339" -> Some(BigInt("123098048502992634339")),
    )

    it should "not allow for plus when configured" in decimalCases(noPlus)(
        "-0"             -> Some(0),
        "+1"             -> None,
        "-1230980485029" -> Some(-1230980485029L),
    )

    it should "require a plus when configured" in decimalCases(alwaysPlus)(
        "0"              -> None,
        "+1"             -> Some(1),
        "-1230980485029" -> Some(-1230980485029L),
    )

    it should "require at least one digit" in {
        decimalCases(optionalPlus)("" -> None, "+" -> None, "-" -> None)
        decimalCases(noPlus)("" -> None, "-" -> None)
        decimalCases(alwaysPlus)("+" -> None, "-" -> None)
    }

    "signed hexadecimal" should "parse valid decimal numbers of any size" in hexadecimalCases(optionalPlus)(
        "0x0"                     -> Some(0),
        "+0x0123"                 -> Some(0x123),
        "-0x1230980485029"        -> Some(-0x1230980485029L),
        "0x123098048502992634339" -> Some(BigInt("123098048502992634339", 16)),
    )

    it should "not allow for plus when configured" in hexadecimalCases(noPlus)(
        "-0x0"             -> Some(0),
        "+0x1"             -> None,
        "-0x1230980485029" -> Some(-0x1230980485029L),
    )

    it should "require a plus when configured" in hexadecimalCases(alwaysPlus)(
        "0x0"              -> None,
        "+0x1"             -> Some(0x1),
        "-0x1230980485029" -> Some(-0x1230980485029L),
    )

    "unsigned octal" should "parse valid decimal numbers of any size" in octalCases(optionalPlus)(
        "0o0"                     -> Some(0),
        "+0o0123"                 -> Some(BigInt("123", 8)),
        "-0o1230760465027"        -> Some(BigInt("-1230760465027", 8)),
        "0o123036046502772634337" -> Some(BigInt("123036046502772634337", 8)),
    )

    it should "not allow for plus when configured" in octalCases(noPlus)(
        "-0o0"             -> Some(0),
        "+0o01"            -> None,
        "-0o1230760465027" -> Some(BigInt("-1230760465027", 8)),
    )

    it should "require a plus when configured" in octalCases(alwaysPlus)(
        "0o0"              -> None,
        "+0o01"            -> Some(0x1),
        "-0o1230760465027" -> Some(BigInt("-1230760465027", 8)),
    )

    "unsigned binary" should "parse valid decimal numbers of any size" in binaryCases(optionalPlus)(
        "0b0"      -> Some(0),
        "+0b01010" -> Some(BigInt("1010", 2)),
        "-0b10111" -> Some(BigInt("-10111", 2)),
    )

    it should "not allow for leading zeros when configured" in binaryCases(noPlus)(
        "0b0"      -> Some(0),
        "+0b01"    -> None,
        "-0b10111" -> Some(BigInt("-10111", 2)),
    )

    it should "allow for literal break characters when configured" in binaryCases(alwaysPlus)(
        "0b0"      -> None,
        "+0b01"    -> Some(1),
        "-0b10111" -> Some(BigInt("-10111", 2)),
    )

    "number" should "support any of the different bases" in {
        numberCases(noPlus)("123" -> Some(123), "0xff" -> Some(0xff))
        numberCases(alwaysPlus)("+0" -> Some(0), "+0o17" -> Some(15))
        numberCases(optionalPlus)("-0b1010" -> Some(-10))
    }

    "bounded numbers" should "also work across any different base" in {
        cases(optionalPlus.binary8)(
            "0b11010"                -> Some(26),
            "0b0111_1111"            -> Some(127),
            "0b1111_1111"            -> None,
            "-0b1000_0000"           -> Some(-128),
            "-0b1_0000_0000"         -> None,
        )
        cases(optionalPlus.binary8[Long])("0b1_0000_0000" -> None)
        cases(optionalPlus.octal16)(
            "0o404"                  -> Some(260),
            "0o303240"               -> None,
        )
        cases(optionalPlus.hexadecimal16)(
            "0x7fff"                 -> Some(0x7fff),
            "-0x8000"                -> Some(-0x8000),
            "0x8000"                 -> None,
        )
        cases(optionalPlus.decimal32)("1239874" -> Some(1239874))
        cases(optionalPlus.number32)(
            "0x7fff_ffff"            -> Some(0x7fffffff),
            "-0x8000_0000"           -> Some(-0x80000000),
            "0x8000_0000"            -> None,
        )
        cases(optionalPlus.hexadecimal64)(
            "0x1_0000_0000"          -> Some(0x100000000L),
            "0x7fff_ffff_ffff_ffff"  -> Some(0x7fffffffffffffffL),
            "-0x8000_0000_0000_0000" -> Some(-0x8000000000000000L),
            "0x8000_0000_0000_0000"  -> None,
        )
    }
}
