/*
 * Copyright 2020 Parsley Contributors <https://github.com/j-mie6/Parsley/graphs/contributors>
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package parsley.token.numeric

import parsley.Parsley
import parsley.token.errors.ErrorConfig

/** This class defines a uniform interface for defining parsers for mixed kind
  * numeric literals, independent of how whitespace should be handled after the literal
  * or whether the literal should allow for negative numbers.
  *
  * @since 4.0.0
  * @note implementations of this class found within `Lexer` may employ sharing
  *       and refine the non-final `def`s in this class into `val` or `lazy val` when overriding.
  *
  * @define disclaimer
  *   the exact behaviour of this parser is decided by the implementations given in
  *   `Lexer`, which will depend on user-defined configuration. Please see the
  *   relevant documentation of these specific objects.
  *
  * @define base1
  *   This parser will parse either an integer or a real
  *
  * @define base2
  *   handling any ambiguity with the prefixes
  *
  * @define bounded
  *   Additionally, the type is further constrained (see the corresponding parsers).
  *
  * @define multibase
  *   Depending on the configuration this may be able to handle different bases for each type of number.
  */
abstract class CombinedParsers private[numeric] (err: ErrorConfig) { // scalastyle:ignore number.of.methods
    /** $base1 decimal number, $base2.
      *
      * @since 4.0.0
      * @note $disclaimer
      * @see [[IntegerParsers.decimal `IntegerParsers.decimal`]] and [[RealParsers.decimal `RealParsers.decimal`]]
      */
    def decimal: Parsley[Either[BigInt, BigDecimal]]
    /** $base1 hexadecimal number, $base2.
      *
      * @since 4.0.0
      * @note $disclaimer
      * @see [[IntegerParsers.hexadecimal `IntegerParsers.hexadecimal`]] and [[RealParsers.hexadecimal `RealParsers.hexadecimal`]]
      */
    def hexadecimal: Parsley[Either[BigInt, BigDecimal]]
    /** $base1 octal number, $base2.
      *
      * @since 4.0.0
      * @note $disclaimer
      * @see [[IntegerParsers.octal `IntegerParsers.octal`]] and [[RealParsers.octal `RealParsers.octal`]]
      */
    def octal: Parsley[Either[BigInt, BigDecimal]]
    /** $base1 binary number, $base2.
      *
      * @since 4.0.0
      * @note $disclaimer
      * @see [[IntegerParsers.binary `IntegerParsers.binary`]] and [[RealParsers.binary `RealParsers.binary`]]
      */
    def binary: Parsley[Either[BigInt, BigDecimal]]
    /** $base1 number, $base2. $multibase
      *
      * @since 4.0.0
      * @note $disclaimer
      * @see [[IntegerParsers.number `IntegerParsers.number`]] and [[RealParsers.number `RealParsers.number`]]
      */
    def number: Parsley[Either[BigInt, BigDecimal]]

    // $COVERAGE-OFF$
    // It's not so important these are tested, they are just wrappers around the bottoms ones
    /** $base1 number, $base2. $bounded
      *
      * @since 4.0.0
      * @note $disclaimer
      * @see [[IntegerParsers.number8 `IntegerParsers.number8`]] and [[RealParsers.number `RealParsers.number`]]
      */
    @inline final def number8[T: CanHold.can_hold_8_bits]: Parsley[Either[T, BigDecimal]] = numberBounded(_8)
    /** $base1 decimal number, $base2. $bounded
      *
      * @since 4.0.0
      * @note $disclaimer
      * @see [[IntegerParsers.decimal8 `IntegerParsers.decimal8`]] and [[RealParsers.decimal `RealParsers.decimal`]]
      */
    @inline final def decimal8[T: CanHold.can_hold_8_bits]: Parsley[Either[T, BigDecimal]] = decimalBounded(_8)
    /** $base1 hexadecimal number, $base2. $bounded
      *
      * @since 4.0.0
      * @note $disclaimer
      * @see [[IntegerParsers.hexadecimal8 `IntegerParsers.hexadecimal8`]] and [[RealParsers.hexadecimal `RealParsers.hexadecimal`]]
      */
    @inline final def hexadecimal8[T: CanHold.can_hold_8_bits]: Parsley[Either[T, BigDecimal]] = hexadecimalBounded(_8)
    /** $base1 octal number, $base2. $bounded
      *
      * @since 4.0.0
      * @note $disclaimer
      * @see [[IntegerParsers.octal8 `IntegerParsers.octal8`]] and [[RealParsers.octal `RealParsers.octal`]]
      */
    @inline final def octal8[T: CanHold.can_hold_8_bits]: Parsley[Either[T, BigDecimal]] = octalBounded(_8)
    /** $base1 binary number, $base2. $bounded
      *
      * @since 4.0.0
      * @note $disclaimer
      * @see [[IntegerParsers.binary8 `IntegerParsers.binary8`]] and [[RealParsers.binary `RealParsers.binary`]]
      */
    @inline final def binary8[T: CanHold.can_hold_8_bits]: Parsley[Either[T, BigDecimal]] = binaryBounded(_8)

