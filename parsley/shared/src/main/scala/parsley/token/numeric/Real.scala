/*
 * Copyright 2020 Parsley Contributors <https://github.com/j-mie6/Parsley/graphs/contributors>
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package parsley.token.numeric

import parsley.Parsley
import parsley.token.errors.{ErrorConfig, LabelWithExplainConfig}

/** This class defines a uniform interface for defining parsers for floating
  * literals, independent of how whitespace should be handled after the literal.
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
  * @define bounded1 This parser will behave the same as
  * @define bounded2Rounded except it will round the result to the nearest valid
  * @define bounded2Exact except it will ensure that the resulting `BigDecimal` is an ''exactly'' represented
  * @define bounded2Plausible except it will ensure that the resulting `BigDecimal` is within the maximum bounds of a
  * @define bounded3 IEEE 754 floating point value. The result is then converted to a
  * @define exact the number is considered exact when it can be losslessly represented in binary.
  * @define plausible
  *   the validation is performed when the value is within the
  *   precision range, and rounding to the nearest exact value
  *   will still occur.
  * @define rounded
  *   if the values are too big or too negatively big, they will
  *   be rounded to the corresponding infinity.
  */
abstract class RealParsers private[numeric](err: ErrorConfig) {
    /** This parser will parse a single real number literal, which is in decimal form (base 10).
      *
      * @since 4.0.0
      * @note $disclaimer
      * @todo examples
      */
    def decimal: Parsley[BigDecimal]
    /** This parser will parse a single real number literal, which is in hexadecimal form (base 16).
      *
      * @since 4.0.0
      * @note $disclaimer
      * @todo examples
      */
    def hexadecimal: Parsley[BigDecimal]
    /** This parser will parse a single real number literal, which is in octal form (base 8).
      *
      * @since 4.0.0
      * @note $disclaimer
      * @todo examples
      */
    def octal: Parsley[BigDecimal]
    /** This parser will parse a single real number literal, which is in binary form (base 2).
      *
      * @since 4.0.0
      * @note $disclaimer
      * @todo examples
      */
    def binary: Parsley[BigDecimal]
    /** This parser will parse a single number literal, which may be in many different forms/bases
      * depending on the configuration provided.
      *
      * @since 4.0.0
      * @note $disclaimer
      * @todo examples
      */
    def number: Parsley[BigDecimal]

    // $COVERAGE-OFF$
    // It's not so important these are tested, they are just wrappers around the bottom ones
    /** $bounded1 [[decimal `decimal`]] $bounded2Rounded `Float`.
      *
      * @since 4.0.0
      * @note $disclaimer
      * @note $rounded
      */
    @inline final def decimalFloatRounded: Parsley[Float] = decimal.map(_.toFloat)
    /** $bounded1 [[hexadecimal `hexadecimal`]] $bounded2Rounded `Float`.
      *
      * @since 4.0.0
      * @note $disclaimer
      * @note $rounded
      */
    @inline final def hexadecimalFloatRounded: Parsley[Float] = hexadecimal.map(_.toFloat)
    /** $bounded1 [[octal `octal`]] $bounded2Rounded `Float`.
      *
      * @since 4.0.0
      * @note $disclaimer
      * @note $rounded
      */
    @inline final def octalFloatRounded: Parsley[Float] = octal.map(_.toFloat)
    /** $bounded1 [[binary `binary`]] $bounded2Rounded `Float`.
      *
      * @since 4.0.0
      * @note $disclaimer
      * @note $rounded
      */
    @inline final def binaryFloatRounded: Parsley[Float] = binary.map(_.toFloat)
    /** $bounded1 [[number `number`]] $bounded2Rounded `Float`.
      *
      * @since 4.0.0
      * @note $disclaimer
      * @note $rounded
      */
    @inline final def floatRounded: Parsley[Float] = number.map(_.toFloat)

    /** $bounded1 [[decimal `decimal`]] $bounded2Rounded `Double`.
      *
      * @since 4.0.0
      * @note $disclaimer
      * @note $rounded
      */
    @inline final def decimalDoubleRounded: Parsley[Double] = decimal.map(_.toDouble)
    /** $bounded1 [[hexadecimal `hexadecimal`]] $bounded2Rounded `Double`.
      *
      * @since 4.0.0
      * @note $disclaimer
      * @note $rounded
      */
    @inline final def hexadecimalDoubleRounded: Parsley[Double] = hexadecimal.map(_.toDouble)
    /** $bounded1 [[octal `octal`]] $bounded2Rounded `Double`.
      *
      * @since 4.0.0
      * @note $disclaimer
      * @note $rounded
      */
    @inline final def octalDoubleRounded: Parsley[Double] = octal.map(_.toDouble)
    /** $bounded1 [[binary `binary`]] $bounded2Rounded `Double`.
      *
      * @since 4.0.0
      * @note $disclaimer
      * @note $rounded
      */
    @inline final def binaryDoubleRounded: Parsley[Double] = binary.map(_.toDouble)
    /** $bounded1 [[number `number`]] $bounded2Rounded `Double`.
      *
      * @since 4.0.0
      * @note $disclaimer
      * @note $rounded
      */
    @inline final def doubleRounded: Parsley[Double] = number.map(_.toDouble)

