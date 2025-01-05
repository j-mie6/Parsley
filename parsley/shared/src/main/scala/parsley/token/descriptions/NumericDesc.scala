/*
 * Copyright 2020 Parsley Contributors <https://github.com/j-mie6/Parsley/graphs/contributors>
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package parsley.token.descriptions

/** This class, and its subtypes, describe whether or not the plus sign (`+`) is allowed
  * in a specific position.
  *
  * @since 4.0.0
  */
sealed abstract class PlusSignPresence
/** This object contains the concrete subtypes for `PlusSignPresence`.
  *
  * @since 4.0.0
  */
object PlusSignPresence {
    /** When writing a non-negative literal, a `+` is mandatory before the literal.
      *
      * @since 4.0.0
      */
    case object Required extends PlusSignPresence
    /** When writing a non-negative literal, a `+` can be added, but is not required.
      *
      * @since 4.0.0
      */
    case object Optional extends PlusSignPresence
    /** Positive literals must not be prefixed by a `+`.
      *
      * @since 4.0.0
      */
    case object Illegal extends PlusSignPresence
}

/** This class, and its subtypes, describe how scientific exponent notation can be used within real literals.
  *
  * @since 4.0.0
  */
sealed abstract class ExponentDesc
/** This object contains the concrete subtypes of `ExponentDesc`.
  *
  * @since 4.0.0
  */
object ExponentDesc {
    /** Exponents are not supported.
      *
      * @since 4.0.0
      */
    case object NoExponents extends ExponentDesc
    /** Exponents are supported, which may be compulsory. The base of the exponent can vary, as can whether a positive (`+`) sign
      * is allowed before the exponent.
      *
      * @param compulsory is an exponent ''required'' for the literal (at a specific base) to be valid?
      * @param chars the set of possible characters that can start an exponent part of a literal.
      * @param base the base of the exponent: for instance `e3` with `base = 10` would represent multiplication by 1000.
      * @param positiveSign are positive (`+`) signs allowed, required, or illegal in front of the exponent?
      * @param leadingZerosAllowed are extraneous zeros allowed at the start of the exponent?
      * @since 4.0.0
      */
    final case class Supported(compulsory: Boolean,
                               chars: Set[Char],
                               base: Int,
                               positiveSign: PlusSignPresence,
                               leadingZerosAllowed: Boolean,
                              ) extends ExponentDesc {
        require(chars.nonEmpty, "The characters used for floating point exponents must not be empty")
    }
}

/** This class, and its subtypes, describe how break characters are supported within literals.
  *
  * @since 4.0.0
  */
sealed abstract class BreakCharDesc
/** This object contains the concrete subtypes of `BreakCharDesc`.
  *
  * @since 4.0.0
  */
object BreakCharDesc {
    /** Literals cannot be broken.
      *
      * @since 4.0.0
      */
    case object NoBreakChar extends BreakCharDesc
    /** Literals may be broken, and this break may be legal after a non-decimal literal prefix
      *
      * @param breakChar the character allowed to break a literal (often `_`).
      * @param allowedAfterNonDecimalPrefix is it possible to write, say, `0x_300`?
      * @since 4.0.0
      */
    final case class Supported(breakChar: Char, allowedAfterNonDecimalPrefix: Boolean) extends BreakCharDesc
}

