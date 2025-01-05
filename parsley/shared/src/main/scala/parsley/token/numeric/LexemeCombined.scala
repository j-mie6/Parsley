/*
 * Copyright 2020 Parsley Contributors <https://github.com/j-mie6/Parsley/graphs/contributors>
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package parsley.token.numeric

import parsley.Parsley
import parsley.token.Lexeme
import parsley.token.errors.ErrorConfig

private [token] final class LexemeCombined(combined: CombinedParsers, lexeme: Lexeme, err: ErrorConfig) extends CombinedParsers(err) {
    override lazy val decimal: Parsley[Either[BigInt,BigDecimal]] = lexeme(combined.decimal)
    override lazy val hexadecimal: Parsley[Either[BigInt,BigDecimal]] = lexeme(combined.hexadecimal)
    override lazy val octal: Parsley[Either[BigInt,BigDecimal]] = lexeme(combined.octal)
    override lazy val binary: Parsley[Either[BigInt,BigDecimal]] = lexeme(combined.binary)
    override lazy val  number: Parsley[Either[BigInt,BigDecimal]] = lexeme(combined.number)

    override protected[numeric] def bounded[T](number: Parsley[Either[BigInt,BigDecimal]], bits: Bits, radix: Int)
                                              (implicit ev: CanHold[bits.self,T]): Parsley[Either[T, BigDecimal]] =
        lexeme(combined.bounded(number, bits, radix))

    override protected [numeric] def _decimal: Parsley[Either[BigInt,BigDecimal]] = combined.decimal
    override protected [numeric] def _hexadecimal: Parsley[Either[BigInt,BigDecimal]] = combined.hexadecimal
    override protected [numeric] def _octal: Parsley[Either[BigInt,BigDecimal]] = combined.octal
    override protected [numeric] def _binary: Parsley[Either[BigInt,BigDecimal]] = combined.binary
    override protected [numeric] def _number: Parsley[Either[BigInt,BigDecimal]] = combined.number
}