    /** $base1 number, $base2. $bounded
      *
      * @since 4.0.0
      * @note $disclaimer
      * @see [[IntegerParsers.number8 `IntegerParsers.number8`]] and [[RealParsers.float `RealParsers.float`]]
      */
    @inline final def number8Float[T: CanHold.can_hold_8_bits]: Parsley[Either[T, Float]] = ensureFloat(numberBounded(_8))
    /** $base1 decimal number, $base2. $bounded
      *
      * @since 4.0.0
      * @note $disclaimer
      * @see [[IntegerParsers.decimal8 `IntegerParsers.decimal8`]] and [[RealParsers.decimalFloat `RealParsers.decimalFloat`]]
      */
    @inline final def decimal8Float[T: CanHold.can_hold_8_bits]: Parsley[Either[T, Float]] = ensureFloat(decimalBounded(_8))
    /** $base1 hexadecimal number, $base2. $bounded
      *
      * @since 4.0.0
      * @note $disclaimer
      * @see [[IntegerParsers.hexadecimal8 `IntegerParsers.hexadecimal8`]] and [[RealParsers.hexadecimalFloat `RealParsers.hexadecimalFloat`]]
      */
    @inline final def hexadecimal8Float[T: CanHold.can_hold_8_bits]: Parsley[Either[T, Float]] = ensureFloat(hexadecimalBounded(_8))
    /** $base1 octal number, $base2. $bounded
      *
      * @since 4.0.0
      * @note $disclaimer
      * @see [[IntegerParsers.octal8 `IntegerParsers.octal8`]] and [[RealParsers.octalFloat `RealParsers.octalFloat`]]
      */
    @inline final def octal8Float[T: CanHold.can_hold_8_bits]: Parsley[Either[T, Float]] = ensureFloat(octalBounded(_8))
    /** $base1 binary number, $base2. $bounded
      *
      * @since 4.0.0
      * @note $disclaimer
      * @see [[IntegerParsers.binary8 `IntegerParsers.binary8`]] and [[RealParsers.binaryFloat `RealParsers.binaryFloat`]]
      */
    @inline final def binary8Float[T: CanHold.can_hold_8_bits]: Parsley[Either[T, Float]] = ensureFloat(binaryBounded(_8))

