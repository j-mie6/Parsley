/*
 * Copyright 2020 Parsley Contributors <https://github.com/j-mie6/Parsley/graphs/contributors>
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package parsley.token.numeric

import parsley.Parsley, Parsley.pure
import parsley.character, character.{isHexDigit, isOctDigit, satisfy}
import parsley.combinator.optional
import parsley.errors.combinator.ErrorMethods
import parsley.syntax.character.charLift
import parsley.token.descriptions.{BreakCharDesc, NumericDesc}
import parsley.token.errors.{ErrorConfig, LabelConfig}

private [token] class Generic(err: ErrorConfig) {
    private def ofRadix(radix: Int, digit: Parsley[Char], label: LabelConfig): Parsley[BigInt] = ofRadix(radix, digit, digit, label)
    private def ofRadix(radix: Int, startDigit: Parsley[Char], digit: Parsley[Char], label: LabelConfig): Parsley[BigInt] = {
        val pf = pure((x: BigInt, d: Char) => x*radix + d.asDigit)
        val trailingDigit = label(digit)
        parsley.expr.infix.secretLeft1(startDigit.map(d => BigInt(d.asDigit)), trailingDigit, pf, null) //FIXME: thread name?
    }

    private def ofRadix(radix: Int, digit: Parsley[Char], breakChar: Char, label: LabelConfig): Parsley[BigInt] = {
        ofRadix(radix, digit, digit, breakChar, label)
    }
    private def ofRadix(radix: Int, startDigit: Parsley[Char], digit: Parsley[Char], breakChar: Char, label: LabelConfig): Parsley[BigInt] = {
        val pf = pure((x: BigInt, d: Char) => x*radix + d.asDigit)
        val trailingDigit =
            optional(err.labelNumericBreakChar.orElse(label)(breakChar)) *>
            label(digit)
        parsley.expr.infix.secretLeft1(startDigit.map(d => BigInt(d.asDigit)), trailingDigit, pf, null) //FIXME: thread name?
    }

    // Digits
    private def nonZeroDigit = satisfy(c => c.isDigit && c != '0').label("digit")
    private def nonZeroHexDigit = satisfy(c => isHexDigit(c) && c != '0').label("hexadecimal digit")
    private def nonZeroOctDigit = satisfy(c => isOctDigit(c) && c != '0').label("octal digit")
    private def nonZeroBit = '1'.label("bit")
    // why secret? so that the above digits can be marked as digits without "non-zero or zero digit" nonsense
    private def secretZero = '0'.hide.as(BigInt(0))

    private def digit = character.digit
    private def hexDigit = character.hexDigit
    private def octDigit = character.octDigit
    private def bit = character.bit

    def zeroAllowedDecimal(endLabel: LabelConfig): Parsley[BigInt] = ofRadix(10, digit, endLabel)
    def zeroAllowedHexadecimal(endLabel: LabelConfig): Parsley[BigInt] = ofRadix(16, hexDigit, endLabel)
    def zeroAllowedOctal(endLabel: LabelConfig): Parsley[BigInt] = ofRadix(8, octDigit, endLabel)
    def zeroAllowedBinary(endLabel: LabelConfig): Parsley[BigInt] = ofRadix(2, bit, endLabel)

    def zeroNotAllowedDecimal(endLabel: LabelConfig): Parsley[BigInt] = ofRadix(10, nonZeroDigit, digit, endLabel) <|> secretZero
    def zeroNotAllowedHexadecimal(endLabel: LabelConfig): Parsley[BigInt] = ofRadix(16, nonZeroHexDigit, hexDigit, endLabel) <|> secretZero
    def zeroNotAllowedOctal(endLabel: LabelConfig): Parsley[BigInt] = ofRadix(8, nonZeroOctDigit, octDigit, endLabel) <|> secretZero
    def zeroNotAllowedBinary(endLabel: LabelConfig): Parsley[BigInt] = ofRadix(2, nonZeroBit, bit, endLabel) <|> secretZero

    def plainDecimal(desc: NumericDesc, endLabel: LabelConfig): Parsley[BigInt] = plainDecimal(desc.leadingZerosAllowed, desc.literalBreakChar, endLabel)
    private def plainDecimal(leadingZerosAllowed: Boolean, literalBreakChar: BreakCharDesc, endLabel: LabelConfig): Parsley[BigInt] = literalBreakChar match {
        case BreakCharDesc.NoBreakChar if leadingZerosAllowed     => zeroAllowedDecimal(endLabel)
        case BreakCharDesc.NoBreakChar                            => zeroNotAllowedDecimal(endLabel)
        case BreakCharDesc.Supported(c, _) if leadingZerosAllowed => ofRadix(10, digit, c, endLabel)
        case BreakCharDesc.Supported(c, _)                        => ofRadix(10, nonZeroDigit, digit, c, endLabel) <|> secretZero
    }

    def plainHexadecimal(desc: NumericDesc, endLabel: LabelConfig): Parsley[BigInt] = {
        plainHexadecimal(desc.leadingZerosAllowed, desc.literalBreakChar, endLabel)
    }
    private def plainHexadecimal(leadingZerosAllowed: Boolean, literalBreakChar: BreakCharDesc, endLabel: LabelConfig): Parsley[BigInt] = {
        literalBreakChar match {
            case BreakCharDesc.NoBreakChar if leadingZerosAllowed     => zeroAllowedHexadecimal(endLabel)
            case BreakCharDesc.NoBreakChar                            => zeroNotAllowedHexadecimal(endLabel)
            case BreakCharDesc.Supported(c, _) if leadingZerosAllowed => ofRadix(16, hexDigit, c, endLabel)
            case BreakCharDesc.Supported(c, _)                        => ofRadix(16, nonZeroHexDigit, hexDigit, c, endLabel) <|> secretZero
        }
    }

    def plainOctal(desc: NumericDesc, endLabel: LabelConfig): Parsley[BigInt] = plainOctal(desc.leadingZerosAllowed, desc.literalBreakChar, endLabel)
    private def plainOctal(leadingZerosAllowed: Boolean, literalBreakChar: BreakCharDesc, endLabel: LabelConfig): Parsley[BigInt] = literalBreakChar match {
        case BreakCharDesc.NoBreakChar if leadingZerosAllowed     => zeroAllowedOctal(endLabel)
        case BreakCharDesc.NoBreakChar                            => zeroNotAllowedOctal(endLabel)
        case BreakCharDesc.Supported(c, _) if leadingZerosAllowed => ofRadix(8, octDigit, c, endLabel)
        case BreakCharDesc.Supported(c, _)                        => ofRadix(8, nonZeroOctDigit, octDigit, c, endLabel) <|> secretZero
    }

    def plainBinary(desc: NumericDesc, endLabel: LabelConfig): Parsley[BigInt] = plainBinary(desc.leadingZerosAllowed, desc.literalBreakChar, endLabel)
    private def plainBinary(leadingZerosAllowed: Boolean, literalBreakChar: BreakCharDesc, endLabel: LabelConfig): Parsley[BigInt] = literalBreakChar match {
        case BreakCharDesc.NoBreakChar if leadingZerosAllowed     => zeroAllowedBinary(endLabel)
        case BreakCharDesc.NoBreakChar                            => zeroNotAllowedBinary(endLabel)
        case BreakCharDesc.Supported(c, _) if leadingZerosAllowed => ofRadix(2, bit, c, endLabel)
        case BreakCharDesc.Supported(c, _)                        => ofRadix(2, nonZeroBit, bit, c, endLabel) <|> secretZero
    }
}