    /** $bounded1 [[decimal `decimal`]] $bounded2Plausible single-precision $bounded3 `Float`.
      *
      * @since 4.0.0
      * @note $disclaimer
      * @note $plausible
      */
    lazy val decimalFloat: Parsley[Float] = ensureFloat(_decimal, err.labelRealFloatDecimal)
    /** $bounded1 [[hexadecimal `hexadecimal`]] $bounded2Plausible single-precision $bounded3 `Float`.
      *
      * @since 4.0.0
      * @note $disclaimer
      * @note $plausible
      */
    lazy val hexadecimalFloat: Parsley[Float] = ensureFloat(_hexadecimal, err.labelRealFloatHexadecimal)
    /** $bounded1 [[octal `octal`]] $bounded2Plausible single-precision $bounded3 `Float`.
      *
      * @since 4.0.0
      * @note $disclaimer
      * @note $plausible
      */
    lazy val octalFloat: Parsley[Float] = ensureFloat(_octal, err.labelRealFloatOctal)
    /** $bounded1 [[binary `binary`]] $bounded2Plausible single-precision $bounded3 `Float`.
      *
      * @since 4.0.0
      * @note $disclaimer
      * @note $plausible
      */
    lazy val binaryFloat: Parsley[Float] = ensureFloat(_binary, err.labelRealFloatBinary)
    /** $bounded1 [[number `number`]] $bounded2Plausible single-precision $bounded3 `Float`.
      *
      * @since 4.0.0
      * @note $disclaimer
      * @note $plausible
      */
    lazy val float: Parsley[Float] = ensureFloat(_number, err.labelRealFloatNumber)

    /** $bounded1 [[decimal `decimal`]] $bounded2Plausible double-precision $bounded3 `Double`.
      *
      * @since 4.0.0
      * @note $disclaimer
      * @note $plausible
      */
    lazy val decimalDouble: Parsley[Double] = ensureDouble(_decimal, err.labelRealDoubleDecimal)
    /** $bounded1 [[hexadecimal `hexadecimal`]] $bounded2Plausible double-precision $bounded3 `Double`.
      *
      * @since 4.0.0
      * @note $disclaimer
      * @note $plausible
      */
    lazy val hexadecimalDouble: Parsley[Double] = ensureDouble(_hexadecimal, err.labelRealDoubleHexadecimal)
    /** $bounded1 [[octal `octal`]] $bounded2Plausible double-precision $bounded3 `Double`.
      *
      * @since 4.0.0
      * @note $disclaimer
      * @note $plausible
      */
    lazy val octalDouble: Parsley[Double] = ensureDouble(_octal, err.labelRealDoubleOctal)
    /** $bounded1 [[binary `binary`]] $bounded2Plausible double-precision $bounded3 `Double`.
      *
      * @since 4.0.0
      * @note $disclaimer
      * @note $plausible
      */
    lazy val binaryDouble: Parsley[Double] = ensureDouble(_binary, err.labelRealDoubleBinary)
    /** $bounded1 [[number `number`]] $bounded2Plausible double-precision $bounded3 `Double`.
      *
      * @since 4.0.0
      * @note $disclaimer
      * @note $plausible
      */
    lazy val double: Parsley[Double] = ensureDouble(_number, err.labelRealDoubleNumber)
    /** $bounded1 [[decimal `decimal`]] $bounded2Plausible single-precision $bounded3 `Float`.
      *
      * @since 4.0.0
      * @note $disclaimer
      * @note $exact
      */
    lazy val decimalExactFloat: Parsley[Float] = ensureExactFloat(_decimal, err.labelRealFloatDecimal)
    /** $bounded1 [[hexadecimal `hexadecimal`]] $bounded2Exact single-precision $bounded3 `Float`.
      *
      * @since 4.0.0
      * @note $disclaimer
      * @note $exact
      */
    lazy val hexadecimalExactFloat: Parsley[Float] = ensureExactFloat(_hexadecimal, err.labelRealFloatHexadecimal)
    /** $bounded1 [[octal `octal`]] $bounded2Exact single-precision $bounded3 `Float`.
      *
      * @since 4.0.0
      * @note $disclaimer
      * @note $exact
      */
    lazy val octalExactFloat: Parsley[Float] = ensureExactFloat(_octal, err.labelRealFloatOctal)
    /** $bounded1 [[binary `binary`]] $bounded2Exact single-precision $bounded3 `Float`.
      *
      * @since 4.0.0
      * @note $disclaimer
      * @note $exact
      */
    lazy val binaryExactFloat: Parsley[Float] = ensureExactFloat(_binary, err.labelRealFloatBinary)
    /** $bounded1 [[number `number`]] $bounded2Exact single-precision $bounded3 `Float`.
      *
      * @since 4.0.0
      * @note $disclaimer
      * @note $exact
      */
    lazy val exactFloat: Parsley[Float] = ensureExactFloat(_number, err.labelRealFloatNumber)

