/*
 * Copyright 2020 Parsley Contributors <https://github.com/j-mie6/Parsley/graphs/contributors>
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package parsley.token.text

import parsley.Parsley, Parsley.empty
import parsley.character.char
import parsley.token.descriptions.{EscapeDesc, NumberOfDigits, NumericEscape}
import parsley.token.errors.{ErrorConfig, NotConfigured}
import parsley.token.numeric

import parsley.internal.deepembedding.singletons.token

/** This class' implementation has been optimised for performance. If you've stumbled on
  * this file hoping to learn about how it works, this is the wrong place. The original,
  * unoptimised implementation is preserved for testing in the corresponding place in the
  * "test" folder. This is because a requirement of optimisation is that the semantics can
  * still be implemented by plain combinators. Go look there!
  */
private [token] class Escape(desc: EscapeDesc, err: ErrorConfig, generic: numeric.Generic) {
    private val escMapped = if (desc.escTrie.isEmpty) empty else new Parsley(new token.EscapeMapped(desc.escTrie, desc.escs))

    private def boundedChar(p: Parsley[BigInt], maxValue: Int, prefix: Option[Char], radix: Int) = err.labelEscapeNumeric(radix) {
        val numericTail = err.filterEscapeCharNumericSequenceIllegal(maxValue, radix).collect(p) {
            case n if n <= maxValue && Character.isValidCodePoint(n.toInt) => n.toInt
        }
        prefix match {
            case None => numericTail
            case Some(c) => char(c) *> err.labelEscapeNumericEnd(c, radix)(numericTail)
        }
    }

    private def atMost(n: Int, radix: Int): Parsley[BigInt] = new Parsley(new token.EscapeAtMost(n, radix))
    private def oneOfExactly(n: Int, ns: List[Int], radix: Int): Parsley[BigInt] = {
        new Parsley(new token.EscapeOneOfExactly(radix, (n :: ns).sorted, err.filterEscapeCharRequiresExactDigits(radix, (n :: ns).sorted)))
    }

    private def fromDesc(radix: Int, desc: NumericEscape, integer: =>Parsley[BigInt]): Parsley[Int] = desc match {
        case NumericEscape.Illegal => empty
        case NumericEscape.Supported(prefix, numberOfDigits, maxValue) => numberOfDigits match {
            case NumberOfDigits.Unbounded         => boundedChar(integer, maxValue, prefix, radix)
            case NumberOfDigits.AtMost(n)         => boundedChar(atMost(n, radix), maxValue, prefix, radix)
            case NumberOfDigits.Exactly(n, ns@_*) => boundedChar(oneOfExactly(n, ns.toList, radix), maxValue, prefix, radix)
        }
    }

    private val decimalEscape = fromDesc(radix = 10, desc.decimalEscape, generic.zeroAllowedDecimal(NotConfigured))
    private val hexadecimalEscape = fromDesc(radix = 16, desc.hexadecimalEscape, generic.zeroAllowedHexadecimal(NotConfigured))
    private val octalEscape = fromDesc(radix = 8, desc.octalEscape, generic.zeroAllowedOctal(NotConfigured))
    private val binaryEscape = fromDesc(radix = 2, desc.binaryEscape, generic.zeroAllowedBinary(NotConfigured))
    private val numericEscape = decimalEscape <|> hexadecimalEscape <|> octalEscape <|> binaryEscape
    val escapeCode = err.labelEscapeEnd(escMapped <|> numericEscape)
    val escapeBegin = err.labelEscapeSequence(char(desc.escBegin)).void
    // do not make atomic
    val escapeChar = escapeBegin *> escapeCode
}
