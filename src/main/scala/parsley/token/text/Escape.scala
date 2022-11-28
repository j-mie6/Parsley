/* SPDX-FileCopyrightText: © 2022 Parsley Contributors <https://github.com/j-mie6/Parsley/graphs/contributors>
 * SPDX-License-Identifier: BSD-3-Clause
 */
package parsley.token.text

import parsley.Parsley, Parsley.{attempt, empty, pure, unit}
import parsley.character.{bit, char, digit, hexDigit, octDigit, strings}
import parsley.combinator.{choice, ensure, option}
import parsley.errors.combinator.{amend, entrench, ErrorMethods}
import parsley.implicits.zipped.Zipped3
import parsley.token.descriptions.text.{EscapeDesc, NumberOfDigits, NumericEscape}
import parsley.token.numeric

private [token] class Escape(desc: EscapeDesc) {
    // NOTE: `strings`, while nice, is not perfect as it doesn't leverage a trie-based folding
    //       on the possibilities. We'll want trie-based folding here, or at least a specialised
    //       instruction that has the trie lookup logic baked in.
    // We do need to backtrack out of this if things go wrong, it's possible another escape sequence might share a lead
    private val escMapped = {
        val (x::xs) = desc.escMap.view.map {
            case (e, c) => e -> pure(c)
        }.toList
        attempt(strings(x, xs: _*))
    }

    private def boundedChar(p: Parsley[BigInt], maxValue: Int, prefix: Option[Char], radix: Int): Parsley[Int] =
        prefix.fold(unit)(c => char(c).void) *> amend {
            val prefixString = prefix.fold("")(c => s"$c")
            entrench(p).collectMsg(n => Seq(
                if (n > maxValue) {
                    s"\\$prefixString${n.toString(radix)} is greater than the maximum character of \\$prefixString${BigInt(maxValue).toString(radix)}"
                }
                else s"illegal unicode codepoint: \\$prefixString${n.toString(radix)}")) {
                case n if n <= maxValue && Character.isValidCodePoint(n.toInt) => n.toInt
            }
        }

    // this is a really neat trick :)
    private lazy val atMostReg = parsley.registers.Reg.make[Int]
    private def atMost(n: Int, radix: Int, digit: Parsley[Char]): Parsley[BigInt] = {
        atMostReg.put(n) *> ensure(atMostReg.gets(_ > 0),
                                   digit <* atMostReg.modify(_ - 1)).foldLeft1[BigInt](0)((n, d) => n * radix + d.asDigit)
    }

    private def exactly(n: Int, full: Int, radix: Int, digit: Parsley[Char]): Parsley[BigInt] = {
        atMost(n, radix, digit) <* atMostReg.get.guardAgainst {
            case x if x > 0 => Seq(s"literal required $full digits, but only got ${full-x}")
        }
    }

    private lazy val digitsParsed = parsley.registers.Reg.make[Int]
    private def oneOfExactly(n: Int, ns: List[Int], radix: Int, digit: Parsley[Char]): Parsley[BigInt] = {
        def go(digits: Int, m: Int, ns: List[Int]): Parsley[BigInt] = ns match {
            case Nil => exactly(digits, m, radix, digit) <* digitsParsed.put(digits)
            case n :: ns  =>
                val theseDigits = exactly(digits, m, radix, digit)
                val restDigits = (
                        (attempt(go(n-m, n, ns).map(Some(_)) <* digitsParsed.modify(_ + digits)))
                    <|> (digitsParsed.put(digits) #> None)
                )
                (theseDigits, restDigits, digitsParsed.get).zipped[BigInt] {
                    case (x, None, _) => x
                    case (x, Some(y), exp) => (x * BigInt(radix).pow(exp - digits) + y) // digits is removed here, because it's been added before the get
                }
        }
        val (m :: ms) = (n :: ns).sorted // make this a precondition of the description?
        go(m, m, ms)
    }

    private def fromDesc(radix: Int, desc: NumericEscape, integer: =>Parsley[BigInt], digit: Parsley[Char]): Parsley[Int] = desc match {
        case NumericEscape.Illegal => empty
        case NumericEscape.Supported(prefix, numberOfDigits, maxValue) => numberOfDigits match {
            case NumberOfDigits.Unbounded         => boundedChar(integer, maxValue, prefix, radix)
            case NumberOfDigits.AtMost(n)         => boundedChar(atMost(n, radix, digit), maxValue, prefix, radix)
            case NumberOfDigits.Exactly(n, ns@_*) => boundedChar(oneOfExactly(n, ns.toList, radix, digit), maxValue, prefix, radix)
        }
    }

    private val decimalEscape = fromDesc(radix = 10, desc.decimalEscape, numeric.Generic.zeroAllowedDecimal, digit)
    private val hexadecimalEscape = fromDesc(radix = 16, desc.hexadecimalEscape, numeric.Generic.zeroAllowedHexadecimal, hexDigit)
    private val octalEscape = fromDesc(radix = 8, desc.octalEscape, numeric.Generic.zeroAllowedOctal, octDigit)
    private val binaryEscape = fromDesc(radix = 2, desc.binaryEscape, numeric.Generic.zeroAllowedBinary, bit)
    private val numericEscape = decimalEscape <|> hexadecimalEscape <|> octalEscape <|> binaryEscape
    val escapeCode = (escMapped <|> numericEscape).label("end of escape sequence")
    val escapeChar = char(desc.escBegin) *> escapeCode
}