    /** $base1 number, $base2. $bounded
      *
      * @since 4.0.0
      * @note $disclaimer
      * @see [[IntegerParsers.number8 `IntegerParsers.number8`]] and [[RealParsers.double `RealParsers.double`]]
      */
    @inline final def number8Double[T: CanHold.can_hold_8_bits]: Parsley[Either[T, Double]] = ensureDouble(numberBounded(_8))
    /** $base1 decimal number, $base2. $bounded
      *
      * @since 4.0.0
      * @note $disclaimer
      * @see [[IntegerParsers.decimal8 `IntegerParsers.decimal8`]] and [[RealParsers.decimalDouble `RealParsers.decimalDouble`]]
      */
    @inline final def decimal8Double[T: CanHold.can_hold_8_bits]: Parsley[Either[T, Double]] = ensureDouble(decimalBounded(_8))
    /** $base1 hexadecimal number, $base2. $bounded
      *
      * @since 4.0.0
      * @note $disclaimer
      * @see [[IntegerParsers.hexadecimal8 `IntegerParsers.hexadecimal8`]] and [[RealParsers.hexadecimalDouble `RealParsers.hexadecimalDouble`]]
      */
    @inline final def hexadecimal8Double[T: CanHold.can_hold_8_bits]: Parsley[Either[T, Double]] = ensureDouble(hexadecimalBounded(_8))
    /** $base1 octal number, $base2. $bounded
      *
      * @since 4.0.0
      * @note $disclaimer
      * @see [[IntegerParsers.octal8 `IntegerParsers.octal8`]] and [[RealParsers.octalDouble `RealParsers.octalDouble`]]
      */
    @inline final def octal8Double[T: CanHold.can_hold_8_bits]: Parsley[Either[T, Double]] = ensureDouble(octalBounded(_8))
    /** $base1 binary number, $base2. $bounded
      *
      * @since 4.0.0
      * @note $disclaimer
      * @see [[IntegerParsers.binary8 `IntegerParsers.binary8`]] and [[RealParsers.binaryDouble `RealParsers.binaryDouble`]]
      */
    @inline final def binary8Double[T: CanHold.can_hold_8_bits]: Parsley[Either[T, Double]] = ensureDouble(binaryBounded(_8))

    /** $base1 number, $base2. $bounded
      *
      * @since 4.0.0
      * @note $disclaimer
      * @see [[IntegerParsers.number16 `IntegerParsers.number8`]] and [[RealParsers.number `RealParsers.number`]]
      */
    @inline final def number16[T: CanHold.can_hold_16_bits]: Parsley[Either[T, BigDecimal]] = numberBounded(_16)
    /** $base1 decimal number, $base2. $bounded
      *
      * @since 4.0.0
      * @note $disclaimer
      * @see [[IntegerParsers.decimal16 `IntegerParsers.decimal16`]] and [[RealParsers.decimal `RealParsers.decimal`]]
      */
    @inline final def decimal16[T: CanHold.can_hold_16_bits]: Parsley[Either[T, BigDecimal]] = decimalBounded(_16)
    /** $base1 hexadecimal number, $base2. $bounded
      *
      * @since 4.0.0
      * @note $disclaimer
      * @see [[IntegerParsers.hexadecimal16 `IntegerParsers.hexadecimal16`]] and [[RealParsers.hexadecimal `RealParsers.hexadecimal`]]
      */
    @inline final def hexadecimal16[T: CanHold.can_hold_16_bits]: Parsley[Either[T, BigDecimal]] = hexadecimalBounded(_16)
    /** $base1 octal number, $base2. $bounded
      *
      * @since 4.0.0
      * @note $disclaimer
      * @see [[IntegerParsers.octal16 `IntegerParsers.octal16`]] and [[RealParsers.octal `RealParsers.octal`]]
      */
    @inline final def octal16[T: CanHold.can_hold_16_bits]: Parsley[Either[T, BigDecimal]] = octalBounded(_16)
    /** $base1 binary number, $base2. $bounded
      *
      * @since 4.0.0
      * @note $disclaimer
      * @see [[IntegerParsers.binary16 `IntegerParsers.binary16`]] and [[RealParsers.binary `RealParsers.binary`]]
      */
    @inline final def binary16[T: CanHold.can_hold_16_bits]: Parsley[Either[T, BigDecimal]] = binaryBounded(_16)

