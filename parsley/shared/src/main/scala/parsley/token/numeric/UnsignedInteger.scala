/*
 * Copyright 2020 Parsley Contributors <https://github.com/j-mie6/Parsley/graphs/contributors>
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package parsley.token.numeric

import parsley.Parsley, Parsley.{atomic, pure, unit}
import parsley.character.oneOf
import parsley.combinator.optional
import parsley.errors.combinator.ErrorMethods
import parsley.syntax.character.charLift
import parsley.token.descriptions.{BreakCharDesc, NumericDesc}
import parsley.token.errors.{ErrorConfig, LabelWithExplainConfig}

private [token] final class UnsignedInteger(desc: NumericDesc, err: ErrorConfig, generic: Generic) extends IntegerParsers(desc) {

    // labelless versions
    protected [numeric] override lazy val _decimal: Parsley[BigInt] = generic.plainDecimal(desc, err.labelIntegerDecimalEnd)
    protected [numeric] override lazy val _hexadecimal: Parsley[BigInt] = atomic('0' *> noZeroHexadecimal)
    protected [numeric] override lazy val _octal: Parsley[BigInt] = atomic('0' *> noZeroOctal)
    protected [numeric] override lazy val _binary: Parsley[BigInt] = atomic('0' *> noZeroBinary)
    // FIXME: numberEnd label is not applied here!
    protected [numeric] override lazy val _number: Parsley[BigInt] = {
        if (desc.decimalIntegersOnly) decimal
        else {
            def addHex(p: Parsley[BigInt]) = {
                if (desc.integerNumbersCanBeHexadecimal) noZeroHexadecimal <|> p
                else p
            }
            def addOct(p: Parsley[BigInt]) = {
                if (desc.integerNumbersCanBeOctal) noZeroOctal <|> p
                else p
            }
            def addBin(p: Parsley[BigInt]) = {
                if (desc.integerNumbersCanBeBinary) noZeroBinary <|> p
                else p
            }
            val zeroLead = '0'.label("digit") *> (addHex(addOct(addBin(decimal <|> pure(BigInt(0))))))
            atomic(zeroLead <|> decimal)
        }
    }

    override def decimal: Parsley[BigInt] = err.labelIntegerUnsignedDecimal.apply(_decimal)
    override def hexadecimal: Parsley[BigInt] = err.labelIntegerUnsignedHexadecimal.apply(_hexadecimal)
    override def octal: Parsley[BigInt] = err.labelIntegerUnsignedOctal.apply(_octal)
    override def binary: Parsley[BigInt] = err.labelIntegerUnsignedBinary.apply(_binary)
    override def number: Parsley[BigInt] = err.labelIntegerUnsignedNumber.apply(_number)

    private def when(b: Boolean, p: Parsley[_]): Parsley[_] = if (b) p else unit

    val leadingBreakChar = desc.literalBreakChar match {
        case BreakCharDesc.NoBreakChar => unit
        case BreakCharDesc.Supported(breakChar, allowedAfterNonDecimalPrefix) => when(allowedAfterNonDecimalPrefix, optional(breakChar))
    }

    private val noZeroHexadecimal =
        when(desc.hexadecimalLeads.nonEmpty, oneOf(desc.hexadecimalLeads)) *> leadingBreakChar *>
        err.labelIntegerHexadecimalEnd(generic.plainHexadecimal(desc, err.labelIntegerHexadecimalEnd))
    private val noZeroOctal =
        when(desc.octalLeads.nonEmpty, oneOf(desc.octalLeads)) *> leadingBreakChar *>
        err.labelIntegerOctalEnd(generic.plainOctal(desc, err.labelIntegerOctalEnd))
    private val noZeroBinary =
        when(desc.binaryLeads.nonEmpty, oneOf(desc.binaryLeads)) *> leadingBreakChar *>
        err.labelIntegerBinaryEnd(generic.plainBinary(desc, err.labelIntegerBinaryEnd))

    override protected [numeric] def bounded[T](number: Parsley[BigInt], bits: Bits, radix: Int, label: (ErrorConfig, Boolean) => LabelWithExplainConfig)
                                               (implicit ev: CanHold[bits.self,T]): Parsley[T] = label(err, false) {
        err.filterIntegerOutOfBounds(min = 0, bits.upperUnsigned, radix).collect(number) {
            case x if 0 <= x && x <= bits.upperUnsigned => ev.fromBigInt(x)
        }
    }
}