    /** $bounded1 [[decimal `decimal`]] $bounded2Exact double-precision $bounded3 `Double`.
      *
      * @since 4.0.0
      * @note $disclaimer
      * @note $exact
      */
    lazy val decimalExactDouble: Parsley[Double] = ensureExactDouble(_decimal, err.labelRealDoubleDecimal)
    /** $bounded1 [[hexadecimal `hexadecimal`]] $bounded2Exact double-precision $bounded3 `Double`.
      *
      * @since 4.0.0
      * @note $disclaimer
      * @note $exact
      */
    lazy val hexadecimalExactDouble: Parsley[Double] = ensureExactDouble(_hexadecimal, err.labelRealDoubleHexadecimal)
    /** $bounded1 [[octal `octal`]] $bounded2Exact double-precision $bounded3 `Double`.
      *
      * @since 4.0.0
      * @note $disclaimer
      * @note $exact
      */
    lazy val octalExactDouble: Parsley[Double] = ensureExactDouble(_octal, err.labelRealDoubleOctal)
    /** $bounded1 [[binary `binary`]] $bounded2Exact double-precision $bounded3 `Double`.
      *
      * @since 4.0.0
      * @note $disclaimer
      * @note $exact
      */
    lazy val binaryExactDouble: Parsley[Double] = ensureExactDouble(_binary, err.labelRealDoubleBinary)
    /** $bounded1 [[number `number`]] $bounded2Exact double-precision $bounded3 `Double`.
      *
      * @since 4.0.0
      * @note $disclaimer
      * @note $exact
      */
    lazy val exactDouble: Parsley[Double] = ensureExactDouble(_number, err.labelRealDoubleNumber)
    // $COVERAGE-ON$

    protected [numeric] def ensureFloat(number: Parsley[BigDecimal], label: LabelWithExplainConfig): Parsley[Float] = {
        err.filterRealOutOfBounds(err.floatName, BigDecimal(Float.MinValue.toDouble), BigDecimal(Float.MaxValue.toDouble)).collect(label(number)) {
            case n if RealParsers.isFloat(n) => n.toFloat
        }
    }

    protected [numeric] def ensureDouble(number: Parsley[BigDecimal], label: LabelWithExplainConfig): Parsley[Double] = {
        err.filterRealOutOfBounds(err.doubleName, BigDecimal(Double.MinValue), BigDecimal(Double.MaxValue)).collect(label(number)) {
            case n if RealParsers.isDouble(n) => n.toDouble
        }
    }

    protected [numeric] def ensureExactFloat(number: Parsley[BigDecimal], label: LabelWithExplainConfig): Parsley[Float] = {
        err.filterRealNotExact(err.floatName).collect(label(number)) {
            case n if n.isExactFloat => n.toFloat
        }
    }

    protected [numeric] def ensureExactDouble(number: Parsley[BigDecimal], label: LabelWithExplainConfig): Parsley[Double] = {
        err.filterRealNotExact(err.doubleName).collect(label(number)) {
            case n if n.isExactDouble => n.toDouble
        }
    }

    // $COVERAGE-OFF$
    protected [numeric] def _decimal: Parsley[BigDecimal] = decimal
    protected [numeric] def _hexadecimal: Parsley[BigDecimal] = hexadecimal
    protected [numeric] def _octal: Parsley[BigDecimal] = octal
    protected [numeric] def _binary: Parsley[BigDecimal] = binary
    protected [numeric] def _number: Parsley[BigDecimal] = number
    // $COVERAGE-ON$
}

private [numeric] object RealParsers {
    def isDouble(n: BigDecimal): Boolean = {
        n == 0.0 || n == -0.0 || {
            val x = n.toDouble
            !x.isInfinity && x != 0.0 && x != -0.0
        }
    }

    def isFloat(n: BigDecimal): Boolean = {
        n == 0.0 || n == -0.0 || {
            val x = n.toFloat
            !x.isInfinity && x != 0.0 && x != -0.0
        }
    }
}