    /** $base1 number, $base2. $bounded
      *
      * @since 4.0.0
      * @note $disclaimer
      * @see [[IntegerParsers.number16 `IntegerParsers.number16`]] and [[RealParsers.float `RealParsers.float`]]
      */
    @inline final def number16Float[T: CanHold.can_hold_16_bits]: Parsley[Either[T, Float]] = ensureFloat(numberBounded(_16))
    /** $base1 decimal number, $base2. $bounded
      *
      * @since 4.0.0
      * @note $disclaimer
      * @see [[IntegerParsers.decimal16 `IntegerParsers.decimal16`]] and [[RealParsers.decimalFloat `RealParsers.decimalFloat`]]
      */
    @inline final def decimal16Float[T: CanHold.can_hold_16_bits]: Parsley[Either[T, Float]] = ensureFloat(decimalBounded(_16))
    /** $base1 hexadecimal number, $base2. $bounded
      *
      * @since 4.0.0
      * @note $disclaimer
      * @see [[IntegerParsers.hexadecimal16 `IntegerParsers.hexadecimal16`]] and [[RealParsers.hexadecimalFloat `RealParsers.hexadecimalFloat`]]
      */
    @inline final def hexadecimal16Float[T: CanHold.can_hold_16_bits]: Parsley[Either[T, Float]] = ensureFloat(hexadecimalBounded(_16))
    /** $base1 octal number, $base2. $bounded
      *
      * @since 4.0.0
      * @note $disclaimer
      * @see [[IntegerParsers.octal16 `IntegerParsers.octal16`]] and [[RealParsers.octalFloat `RealParsers.octalFloat`]]
      */
    @inline final def octal16Float[T: CanHold.can_hold_16_bits]: Parsley[Either[T, Float]] = ensureFloat(octalBounded(_16))
    /** $base1 binary number, $base2. $bounded
      *
      * @since 4.0.0
      * @note $disclaimer
      * @see [[IntegerParsers.binary16 `IntegerParsers.binary16`]] and [[RealParsers.binaryFloat `RealParsers.binaryFloat`]]
      */
    @inline final def binary16Float[T: CanHold.can_hold_16_bits]: Parsley[Either[T, Float]] = ensureFloat(binaryBounded(_16))

    /** $base1 number, $base2. $bounded
      *
      * @since 4.0.0
      * @note $disclaimer
      * @see [[IntegerParsers.number16 `IntegerParsers.number16`]] and [[RealParsers.double `RealParsers.double`]]
      */
    @inline final def number16Double[T: CanHold.can_hold_16_bits]: Parsley[Either[T, Double]] = ensureDouble(numberBounded(_16))
    /** $base1 decimal number, $base2. $bounded
      *
      * @since 4.0.0
      * @note $disclaimer
      * @see [[IntegerParsers.decimal16 `IntegerParsers.decimal16`]] and [[RealParsers.decimalDouble `RealParsers.decimalDouble`]]
      */
    @inline final def decimal16Double[T: CanHold.can_hold_16_bits]: Parsley[Either[T, Double]] = ensureDouble(decimalBounded(_16))
    /** $base1 hexadecimal number, $base2. $bounded
      *
      * @since 4.0.0
      * @note $disclaimer
      * @see [[IntegerParsers.hexadecimal16 `IntegerParsers.hexadecimal16`]] and [[RealParsers.hexadecimalDouble `RealParsers.hexadecimalDouble`]]
      */
    @inline final def hexadecimal16Double[T: CanHold.can_hold_16_bits]: Parsley[Either[T, Double]] = ensureDouble(hexadecimalBounded(_16))
    /** $base1 octal number, $base2. $bounded
      *
      * @since 4.0.0
      * @note $disclaimer
      * @see [[IntegerParsers.octal16 `IntegerParsers.octal16`]] and [[RealParsers.octalDouble `RealParsers.octalDouble`]]
      */
    @inline final def octal16Double[T: CanHold.can_hold_16_bits]: Parsley[Either[T, Double]] = ensureDouble(octalBounded(_16))
    /** $base1 binary number, $base2. $bounded
      *
      * @since 4.0.0
      * @note $disclaimer
      * @see [[IntegerParsers.binary16 `IntegerParsers.binary16`]] and [[RealParsers.binaryDouble `RealParsers.binaryDouble`]]
      */
    @inline final def binary16Double[T: CanHold.can_hold_16_bits]: Parsley[Either[T, Double]] = ensureDouble(binaryBounded(_16))

