/*
 * Copyright 2020 Parsley Contributors <https://github.com/j-mie6/Parsley/graphs/contributors>
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package parsley.token.numeric

import parsley.Parsley
import parsley.token.Lexeme
import parsley.token.errors.{ErrorConfig, LabelWithExplainConfig}

private [token] final class LexemeReal(real: RealParsers, lexeme: Lexeme, err: ErrorConfig) extends RealParsers(err) {
    override lazy val decimal: Parsley[BigDecimal] = lexeme(real.decimal)
    override lazy val hexadecimal: Parsley[BigDecimal] = lexeme(real.hexadecimal)
    override lazy val octal: Parsley[BigDecimal] = lexeme(real.octal)
    override lazy val binary: Parsley[BigDecimal] = lexeme(real.binary)
    override lazy val number: Parsley[BigDecimal] = lexeme(real.number)

    override protected [numeric] def _decimal: Parsley[BigDecimal] = real.decimal
    override protected [numeric] def _hexadecimal: Parsley[BigDecimal] = real.hexadecimal
    override protected [numeric] def _octal: Parsley[BigDecimal] = real.octal
    override protected [numeric] def _binary: Parsley[BigDecimal] = real.binary
    override protected [numeric] def _number: Parsley[BigDecimal] = real.number

    override protected [numeric] def ensureFloat(number: Parsley[BigDecimal], label: LabelWithExplainConfig): Parsley[Float] =
        lexeme(super.ensureFloat(number,  label))
    override protected [numeric] def ensureDouble(number: Parsley[BigDecimal], label: LabelWithExplainConfig): Parsley[Double] =
        lexeme(super.ensureDouble(number,  label))
    override protected [numeric] def ensureExactFloat(number: Parsley[BigDecimal], label: LabelWithExplainConfig): Parsley[Float] =
        lexeme(super.ensureExactFloat(number,  label))
    override protected [numeric] def ensureExactDouble(number: Parsley[BigDecimal], label: LabelWithExplainConfig): Parsley[Double] =
        lexeme(super.ensureExactDouble(number,  label))
}