// TODO: configurable dot?
/** This class describes how numeric literals, in different bases, should be processed lexically.
  *
  * @param literalBreakChar describes if breaks can be found within numeric literals.
  * @param leadingDotAllowed can a real number omit a leading 0 before the point?
  * @param trailingDotAllowed can a real number omit a trailing 0 after the point?
  * @param leadingZerosAllowed are extraneous zeros allowed at the start of decimal numbers?
  * @param positiveSign describes if positive (`+`) signs are allowed, compulsory, or illegal.
  * @param integerNumbersCanBeHexadecimal $genericInt hexadecimal?
  * @param integerNumbersCanBeOctal $genericInt octal?
  * @param integerNumbersCanBeBinary $genericInt binary?
  * @param realNumbersCanBeHexadecimal $genericReal hexadecimal?
  * @param realNumbersCanBeOctal $genericReal octal?
  * @param realNumbersCanBeBinary $genericReal binary?
  * @param hexadecimalLeads what characters begin a hexadecimal literal following a `0` (may be empty).
  * @param octalLeads what characters begin an octal literal following a `0` (may be empty).
  * @param binaryLeads what characters begin a binary literal following a `0` (may be empty).
  * @param decimalExponentDesc $genericExp decimal literals.
  * @param hexadecimalExponentDesc $genericExp hexadecimal literals.
  * @param octalExponentDesc $genericExp octal literals.
  * @param binaryExponentDesc $genericExp binary literals.
  * @since 4.0.0
  *
  * @define genericInt is it possible for generic "integer numbers" to be
  * @define genericReal is it possible for generic "real numbers" to be
  * @define genericExp describes how scientific exponent notation should work for
  */
final case class NumericDesc (literalBreakChar: BreakCharDesc,
                              leadingDotAllowed: Boolean,
                              trailingDotAllowed: Boolean,
                              leadingZerosAllowed: Boolean,
                              positiveSign: PlusSignPresence,
                              // generic number
                              integerNumbersCanBeHexadecimal: Boolean,
                              integerNumbersCanBeOctal: Boolean,
                              integerNumbersCanBeBinary: Boolean,
                              realNumbersCanBeHexadecimal: Boolean,
                              realNumbersCanBeOctal: Boolean,
                              realNumbersCanBeBinary: Boolean,
                              // special literals
                              hexadecimalLeads: Set[Char],
                              octalLeads: Set[Char],
                              binaryLeads: Set[Char],
                              // exponents
                              decimalExponentDesc: ExponentDesc,
                              hexadecimalExponentDesc: ExponentDesc,
                              octalExponentDesc: ExponentDesc,
                              binaryExponentDesc: ExponentDesc,
                             ) {
    private def boolToInt(x: Boolean): Int = if (x) 1 else 0

    locally {
        val intHex = boolToInt(integerNumbersCanBeHexadecimal)
        val intOct = boolToInt(integerNumbersCanBeOctal)
        val intBin = boolToInt(integerNumbersCanBeBinary)
        val realHex = boolToInt(realNumbersCanBeHexadecimal)
        val realOct = boolToInt(realNumbersCanBeOctal)
        val realBin = boolToInt(realNumbersCanBeBinary)
        // There can, for either ints or real /number/s be at most 1 empty prefix special
        // they can all not require prefixes when they are used explicitly
        val emptyHex = boolToInt(hexadecimalLeads.isEmpty)
        val emptyOct = boolToInt(octalLeads.isEmpty)
        val emptyBin = boolToInt(binaryLeads.isEmpty)
        val numEmptyInt = intHex * emptyHex + intOct * emptyOct + intBin * emptyBin
        val numEmptyReal =  realHex * emptyHex + realOct * emptyOct + realBin * emptyBin

        require(numEmptyInt <= 1 && numEmptyReal <= 1,
                "More than one of hexadecimal, octal, or binary do not use a prefix in integer or real numbers, this is not allowed as it is ambiguous")

        require(numEmptyInt + numEmptyReal == 0 || !leadingZerosAllowed,
                "One of hexadecimal, octal, or binary do not use a prefix, so decimal numbers must not allow for leading zeros as it is ambiguous")
    }


    private [token] def exponentDescForRadix(x: Int): ExponentDesc = (x: @unchecked) match {
        case 10 => decimalExponentDesc
        case 16 => hexadecimalExponentDesc
        case 8 => octalExponentDesc
        case 2 => binaryExponentDesc
    }

    private [token] def decimalIntegersOnly: Boolean = !(integerNumbersCanBeBinary || integerNumbersCanBeHexadecimal || integerNumbersCanBeOctal)
    private [token] def decimalRealsOnly: Boolean = !(realNumbersCanBeBinary || realNumbersCanBeHexadecimal || realNumbersCanBeOctal)
}