    /** $base1 number, $base2. $bounded
      *
      * @since 4.0.0
      * @note $disclaimer
      * @see [[IntegerParsers.number32 `IntegerParsers.number32`]] and [[RealParsers.number `RealParsers.number`]]
      */
    @inline final def number32[T: CanHold.can_hold_32_bits]: Parsley[Either[T, BigDecimal]] = numberBounded(_32)
    /** $base1 decimal number, $base2. $bounded
      *
      * @since 4.0.0
      * @note $disclaimer
      * @see [[IntegerParsers.decimal32 `IntegerParsers.decimal32`]] and [[RealParsers.decimal `RealParsers.decimal`]]
      */
    @inline final def decimal32[T: CanHold.can_hold_32_bits]: Parsley[Either[T, BigDecimal]] = decimalBounded(_32)
    /** $base1 hexadecimal number, $base2. $bounded
      *
      * @since 4.0.0
      * @note $disclaimer
      * @see [[IntegerParsers.hexadecimal32 `IntegerParsers.hexadecimal32`]] and [[RealParsers.hexadecimal `RealParsers.hexadecimal`]]
      */
    @inline final def hexadecimal32[T: CanHold.can_hold_32_bits]: Parsley[Either[T, BigDecimal]] = hexadecimalBounded(_32)
    /** $base1 octal number, $base2. $bounded
      *
      * @since 4.0.0
      * @note $disclaimer
      * @see [[IntegerParsers.octal32 `IntegerParsers.octal32`]] and [[RealParsers.octal `RealParsers.octal`]]
      */
    @inline final def octal32[T: CanHold.can_hold_32_bits]: Parsley[Either[T, BigDecimal]] = octalBounded(_32)
    /** $base1 binary number, $base2. $bounded
      *
      * @since 4.0.0
      * @note $disclaimer
      * @see [[IntegerParsers.binary32 `IntegerParsers.binary32`]] and [[RealParsers.binary `RealParsers.binary`]]
      */
    @inline final def binary32[T: CanHold.can_hold_32_bits]: Parsley[Either[T, BigDecimal]] = binaryBounded(_32)

    /** $base1 number, $base2. $bounded
      *
      * @since 4.0.0
      * @note $disclaimer
      * @see [[IntegerParsers.number8 `IntegerParsers.number32`]] and [[RealParsers.float `RealParsers.float`]]
      */
    @inline final def number32Float[T: CanHold.can_hold_32_bits]: Parsley[Either[T, Float]] = ensureFloat(numberBounded(_32))
    /** $base1 decimal number, $base2. $bounded
      *
      * @since 4.0.0
      * @note $disclaimer
      * @see [[IntegerParsers.decimal32 `IntegerParsers.decimal32`]] and [[RealParsers.decimalFloat `RealParsers.decimalFloat`]]
      */
    @inline final def decimal32Float[T: CanHold.can_hold_32_bits]: Parsley[Either[T, Float]] = ensureFloat(decimalBounded(_32))
    /** $base1 hexadecimal number, $base2. $bounded
      *
      * @since 4.0.0
      * @note $disclaimer
      * @see [[IntegerParsers.hexadecimal32 `IntegerParsers.hexadecimal32`]] and [[RealParsers.hexadecimalFloat `RealParsers.hexadecimalFloat`]]
      */
    @inline final def hexadecimal32Float[T: CanHold.can_hold_32_bits]: Parsley[Either[T, Float]] = ensureFloat(hexadecimalBounded(_32))
    /** $base1 octal number, $base2. $bounded
      *
      * @since 4.0.0
      * @note $disclaimer
      * @see [[IntegerParsers.octal32 `IntegerParsers.octal32`]] and [[RealParsers.octalFloat `RealParsers.octalFloat`]]
      */
    @inline final def octal32Float[T: CanHold.can_hold_32_bits]: Parsley[Either[T, Float]] = ensureFloat(octalBounded(_32))
    /** $base1 binary number, $base2. $bounded
      *
      * @since 4.0.0
      * @note $disclaimer
      * @see [[IntegerParsers.binary32 `IntegerParsers.binary32`]] and [[RealParsers.binaryFloat `RealParsers.binaryFloat`]]
      */
    @inline final def binary32Float[T: CanHold.can_hold_32_bits]: Parsley[Either[T, Float]] = ensureFloat(binaryBounded(_32))

