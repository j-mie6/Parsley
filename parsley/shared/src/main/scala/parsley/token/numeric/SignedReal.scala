/*
 * Copyright 2020 Parsley Contributors <https://github.com/j-mie6/Parsley/graphs/contributors>
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package parsley.token.numeric

import parsley.Parsley, Parsley.atomic
import parsley.token.descriptions.NumericDesc
import parsley.token.errors.ErrorConfig

import parsley.internal.deepembedding.Sign.DoubleType
import parsley.internal.deepembedding.singletons

private [token] final class SignedReal(desc: NumericDesc, unsigned: RealParsers, err: ErrorConfig) extends RealParsers(err) {
    private val sign = new Parsley(new singletons.Sign[DoubleType.resultType](DoubleType, desc.positiveSign))

    override lazy val _decimal: Parsley[BigDecimal] = atomic(sign <*> err.labelRealDecimalEnd(unsigned._decimal))
    override lazy val _hexadecimal: Parsley[BigDecimal] = atomic(sign <*> err.labelRealHexadecimalEnd(unsigned._hexadecimal))
    override lazy val _octal: Parsley[BigDecimal] = atomic(sign <*> err.labelRealOctalEnd(unsigned._octal))
    override lazy val _binary: Parsley[BigDecimal] = atomic(sign <*> err.labelRealBinaryEnd(unsigned._binary))
    override lazy val _number: Parsley[BigDecimal] = atomic(sign <*> err.labelRealNumberEnd(unsigned._number))

    override def decimal: Parsley[BigDecimal] = err.labelRealDecimal(_decimal)
    override def hexadecimal: Parsley[BigDecimal] = err.labelRealHexadecimal(_hexadecimal)
    override def octal: Parsley[BigDecimal] = err.labelRealOctal(_octal)
    override def binary: Parsley[BigDecimal] = err.labelRealBinary(_binary)
    override def number: Parsley[BigDecimal] = err.labelRealNumber(_number)
}
