/*
 * Copyright 2020 Parsley Contributors <https://github.com/j-mie6/Parsley/graphs/contributors>
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package parsley.token.numeric

import parsley.Parsley, Parsley.atomic
import parsley.token.descriptions.NumericDesc
import parsley.token.errors.ErrorConfig

import org.typelevel.scalaccompat.annotation.unused

private [token] final class UnsignedCombined(@unused desc: NumericDesc, integer: IntegerParsers, rational: RealParsers, err: ErrorConfig)
    extends CombinedParsers(err) {
    override lazy val decimal: Parsley[Either[BigInt, BigDecimal]] = (atomic(rational.decimal) <+> integer.decimal).map(_.swap)
    override lazy val hexadecimal: Parsley[Either[BigInt, BigDecimal]] = (atomic(rational.hexadecimal) <+> integer.hexadecimal).map(_.swap)
    override lazy val octal: Parsley[Either[BigInt, BigDecimal]] = (atomic(rational.octal) <+> integer.octal).map(_.swap)
    override lazy val binary: Parsley[Either[BigInt, BigDecimal]] = (atomic(rational.binary) <+> integer.binary).map(_.swap)
    override lazy val number: Parsley[Either[BigInt, BigDecimal]] = (atomic(rational.number) <+> integer.number).map(_.swap)

    // FIXME: gross :( we should restructure this to be more reusable friendly
    // FIXME: This doesn't respect the number requirements on hex oct bin for both integer and rational
    // FIXME: It also doesn't account for the trailing and leading dot
    // FIXME: It also doesn't respect exponent descriptions
        /*{
        val decFractional = '.' *> digit.foldRight1[BigDecimal](0)((d, x) => x/10 + d.asDigit)
        // TODO: This integer needs to have the description altered to account for the sign presence
        val decExponent = oneOf('e', 'E') *> integer.decimal32
        val zeroPoint = (decFractional, decExponent <|> pure(0)).zipped[Either[BigInt, BigDecimal]] {
            case (f, e) => Right(f / 10 * BigDecimal(10).pow(e))
        } <|> decExponent.as(Right(BigDecimal(0)))
        val zeroLead = '0' *> (noZeroHexadecimal <|> noZeroOctal <|> noZeroBinary <|> zeroPoint <|> decimal <|> pure(Left(BigInt(0))))
        atomic(zeroLead <|> decimal)
    }*/

    // TODO: Using choice here will generate a jump table, which will be nicer for `number` (this requires enhancements to the jumptable optimisation)
    // TODO: Leave these as defs so they get inlined into number for the jumptable optimisation
    //private val noZeroHexadecimal = oneOf(desc.hexadecimalLeads) *> ofRadix(16, 2, hexDigit, oneOf('p', 'P'))
    //private val noZeroOctal = oneOf(desc.octalLeads) *> ofRadix(8, 8, octDigit, oneOf('p', 'P'))
    //private val noZeroBinary = oneOf(desc.binaryLeads) *> ofRadix(2, 2, oneOf('0', '1'), oneOf('p', 'P'))

    override protected [numeric] def bounded[T](number: Parsley[Either[BigInt, BigDecimal]], bits: Bits, radix: Int)
                                               (implicit ev: CanHold[bits.self,T]): Parsley[Either[T, BigDecimal]] = {
        err.filterIntegerOutOfBounds(min = 0, bits.upperUnsigned, radix).injectLeft.collect(number) {
            case Left(x) if x <= bits.upperUnsigned => Left(ev.fromBigInt(x))
            case Right(x) => Right(x)
        }
    }

    // This isn't quite accurate: for non decimal floats the exponent is required, but for wholes it is not
    /*private def ofRadix(radix: Int, base: Int, digit: Parsley[Char], exp: Parsley[Char]) = {
        // could allow for foldLeft and foldRight here!
        // this reuses components of natural numbers, which will prevent duplication in a larger parser
        val whole = radix match {
            case 10 => integer.plainDecimal
            case 16 => integer.plainHexadecimal
            case 8 => integer.plainOctal
            case 2 => integer.plainBinary
        }
        val fractional = '.' *> digit.foldRight1[BigDecimal](0)((d, x) => x/radix + d.asDigit)
        val exponent = exp *> integer.decimal32
        //(whole, (fractional <~> exponent) <+> fractional
        if (radix == 10) {
            (whole, option(fractional), option(exponent)).zipped[Either[BigInt, BigDecimal]] {
                case (w, None, None)       => Left(w)
                case (w, Some(f), None)    => Right(BigDecimal(w) + f / radix)
                case (w, None, Some(e))    => Right(BigDecimal(w) * BigDecimal(base).pow(e))
                case (w, Some(f), Some(e)) => Right((BigDecimal(w) + f / radix) * BigDecimal(base).pow(e))
            }
        }
        else {
            (whole, option((fractional <~> exponent) <+> exponent)).zipped[Either[BigInt, BigDecimal]] {
                case (w, None)               => Left(w)
                case (w, Some(Left((f, e)))) => Right((BigDecimal(w) + f / radix) * BigDecimal(base).pow(e))
                case (w, Some(Right(e)))     => Right(BigDecimal(w) * BigDecimal(base).pow(e))
            }
        }
    }*/
}