    /** $base1 number, $base2. $bounded
      *
      * @since 4.0.0
      * @note $disclaimer
      * @see [[IntegerParsers.number32 `IntegerParsers.number32`]] and [[RealParsers.double `RealParsers.double`]]
      */
    @inline final def number32Double[T: CanHold.can_hold_32_bits]: Parsley[Either[T, Double]] = ensureDouble(numberBounded(_32))
    /** $base1 decimal number, $base2. $bounded
      *
      * @since 4.0.0
      * @note $disclaimer
      * @see [[IntegerParsers.decimal32 `IntegerParsers.decimal32`]] and [[RealParsers.decimalDouble `RealParsers.decimalDouble`]]
      */
    @inline final def decimal32Double[T: CanHold.can_hold_32_bits]: Parsley[Either[T, Double]] = ensureDouble(decimalBounded(_32))
    /** $base1 hexadecimal number, $base2. $bounded
      *
      * @since 4.0.0
      * @note $disclaimer
      * @see [[IntegerParsers.hexadecimal32 `IntegerParsers.hexadecimal32`]] and [[RealParsers.hexadecimalDouble `RealParsers.hexadecimalDouble`]]
      */
    @inline final def hexadecimal32Double[T: CanHold.can_hold_32_bits]: Parsley[Either[T, Double]] = ensureDouble(hexadecimalBounded(_32))
    /** $base1 octal number, $base2. $bounded
      *
      * @since 4.0.0
      * @note $disclaimer
      * @see [[IntegerParsers.octal32 `IntegerParsers.octal32`]] and [[RealParsers.octalDouble `RealParsers.octalDouble`]]
      */
    @inline final def octal32Double[T: CanHold.can_hold_32_bits]: Parsley[Either[T, Double]] = ensureDouble(octalBounded(_32))
    /** $base1 binary number, $base2. $bounded
      *
      * @since 4.0.0
      * @note $disclaimer
      * @see [[IntegerParsers.binary32 `IntegerParsers.binary32`]] and [[RealParsers.binaryDouble `RealParsers.binaryDouble`]]
      */
    @inline final def binary32Double[T: CanHold.can_hold_32_bits]: Parsley[Either[T, Double]] = ensureDouble(binaryBounded(_32))

    /** $base1 number, $base2. $bounded
      *
      * @since 4.0.0
      * @note $disclaimer
      * @see [[IntegerParsers.number64 `IntegerParsers.number8`]] and [[RealParsers.number `RealParsers.number`]]
      */
    @inline final def number64[T: CanHold.can_hold_64_bits]: Parsley[Either[T, BigDecimal]] = numberBounded(_64)
    /** $base1 decimal number, $base2. $bounded
      *
      * @since 4.0.0
      * @note $disclaimer
      * @see [[IntegerParsers.decimal64 `IntegerParsers.decimal64`]] and [[RealParsers.decimal `RealParsers.decimal`]]
      */
    @inline final def decimal64[T: CanHold.can_hold_64_bits]: Parsley[Either[T, BigDecimal]] = decimalBounded(_64)
    /** $base1 hexadecimal number, $base2. $bounded
      *
      * @since 4.0.0
      * @note $disclaimer
      * @see [[IntegerParsers.hexadecimal64 `IntegerParsers.hexadecimal64`]] and [[RealParsers.hexadecimal `RealParsers.hexadecimal`]]
      */
    @inline final def hexadecimal64[T: CanHold.can_hold_64_bits]: Parsley[Either[T, BigDecimal]] = hexadecimalBounded(_64)
    /** $base1 octal number, $base2. $bounded
      *
      * @since 4.0.0
      * @note $disclaimer
      * @see [[IntegerParsers.octal64 `IntegerParsers.octal64`]] and [[RealParsers.octal `RealParsers.octal`]]
      */
    @inline final def octal64[T: CanHold.can_hold_64_bits]: Parsley[Either[T, BigDecimal]] = octalBounded(_64)
    /** $base1 binary number, $base2. $bounded
      *
      * @since 4.0.0
      * @note $disclaimer
      * @see [[IntegerParsers.binary64 `IntegerParsers.binary64`]] and [[RealParsers.binary `RealParsers.binary`]]
      */
    @inline final def binary64[T: CanHold.can_hold_64_bits]: Parsley[Either[T, BigDecimal]] = binaryBounded(_64)

