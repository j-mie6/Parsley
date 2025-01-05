/*
 * Copyright 2020 Parsley Contributors <https://github.com/j-mie6/Parsley/graphs/contributors>
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package parsley.token.numeric

import parsley.Parsley
import parsley.token.Lexeme
import parsley.token.errors.{ErrorConfig, LabelWithExplainConfig}

private [token] final class LexemeInteger(integer: IntegerParsers, lexeme: Lexeme) extends IntegerParsers(integer.desc) {
    override lazy val decimal: Parsley[BigInt] = lexeme(integer.decimal)
    override lazy val hexadecimal: Parsley[BigInt] = lexeme(integer.hexadecimal)
    override lazy val octal: Parsley[BigInt] = lexeme(integer.octal)
    override lazy val binary: Parsley[BigInt] = lexeme(integer.binary)
    override lazy val  number: Parsley[BigInt] = lexeme(integer.number)

    override protected[numeric] def bounded[T](number: Parsley[BigInt], bits: Bits, radix: Int, label: (ErrorConfig, Boolean) => LabelWithExplainConfig)
                                              (implicit ev: CanHold[bits.self,T]): Parsley[T] =
        lexeme(integer.bounded(number, bits, radix, label))

    override protected [numeric] def _decimal: Parsley[BigInt] = integer.decimal
    override protected [numeric] def _hexadecimal: Parsley[BigInt] = integer.hexadecimal
    override protected [numeric] def _octal: Parsley[BigInt] = integer.octal
    override protected [numeric] def _binary: Parsley[BigInt] = integer.binary
    override protected [numeric] def _number: Parsley[BigInt] = integer.number
}