/** This object contains any preconfigured text definitions.
  *
  * @since 4.0.0
  */
object NumericDesc {
    /** Plain definition of numeric literals. Supports leading zeros; hexadecimal, octal, and binary notation;
      * and exponent notation for all four bases too. Only hexadecimal and octal are enabled for integer numbers,
      * and only decimal for real numbers.
      *
      * {{{
      * literalBreakChar = BreakCharDesc.NoBreakChar
      * leadingDotAllowed = false
      * trailingDotAllowed = false
      * leadingZerosAllowed = true
      * positiveSign = PlusSignPresence.Optional
      * integerNumbersCanBeHexadecimal = true
      * integerNumbersCanBeOctal = true
      * integerNumbersCanBeBinary = false
      * realNumbersCanBeHexadecimal = false
      * realNumbersCanBeOctal = false
      * realNumbersCanBeBinary = false
      * hexadecimalLeads = Set('x', 'X')
      * octalLeads = Set('o', 'O')
      * binaryLeads = Set('b', 'B')
      * decimalExponentDesc = ExponentDesc.Supported(compulsory = false, chars = Set('e', 'E'), base = 10, positiveSign = PlusSignPresence.Optional, leadingZerosAllowed = true)
      * hexadecimalExponentDesc = ExponentDesc.Supported(compulsory = true, chars = Set('p', 'P'), base = 2, positiveSign = PlusSignPresence.Optional, leadingZerosAllowed = true)
      * octalExponentDesc = ExponentDesc.Supported(compulsory = true, chars = Set('e', 'E', 'p', 'P'), base = 2, positiveSign = PlusSignPresence.Optional, leadingZerosAllowed = true)
      * binaryExponentDesc = ExponentDesc.Supported(compulsory = true, chars = Set('e', 'E', 'p', 'P'), base = 2, positiveSign = PlusSignPresence.Optional, leadingZerosAllowed = true)
      * }}}
      *
      * @since 4.0.0
      */
    val plain: NumericDesc = NumericDesc(
        literalBreakChar = BreakCharDesc.NoBreakChar,
        leadingDotAllowed = false,
        trailingDotAllowed = false,
        leadingZerosAllowed = true,
        positiveSign = PlusSignPresence.Optional,
        // generic number
        integerNumbersCanBeHexadecimal = true,
        integerNumbersCanBeOctal = true,
        integerNumbersCanBeBinary = false,
        realNumbersCanBeHexadecimal = false,
        realNumbersCanBeOctal = false,
        realNumbersCanBeBinary = false,
        // special literals
        hexadecimalLeads = Set('x', 'X'),
        octalLeads = Set('o', 'O'),
        binaryLeads = Set('b', 'B'),
        // exponents
        decimalExponentDesc =
            ExponentDesc.Supported(compulsory = false, chars = Set('e', 'E'), base = 10, positiveSign = PlusSignPresence.Optional, leadingZerosAllowed = true),
        hexadecimalExponentDesc =
            ExponentDesc.Supported(compulsory = true, chars = Set('p', 'P'), base = 2, positiveSign = PlusSignPresence.Optional, leadingZerosAllowed = true),
        octalExponentDesc =
            ExponentDesc.Supported(compulsory = true, chars = Set('e', 'E', 'p', 'P'), base = 2, positiveSign = PlusSignPresence.Optional, leadingZerosAllowed = true),
        binaryExponentDesc =
            ExponentDesc.Supported(compulsory = true, chars = Set('e', 'E', 'p', 'P'), base = 2, positiveSign = PlusSignPresence.Optional, leadingZerosAllowed = true),
    )
}