    /** $base1 number, $base2. $bounded
      *
      * @since 4.0.0
      * @note $disclaimer
      * @see [[IntegerParsers.number64 `IntegerParsers.number64`]] and [[RealParsers.float `RealParsers.float`]]
      */
    @inline final def number64Float[T: CanHold.can_hold_64_bits]: Parsley[Either[T, Float]] = ensureFloat(numberBounded(_64))
    /** $base1 decimal number, $base2. $bounded
      *
      * @since 4.0.0
      * @note $disclaimer
      * @see [[IntegerParsers.decimal64 `IntegerParsers.decimal64`]] and [[RealParsers.decimalFloat `RealParsers.decimalFloat`]]
      */
    @inline final def decimal64Float[T: CanHold.can_hold_64_bits]: Parsley[Either[T, Float]] = ensureFloat(decimalBounded(_64))
    /** $base1 hexadecimal number, $base2. $bounded
      *
      * @since 4.0.0
      * @note $disclaimer
      * @see [[IntegerParsers.hexadecimal64 `IntegerParsers.hexadecimal64`]] and [[RealParsers.hexadecimalFloat `RealParsers.hexadecimalFloat`]]
      */
    @inline final def hexadecimal64Float[T: CanHold.can_hold_64_bits]: Parsley[Either[T, Float]] = ensureFloat(hexadecimalBounded(_64))
    /** $base1 octal number, $base2. $bounded
      *
      * @since 4.0.0
      * @note $disclaimer
      * @see [[IntegerParsers.octal64 `IntegerParsers.octal64`]] and [[RealParsers.octalFloat `RealParsers.octalFloat`]]
      */
    @inline final def octal64Float[T: CanHold.can_hold_64_bits]: Parsley[Either[T, Float]] = ensureFloat(octalBounded(_64))
    /** $base1 binary number, $base2. $bounded
      *
      * @since 4.0.0
      * @note $disclaimer
      * @see [[IntegerParsers.binary64 `IntegerParsers.binary64`]] and [[RealParsers.binaryFloat `RealParsers.binaryFloat`]]
      */
    @inline final def binary64Float[T: CanHold.can_hold_64_bits]: Parsley[Either[T, Float]] = ensureFloat(binaryBounded(_64))

