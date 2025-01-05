/*
 * Copyright 2020 Parsley Contributors <https://github.com/j-mie6/Parsley/graphs/contributors>
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package parsley.token.numeric

import parsley.Parsley, Parsley.{atomic, empty, pure, unit}
import parsley.character.{bit, digit, hexDigit, octDigit, oneOf}
import parsley.combinator, combinator.optional
import parsley.errors.combinator.{amendThenDislodge, entrench}
import parsley.lift.lift2
import parsley.state.Ref
import parsley.syntax.character.charLift
import parsley.token.descriptions.{BreakCharDesc, ExponentDesc, NumericDesc}
import parsley.token.errors.{ErrorConfig, LabelConfig}

private [token] final class UnsignedReal(desc: NumericDesc, err: ErrorConfig, generic: Generic) extends RealParsers(err) {
    override lazy val _decimal: Parsley[BigDecimal] = atomic(ofRadix(10, digit, err.labelRealDecimalEnd))
    override lazy val _hexadecimal: Parsley[BigDecimal] = atomic('0' *> noZeroHexadecimal)
    override lazy val _octal: Parsley[BigDecimal] = atomic('0' *> noZeroOctal)
    override lazy val _binary: Parsley[BigDecimal] = atomic('0' *> noZeroBinary)
    override lazy val _number: Parsley[BigDecimal] = {
        if (desc.decimalRealsOnly) decimal
        else {
            def addHex(p: Parsley[BigDecimal]) = {
                if (desc.realNumbersCanBeHexadecimal) noZeroHexadecimal <|> p
                else p
            }
            def addOct(p: Parsley[BigDecimal]) = {
                if (desc.realNumbersCanBeOctal) noZeroOctal <|> p
                else p
            }
            def addBin(p: Parsley[BigDecimal]) = {
                if (desc.realNumbersCanBeBinary) noZeroBinary <|> p
                else p
            }
            // this promotes sharing when the definitions would be otherwise equal
            val leadingDotAllowedDecimal = if (desc.leadingDotAllowed) decimal else ofRadix(10, digit, leadingDotAllowed = true, err.labelRealNumberEnd)
            // not even accounting for the leading and trailing dot being allowed!
            val zeroLead = '0' *> (addHex(addOct(addBin(leadingDotAllowedDecimal <|> pure(BigDecimal(0))))))
            atomic(zeroLead <|> decimal)
        }
    }

    override def decimal: Parsley[BigDecimal] = err.labelRealDecimal(_decimal)
    override def hexadecimal: Parsley[BigDecimal] = err.labelRealHexadecimal(_hexadecimal)
    override def octal: Parsley[BigDecimal] = err.labelRealOctal(_octal)
    override def binary: Parsley[BigDecimal] = err.labelRealBinary(_binary)
    override def number: Parsley[BigDecimal] = err.labelRealNumber(_number)

    private def when(b: Boolean, p: Parsley[_]): Parsley[_] = if (b) p else unit

    def leadingBreakChar(label: LabelConfig): Parsley[_] = desc.literalBreakChar match {
        case BreakCharDesc.NoBreakChar => unit
        case BreakCharDesc.Supported(breakChar, allowedAfterNonDecimalPrefix) =>
            when(allowedAfterNonDecimalPrefix, err.labelNumericBreakChar.orElse(label)(optional(breakChar)))
    }

    private val noZeroHexadecimal =
        when(desc.hexadecimalLeads.nonEmpty, oneOf(desc.hexadecimalLeads)) *>
        leadingBreakChar(err.labelRealHexadecimalEnd) *>
        ofRadix(16, hexDigit, err.labelRealHexadecimalEnd)
    private val noZeroOctal =
        when(desc.octalLeads.nonEmpty, oneOf(desc.octalLeads)) *>
        leadingBreakChar(err.labelRealOctalEnd) *>
        ofRadix(8, octDigit, err.labelRealOctalEnd)
    private val noZeroBinary =
        when(desc.binaryLeads.nonEmpty, oneOf(desc.binaryLeads)) *>
        leadingBreakChar(err.labelRealBinaryEnd) *>
        ofRadix(2, bit, err.labelRealBinaryEnd)

    // could allow integers to be parsed here according to configuration, the intOrFloat captures that case anyway
    private def ofRadix(radix: Int, digit: Parsley[Char], endLabel: LabelConfig): Parsley[BigDecimal] = {
        ofRadix(radix, digit, desc.leadingDotAllowed, endLabel)
    }
    private def ofRadix(radix: Int, digit: Parsley[Char], leadingDotAllowed: Boolean, endLabel: LabelConfig): Parsley[BigDecimal] = {
        lazy val leadingHappened = Ref.make[Boolean]
        lazy val _noDoubleDroppedZero = err.preventRealDoubleDroppedZero(leadingHappened.get)
        val expDesc = desc.exponentDescForRadix(radix)
        val whole = radix match {
            case 10 => generic.plainDecimal(desc, endLabel)
            case 16 => generic.plainHexadecimal(desc, endLabel)
            case 8 => generic.plainOctal(desc, endLabel)
            case 2 => generic.plainBinary(desc, endLabel)
        }
        val f = (d: Char, x: BigDecimal) => x/radix + d.asDigit
        def broken(c: Char) =
            lift2(f,
                endLabel(digit),
                (err.labelNumericBreakChar.orElse(endLabel)(optional(c)) *>
                 endLabel(digit)).foldRight[BigDecimal](0)(f))
        val fractional = amendThenDislodge {
            err.labelRealDot.orElse(endLabel)('.') *> {
                desc.literalBreakChar match {
                    case BreakCharDesc.NoBreakChar if desc.trailingDotAllowed     =>
                        if (!leadingDotAllowed) entrench(digit.foldRight[BigDecimal](0)(f))
                        else entrench(digit.foldRight1[BigDecimal](0)(f)) <|> _noDoubleDroppedZero *> pure[BigDecimal](0)
                    case BreakCharDesc.NoBreakChar                                => entrench(digit.foldRight1[BigDecimal](0)(f))
                    case BreakCharDesc.Supported(c, _) if desc.trailingDotAllowed =>
                        entrench(broken(c)) <|> when(leadingDotAllowed, _noDoubleDroppedZero) *> pure[BigDecimal](0)
                    case BreakCharDesc.Supported(c, _)                            => entrench(broken(c))
                }
            }
        }
        val (requiredExponent, exponent, base) = expDesc match {
            case ExponentDesc.Supported(compulsory, exp, base, sign, leadingZeros) =>
                val expErr = new ErrorConfig {
                    override def labelIntegerSignedDecimal(bits: Int) = err.labelRealExponentEnd.orElse(endLabel)
                    override def labelIntegerDecimalEnd = err.labelRealExponentEnd.orElse(endLabel)
                }
                val expIntDesc = desc.copy(positiveSign = sign, leadingZerosAllowed = leadingZeros)
                val natural = new UnsignedInteger(expIntDesc, expErr, generic)
                val integer = new SignedInteger(expIntDesc, natural, expErr)
                val exponent = err.labelRealExponent.orElse(endLabel)(oneOf(exp)) *> integer.decimal32
                if (compulsory) (exponent, exponent, base)
                else (exponent, exponent <|> pure(0), base)
            // this can't fail for non-required, it has to be the identity exponent
            case ExponentDesc.NoExponents => (empty, pure(0), 1)
        }
        val fractExponent =
                (lift2((f: BigDecimal, e: Int) => (w: BigInt) => (BigDecimal(w) + f / radix) * BigDecimal(base).pow(e),
                       fractional,
                       exponent)
            <|> requiredExponent.map(e => (w: BigInt) => BigDecimal(w) * BigDecimal(base).pow(e)))
        val configuredWhole =
            if (leadingDotAllowed) (
                    when(desc.trailingDotAllowed, leadingHappened.set(false)) *> whole
                <|> when(desc.trailingDotAllowed, leadingHappened.set(true)) *> pure(BigInt(0))
            )
            else whole
        configuredWhole <**> fractExponent
    }
}
