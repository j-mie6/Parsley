/* SPDX-FileCopyrightText: © 2022 Parsley Contributors <https://github.com/j-mie6/Parsley/graphs/contributors>
 * SPDX-License-Identifier: BSD-3-Clause
 */
package parsley.token.numeric

import parsley.Parsley, Parsley.{attempt, pure}
import parsley.character.{digit, hexDigit, octDigit, oneOf}
import parsley.combinator.option
import parsley.errors.combinator.{amend, entrench, ErrorMethods}
import parsley.implicits.character.charLift
import parsley.implicits.zipped.{Zipped2, Zipped3}
import parsley.token.{Bits, CanHold}
import parsley.token.descriptions.NumericDesc

private [token] final class UnsignedCombined(integer: Integer, desc: NumericDesc) extends Combined {
    override lazy val decimal: Parsley[Either[BigInt, BigDecimal]] = ofRadix(10, 10, digit, oneOf('e', 'E'))
    override lazy val hexadecimal: Parsley[Either[BigInt, BigDecimal]] = attempt('0' *> noZeroHexadecimal)
    override lazy val octal: Parsley[Either[BigInt, BigDecimal]] = attempt('0' *> noZeroOctal)
    override lazy val binary: Parsley[Either[BigInt, BigDecimal]] = attempt('0' *> noZeroBinary)
    // FIXME: gross :( we should restructure this to be more reusable friendly
    override lazy val number: Parsley[Either[BigInt, BigDecimal]] = {
        val decFractional = '.' *> digit.foldRight1[BigDecimal](0)((d, x) => x/10 + d.asDigit)
        val decExponent = oneOf('e', 'E') *> integer.decimal32
        val zeroPoint = (decFractional, decExponent <|> pure(0)).zipped[Either[BigInt, BigDecimal]] {
            case (f, e) => Right(f / 10 * BigDecimal(10).pow(e))
        } <|> decExponent #> Right(BigDecimal(0))
        val zeroLead = '0' *> (noZeroHexadecimal <|> noZeroOctal <|> noZeroBinary <|> zeroPoint <|> decimal <|> pure(Left(BigInt(0))))
        attempt(zeroLead <|> decimal)
    }

    private lazy val noZeroHexadecimal = oneOf('x', 'X') *> ofRadix(16, 2, hexDigit, oneOf('p', 'P'))
    private lazy val noZeroOctal = oneOf('o', 'O') *> ofRadix(8, 8, octDigit, oneOf('p', 'P'))
    private lazy val noZeroBinary = oneOf('b', 'B') *> ofRadix(2, 2, oneOf('0', '1'), oneOf('p', 'P'))

    // TODO: render in the "native" radix
    override protected [numeric] def bounded[T](number: Parsley[Either[BigInt, BigDecimal]], bits: Bits, radix: Int)
                                               (implicit ev: CanHold[bits.self,T]): Parsley[Either[T, BigDecimal]] = amend {
        entrench(number).collectMsg(x => Seq(s"literal ${x.asInstanceOf[Left[BigInt, Nothing]].value} is larger than the max value of ${bits.upperUnsigned}")) {
            case Left(x) if x <= bits.upperUnsigned => Left(ev.fromBigInt(x))
            case Right(x) => Right(x)
        }
    }

    // This isn't quite accurate: for non decimal floats the exponent is required, but for wholes it is not
    private def ofRadix(radix: Int, base: Int, digit: Parsley[Char], exp: Parsley[Char]) = {
        // could allow for foldLeft and foldRight here!
        val whole = digit.foldLeft1[BigInt](0)((x, d) => x*radix + d.asDigit)
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
    }
}