    /** $base1 number, $base2. $bounded
      *
      * @since 4.0.0
      * @note $disclaimer
      * @see [[IntegerParsers.number64 `IntegerParsers.number64`]] and [[RealParsers.double `RealParsers.double`]]
      */
    @inline final def number64Double[T: CanHold.can_hold_64_bits]: Parsley[Either[T, Double]] = ensureDouble(numberBounded(_64))
    /** $base1 decimal number, $base2. $bounded
      *
      * @since 4.0.0
      * @note $disclaimer
      * @see [[IntegerParsers.decimal64 `IntegerParsers.decimal64`]] and [[RealParsers.decimalDouble `RealParsers.decimalDouble`]]
      */
    @inline final def decimal64Double[T: CanHold.can_hold_64_bits]: Parsley[Either[T, Double]] = ensureDouble(decimalBounded(_64))
    /** $base1 hexadecimal number, $base2. $bounded
      *
      * @since 4.0.0
      * @note $disclaimer
      * @see [[IntegerParsers.hexadecimal64 `IntegerParsers.hexadecimal64`]] and [[RealParsers.hexadecimalDouble `RealParsers.hexadecimalDouble`]]
      */
    @inline final def hexadecimal64Double[T: CanHold.can_hold_64_bits]: Parsley[Either[T, Double]] = ensureDouble(hexadecimalBounded(_64))
    /** $base1 octal number, $base2. $bounded
      *
      * @since 4.0.0
      * @note $disclaimer
      * @see [[IntegerParsers.octal64 `IntegerParsers.octal64`]] and [[RealParsers.octalDouble `RealParsers.octalDouble`]]
      */
    @inline final def octal64Double[T: CanHold.can_hold_64_bits]: Parsley[Either[T, Double]] = ensureDouble(octalBounded(_64))
    /** $base1 binary number, $base2. $bounded
      *
      * @since 4.0.0
      * @note $disclaimer
      * @see [[IntegerParsers.binary64 `IntegerParsers.binary64`]] and [[RealParsers.binaryDouble `RealParsers.binaryDouble`]]
      */
    @inline final def binary64Double[T: CanHold.can_hold_64_bits]: Parsley[Either[T, Double]] = ensureDouble(binaryBounded(_64))

    // TODO: the bounded should be combined with the ensuring, for better performance
    protected [numeric] def bounded[T](number: Parsley[Either[BigInt, BigDecimal]], bits: Bits, radix: Int)
                                      (implicit ev: CanHold[bits.self, T]): Parsley[Either[T, BigDecimal]]
    protected [numeric] def _decimal: Parsley[Either[BigInt, BigDecimal]] = decimal
    protected [numeric] def _hexadecimal: Parsley[Either[BigInt, BigDecimal]] = hexadecimal
    protected [numeric] def _octal: Parsley[Either[BigInt, BigDecimal]] = octal
    protected [numeric] def _binary: Parsley[Either[BigInt, BigDecimal]] = binary
    protected [numeric] def _number: Parsley[Either[BigInt, BigDecimal]] = number
    // $COVERAGE-ON$

    private def numberBounded[T](bits: Bits)(implicit ev: CanHold[bits.self, T]): Parsley[Either[T, BigDecimal]] = bounded(_number, bits, 10)
    private def decimalBounded[T](bits: Bits)(implicit ev: CanHold[bits.self, T]): Parsley[Either[T, BigDecimal]] = bounded(_decimal, bits, 10)
    private def hexadecimalBounded[T](bits: Bits)(implicit ev: CanHold[bits.self, T]): Parsley[Either[T, BigDecimal]] = bounded(_hexadecimal, bits, 16)
    private def octalBounded[T](bits: Bits)(implicit ev: CanHold[bits.self, T]): Parsley[Either[T, BigDecimal]] = bounded(_octal, bits, 8)
    private def binaryBounded[T](bits: Bits)(implicit ev: CanHold[bits.self, T]): Parsley[Either[T, BigDecimal]] = bounded(_binary, bits, 2)

    protected [numeric] def ensureFloat[T](number: Parsley[Either[T, BigDecimal]]): Parsley[Either[T, Float]] = {
        err.filterRealOutOfBounds(err.floatName, BigDecimal(Float.MinValue.toDouble), BigDecimal(Float.MaxValue.toDouble)).injectRight.collect(number) {
            case Left(n) => Left(n)
            case Right(n) if RealParsers.isFloat(n) => Right(n.toFloat)
        }
    }

    protected [numeric] def ensureDouble[T](number: Parsley[Either[T, BigDecimal]]): Parsley[Either[T, Double]] = {
        err.filterRealOutOfBounds(err.doubleName, BigDecimal(Double.MinValue), BigDecimal(Double.MaxValue)).injectRight.collect(number) {
            case Left(n) => Left(n)
            case Right(n) if RealParsers.isDouble(n) => Right(n.toDouble)
        }
    }
}
