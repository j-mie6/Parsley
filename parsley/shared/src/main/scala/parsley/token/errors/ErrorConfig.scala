/*
 * Copyright 2020 Parsley Contributors <https://github.com/j-mie6/Parsley/graphs/contributors>
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package parsley.token.errors

import org.typelevel.scalaccompat.annotation.unused

/** This class is used to specify how errors should be produced by the
  * [[parsley.token.Lexer `Lexer`]] class.
  *
  * The [[parsley.token.Lexer `Lexer`]] is set up to produce a variety of different
  * errors via `label`-ing, `explain`-ing, and `filter`-ing, and some applications of
  * the ''Verified'' and ''Preventative'' error patterns. The exact content of those
  * errors can be configured here. Errors can be suppressed or specified with different
  * levels of detail, or even switching between ''vanilla'' or ''specialised'' errors.
  *
  * This class should be used by extending it and overriding the relevant parts: all
  * methods here are non-abstract and their default is documented inside. Not configuring
  * something does not mean it will not appear in the message, but will mean it uses the
  * underlying base errors.
  *
  * @since 4.1.0
  * @group errconfig
  *
  * @groupprio numeric 0
  * @groupname numeric Numeric Errors
  * @groupdesc numeric These control the errors generated with the `numeric` component of the `Lexer`.
  *
  * @groupprio text 0
  * @groupname text Text Errors
  * @groupdesc text These control the errors generated with the `text` component of the `Lexer`.
  *
  * @groupprio names 0
  * @groupname names Name Errors
  * @groupdesc names These control the errors generated with the `names` component of the `Lexer`.
  *
  * @groupprio symbol 0
  * @groupname symbol Symbol Errors
  * @groupdesc symbol These control the errors generated with the `symbol` component of the `Lexer`.
  *
  * @groupprio space 0
  * @groupname space Space Errors
  * @groupdesc space These control the errors generated with the `space` component of the `Lexer`.
  */
class ErrorConfig {
    // numeric
    /** How a numeric break character should (like `_`) be referred to or explained within an error.
      * @since 4.1.0
      * @group numeric
      */
    def labelNumericBreakChar: LabelWithExplainConfig = NotConfigured

    /** How unsigned decimal integers should be referred to or explained within an error.
      * @since 4.1.0
      * @note defaults to `labelIntegerUnsignedNumber`
      * @group numeric
      */
    def labelIntegerUnsignedDecimal: LabelWithExplainConfig = labelIntegerUnsignedNumber
    /** How unsigned hexadecimal integers should be referred to or explained within an error.
      * @since 4.1.0
      * @note defaults to `labelIntegerUnsignedNumber`
      * @group numeric
      */
    def labelIntegerUnsignedHexadecimal: LabelWithExplainConfig = labelIntegerUnsignedNumber
    /** How unsigned octal integers should be referred to or explained within an error.
      * @since 4.1.0
      * @note defaults to `labelIntegerUnsignedNumber`
      * @group numeric
      */
    def labelIntegerUnsignedOctal: LabelWithExplainConfig = labelIntegerUnsignedNumber
    /** How unsigned binary integers should be referred to or explained within an error.
      * @since 4.1.0
      * @note defaults to `labelIntegerUnsignedNumber`
      * @group numeric
      */
    def labelIntegerUnsignedBinary: LabelWithExplainConfig = labelIntegerUnsignedNumber
    /** How generic unsigned integers should be referred to or explained within an error.
      * @since 4.1.0
      * @note defaults to [[NotConfigured `NotConfigured`]]
      * @group numeric
      */
    def labelIntegerUnsignedNumber: LabelWithExplainConfig = NotConfigured
    /** How unsigned decimal integers should of a given bit-width be referred to or explained within an error.
      * @since 4.1.0
      * @note defaults to [[parsley.token.errors.ErrorConfig.labelIntegerUnsignedNumber(bits:Int):* `labelIntegerUnsignedNumber`]]
      * @group numeric
      */
    def labelIntegerUnsignedDecimal(@unused bits: Int): LabelWithExplainConfig = labelIntegerUnsignedDecimal
    /** How unsigned hexadecimal integers should of a given bit-width be referred to or explained within an error.
      * @since 4.1.0
      * @note defaults to [[parsley.token.errors.ErrorConfig.labelIntegerUnsignedNumber(bits:Int):* `labelIntegerUnsignedNumber`]]
      * @group numeric
      */
    def labelIntegerUnsignedHexadecimal(@unused bits: Int): LabelWithExplainConfig = labelIntegerUnsignedHexadecimal
    /** How unsigned octal integers should of a given bit-width be referred to or explained within an error.
      * @since 4.1.0
      * @note defaults to [[parsley.token.errors.ErrorConfig.labelIntegerUnsignedNumber(bits:Int):* `labelIntegerUnsignedNumber`]]
      * @group numeric
      */
    def labelIntegerUnsignedOctal(@unused bits: Int): LabelWithExplainConfig = labelIntegerUnsignedOctal
    /** How unsigned binary integers should of a given bit-width be referred to or explained within an error.
      * @since 4.1.0
      * @note defaults to [[parsley.token.errors.ErrorConfig.labelIntegerUnsignedNumber(bits:Int):* `labelIntegerUnsignedNumber`]]
      * @group numeric
      */
    def labelIntegerUnsignedBinary(@unused bits: Int): LabelWithExplainConfig = labelIntegerUnsignedBinary
    /** How generic unsigned integers should of a given bit-width be referred to or explained within an error.
      * @since 4.1.0
      * @note defaults to [[NotConfigured `NotConfigured`]]
      * @group numeric
      */
    def labelIntegerUnsignedNumber(@unused bits: Int): LabelWithExplainConfig = labelIntegerUnsignedNumber

    /** How signed decimal integers should be referred to or explained within an error.
      * @since 4.1.0
      * @note defaults to `labelIntegerSignedNumber`
      * @group numeric
      */
    def labelIntegerSignedDecimal: LabelWithExplainConfig = labelIntegerSignedNumber
    /** How signed hexadecimal integers should be referred to or explained within an error.
      * @since 4.1.0
      * @note defaults to `labelIntegerSignedNumber`
      * @group numeric
      */
    def labelIntegerSignedHexadecimal: LabelWithExplainConfig = labelIntegerSignedNumber
    /** How signed octal integers should be referred to or explained within an error.
      * @since 4.1.0
      * @note defaults to `labelIntegerSignedNumber`
      * @group numeric
      */
    def labelIntegerSignedOctal: LabelWithExplainConfig = labelIntegerSignedNumber
    /** How signed binary integers should be referred to or explained within an error.
      * @since 4.1.0
      * @note defaults to `labelIntegerSignedNumber`
      * @group numeric
      */
    def labelIntegerSignedBinary: LabelWithExplainConfig = labelIntegerSignedNumber
    /** How generic signed integers should be referred to or explained within an error.
      * @since 4.1.0
      * @note defaults to [[NotConfigured `NotConfigured`]]
      * @group numeric
      */
    def labelIntegerSignedNumber: LabelWithExplainConfig = NotConfigured
    /** How signed decimal integers should of a given bit-width be referred to or explained within an error.
      * @since 4.1.0
      * @note defaults to [[parsley.token.errors.ErrorConfig.labelIntegerSignedDecimal(bits:Int):* `labelIntegerSignedDecimal`]]
      * @group numeric
      */
    def labelIntegerSignedDecimal(@unused bits: Int): LabelWithExplainConfig = labelIntegerSignedDecimal
    /** How signed hexadecimal integers should of a given bit-width be referred to or explained within an error.
      * @since 4.1.0
      * @note defaults to [[parsley.token.errors.ErrorConfig.labelIntegerSignedHexadecimal(bits:Int):* `labelIntegerSignedHexadecimal`]]
      * @group numeric
      */
    def labelIntegerSignedHexadecimal(@unused bits: Int): LabelWithExplainConfig = labelIntegerSignedHexadecimal
    /** How signed octal integers should of a given bit-width be referred to or explained within an error.
      * @since 4.1.0
      * @note defaults to [[parsley.token.errors.ErrorConfig.labelIntegerSignedOctal(bits:Int):* `labelIntegerSignedOctal`]]
      * @group numeric
      */
    def labelIntegerSignedOctal(@unused bits: Int): LabelWithExplainConfig = labelIntegerSignedOctal
    /** How signed binary integers should of a given bit-width be referred to or explained within an error.
      * @since 4.1.0
      * @note defaults to [[parsley.token.errors.ErrorConfig.labelIntegerSignedBinary(bits:Int):* `labelIntegerSignedBinary`]]
      * @group numeric
      */
    def labelIntegerSignedBinary(@unused bits: Int): LabelWithExplainConfig = labelIntegerSignedBinary
    /** How generic signed integers should of a given bit-width be referred to or explained within an error.
      * @since 4.1.0
      * @note defaults to [[NotConfigured `NotConfigured`]]
      * @group numeric
      */
    def labelIntegerSignedNumber(@unused bits: Int): LabelWithExplainConfig = labelIntegerSignedNumber

    /** How the fact that the end of a decimal integer literal is expected should be referred to within an error.
      * @since 4.1.0
      * @note defaults to [[labelIntegerNumberEnd `labelIntegerNumberEnd`]]
      * @group numeric
      */
    def labelIntegerDecimalEnd: LabelConfig = labelIntegerNumberEnd
    /** How the fact that the end of a hexadecimal integer literal is expected should be referred to within an error.
      * @since 4.1.0
      * @note defaults to [[labelIntegerNumberEnd `labelIntegerNumberEnd`]]
      * @group numeric
      */
    def labelIntegerHexadecimalEnd: LabelConfig = labelIntegerNumberEnd
    /** How the fact that the end of an octal integer literal is expected should be referred to within an error.
      * @since 4.1.0
      * @note defaults to [[labelIntegerNumberEnd `labelIntegerNumberEnd`]]
      * @group numeric
      */
    def labelIntegerOctalEnd: LabelConfig = labelIntegerNumberEnd
    /** How the fact that the end of a binary integer literal is expected should be referred to within an error.
      * @since 4.1.0
      * @note defaults to [[labelIntegerNumberEnd `labelIntegerNumberEnd`]]
      * @group numeric
      */
    def labelIntegerBinaryEnd: LabelConfig = labelIntegerNumberEnd
    /** How the fact that the end of a generic integer literal is expected should be referred to within an error.
      * @since 4.1.0
      * @note defaults to [[NotConfigured `NotConfigured`]]
      * @group numeric
      */
    def labelIntegerNumberEnd: LabelConfig = NotConfigured

    /** How decimal reals should be referred to or explained within an error.
      * @since 4.1.0
      * @note defaults to [[labelRealNumber `labelRealNumber`]]
      * @group numeric
      */
    def labelRealDecimal: LabelWithExplainConfig = labelRealNumber
    /** How hexadecimal reals should be referred to or explained within an error.
      * @since 4.1.0
      * @note defaults to [[labelRealNumber `labelRealNumber`]]
      * @group numeric
      */
    def labelRealHexadecimal: LabelWithExplainConfig = labelRealNumber
    /** How octal reals should be referred to or explained within an error.
      * @since 4.1.0
      * @note defaults to [[labelRealNumber `labelRealNumber`]]
      * @group numeric
      */
    def labelRealOctal: LabelWithExplainConfig = labelRealNumber
    /** How binary reals should be referred to or explained within an error.
      * @since 4.1.0
      * @note defaults to [[labelRealNumber `labelRealNumber`]]
      * @group numeric
      */
    def labelRealBinary: LabelWithExplainConfig = labelRealNumber
    /** How generic reals should be referred to or explained within an error.
      * @since 4.1.0
      * @note defaults to [[NotConfigured `NotConfigured`]]
      * @group numeric
      */
    def labelRealNumber: LabelWithExplainConfig = NotConfigured
    /** How decimal floats should be referred to or explained within an error.
      * @since 4.1.0
      * @note defaults to [[labelRealDecimal `labelRealDecimal`]]
      * @group numeric
      */
    def labelRealFloatDecimal: LabelWithExplainConfig = labelRealDecimal
    /** How hexadecimal floats should be referred to or explained within an error.
      * @since 4.1.0
      * @note defaults to [[labelRealHexadecimal `labelRealHexadecimal`]]
      * @group numeric
      */
    def labelRealFloatHexadecimal: LabelWithExplainConfig = labelRealHexadecimal
    /** How octal floats should be referred to or explained within an error.
      * @since 4.1.0
      * @note defaults to [[labelRealOctal `labelRealOctal`]]
      * @group numeric
      */
    def labelRealFloatOctal: LabelWithExplainConfig = labelRealOctal
    /** How binary floats should be referred to or explained within an error.
      * @since 4.1.0
      * @note defaults to [[labelRealBinary `labelRealBinary`]]
      * @group numeric
      */
    def labelRealFloatBinary: LabelWithExplainConfig = labelRealBinary
    /** How generic floats should be referred to or explained within an error.
      * @since 4.1.0
      * @note defaults to [[labelRealNumber `labelRealNumber`]]
      * @group numeric
      */
    def labelRealFloatNumber: LabelWithExplainConfig = labelRealNumber
    /** How decimal doubles should be referred to or explained within an error.
      * @since 4.1.0
      * @note defaults to [[labelRealDecimal `labelRealDecimal`]]
      * @group numeric
      */
    def labelRealDoubleDecimal: LabelWithExplainConfig = labelRealDecimal
    /** How hexadecimal doubles should be referred to or explained within an error.
      * @since 4.1.0
      * @note defaults to [[labelRealHexadecimal `labelRealHexadecimal`]]
      * @group numeric
      */
    def labelRealDoubleHexadecimal: LabelWithExplainConfig = labelRealHexadecimal
    /** How octal doubles should be referred to or explained within an error.
      * @since 4.1.0
      * @note defaults to [[labelRealOctal `labelRealOctal`]]
      * @group numeric
      */
    def labelRealDoubleOctal: LabelWithExplainConfig = labelRealOctal
    /** How binary doubles should be referred to or explained within an error.
      * @since 4.1.0
      * @note defaults to [[labelRealBinary `labelRealBinary`]]
      * @group numeric
      */
    def labelRealDoubleBinary: LabelWithExplainConfig = labelRealBinary
    /** How generic doubles should be referred to or explained within an error.
      * @since 4.1.0
      * @note defaults to [[labelRealNumber `labelRealNumber`]]
      * @group numeric
      */
    def labelRealDoubleNumber: LabelWithExplainConfig = labelRealNumber

    /** How the fact that the end of a decimal real literal is expected should be referred to within an error.
      * @since 4.1.0
      * @note defaults to [[labelRealNumberEnd `labelRealNumberEnd`]]
      * @group numeric
      */
    def labelRealDecimalEnd: LabelConfig = labelRealNumberEnd
    /** How the fact that the end of a hexadecimal real literal is expected should be referred to within an error.
      * @since 4.1.0
      * @note defaults to [[labelRealNumberEnd `labelRealNumberEnd`]]
      * @group numeric
      */
    def labelRealHexadecimalEnd: LabelConfig = labelRealNumberEnd
    /** How the fact that the end of an octal real literal is expected should be referred to within an error.
      * @since 4.1.0
      * @note defaults to [[labelRealNumberEnd `labelRealNumberEnd`]]
      * @group numeric
      */
    def labelRealOctalEnd: LabelConfig = labelRealNumberEnd
    /** How the fact that the end of a binary real literal is expected should be referred to within an error.
      * @since 4.1.0
      * @note defaults to [[labelRealNumberEnd `labelRealNumberEnd`]]
      * @group numeric
      */
    def labelRealBinaryEnd: LabelConfig = labelRealNumberEnd
    /** How the fact that the end of a generic real literal is expected should be referred to within an error.
      * @since 4.1.0
      * @note defaults to [[NotConfigured `NotConfigured`]]
      * @group numeric
      */
    def labelRealNumberEnd: LabelConfig = NotConfigured

    /** How the "dot" that separates the integer and fractional part of a real number should be referred to or explained within an error.
      * @since 4.1.0
      * @note defaults to [[NotConfigured `NotConfigured`]]
      * @group numeric
      */
    def labelRealDot: LabelWithExplainConfig = NotConfigured
    /** How the trailing exponents of a real number should be referred to or explained within an error.
      * @since 4.1.0
      * @note defaults to [[NotConfigured `NotConfigured`]]
      * @group numeric
      */
    def labelRealExponent: LabelWithExplainConfig = NotConfigured
    /** How the fact that the end of an exponent part of a real literal is expected should be referred to within an error.
      * @since 4.1.0
      * @note defaults to [[NotConfigured `NotConfigured`]]
      * @group numeric
      */
    def labelRealExponentEnd: LabelConfig = NotConfigured

    /** Even if leading and trailing zeros can be dropped, `.` is not a valid real number: this
      * method specifies how to report that to the user.
      * @since 4.1.0
      * @note defaults to a ''vanilla'' explain: "a real number cannot drop both a leading and trailing zero"
      * @group numeric
      */
    def preventRealDoubleDroppedZero: PreventDotIsZeroConfig = ZeroDotReason("a real number cannot drop both a leading and trailing zero")

    /** This method describes the content of the error when an integer literal is parsed and it is not within the
      * required bit-width.
      * @param min the smallest value the integer could have taken
      * @param max the largest value the integer could have taken
      * @param nativeRadix the radix that the integer was parsed using
      * @since 4.1.0
      * @note defaults to a ''specialised'' error describing what the min and max bounds are.
      * @group numeric
      */
    def filterIntegerOutOfBounds(min: BigInt, max: BigInt, nativeRadix: Int): FilterConfig[BigInt] = new SpecializedMessage[BigInt] {
        def message(n: BigInt) = Seq(s"literal is not within the range ${min.toString(nativeRadix)} to ${max.toString(nativeRadix)}")
    }

    /** This method describes the content of the error when a real literal is parsed and it is not representable exactly as the required precision.
      * @param name the name of the required precision (one of `doubleName` or `floatName`)
      * @since 4.1.0
      * @note defaults to a ''specialised'' error stating that the literal is not exactly representable.
      * @group numeric
      */
    def filterRealNotExact(name: String): FilterConfig[BigDecimal] = new SpecializedMessage[BigDecimal] {
        def message(n: BigDecimal) = Seq(s"literal cannot be represented exactly as an $name")
    }

    /** This method describes the content of the error when a real literal is parsed and it is not within the bounds perscribed by the required precision.
      * @param name the name of the required precision (one of `doubleName` or `floatName`)
      * @param min the smallest value the real could have taken
      * @param max the largest value the real could have taken
      * @since 4.1.0
      * @note defaults to a ''specialised'' error describing what the min and max bounds are.
      * @group numeric
      */
    def filterRealOutOfBounds(name: String, min: BigDecimal, max: BigDecimal): FilterConfig[BigDecimal] =
        new SpecializedMessage[BigDecimal] {
            def message(n: BigDecimal) = Seq(s"literal is not within the range $min to $max and is not an $name")
        }

    // expensive ;)
    // this is not as effective as it may seem, because reasons cannot be hints
    // it's possible a preventative error could be more effective?
    /*def verifiedIntegerBadCharsUsedInLiteral: Option[(predicate.CharPredicate, Int => String)] =
        None*/

    /** The name given to doubles.
      * @since 4.1.0
      * @note defaults to "IEEE 754 double-precision float"
      * @group numeric
      */
    def doubleName: String = "IEEE 754 double-precision float"
    /** The name given to floats.
      * @since 4.1.0
      * @note defaults to "IEEE 754 single-precision float"
      * @group numeric
      */
    def floatName: String = "IEEE 754 single-precision float"

    private [token] final def labelDecimal(bits: Int, signed: Boolean): LabelWithExplainConfig = {
        if (signed) labelIntegerSignedDecimal(bits) else labelIntegerUnsignedDecimal(bits)
    }
    private [token] final def labelHexadecimal(bits: Int, signed: Boolean): LabelWithExplainConfig = {
        if (signed) labelIntegerSignedHexadecimal(bits) else labelIntegerUnsignedHexadecimal(bits)
    }
    private [token] final def labelOctal(bits: Int, signed: Boolean): LabelWithExplainConfig = {
        if (signed) labelIntegerSignedOctal(bits) else labelIntegerUnsignedOctal(bits)
    }
    private [token] final def labelBinary(bits: Int, signed: Boolean): LabelWithExplainConfig = {
        if (signed) labelIntegerSignedBinary(bits) else labelIntegerUnsignedBinary(bits)
    }
    private [token] final def labelNumber(bits: Int, signed: Boolean): LabelWithExplainConfig = {
        if (signed) labelIntegerSignedNumber(bits) else labelIntegerUnsignedNumber(bits)
    }

    /** How an identifier should be referred to in an error message.
      * @since 4.1.0
      * @note defaults to "identifier"
      * @group names
      */
    def labelNameIdentifier: String = "identifier"
    /** How a user-defined operator should be referred to in an error message.
      * @since 4.1.0
      * @note defaults to "operator"
      * @group names
      */
    def labelNameOperator: String = "operator"
    /** How an illegally parsed hard keyword should be referred to as an unexpected component.
      * @param v the illegal identifier
      * @since 4.1.0
      * @note defaults to "keyword v"
      * @group names
      */
    def unexpectedNameIllegalIdentifier(v: String): String = s"keyword $v"
    /** How an illegally parsed hard operator should be referred to as an unexpected component.
      * @since 4.1.0
      * @note defaults to "reserved operator v"
      * @group names
      */
    def unexpectedNameIllegalOperator(v: String): String = s"reserved operator $v"
    /** When parsing identifiers that are required to have specific start characters, how bad identifiers should be reported.
      * @since 4.1.0
      * @note defaults to unexpected "identifier v"
      * @group names
      */
    def filterNameIllFormedIdentifier: FilterConfig[String] = new Unexpected[String] {
        def unexpected(v: String) = s"identifer $v"
    }
    /** When parsing operators that are required to have specific start/end characters, how bad operators should be reported.
      * @since 4.1.0
      * @note defaults to unexpected "operator v"
      * @group names
      */
    def filterNameIllFormedOperator: FilterConfig[String] = new Unexpected[String] {
        def unexpected(v: String) = s"operator $v"
    }

    /** How a ASCII character literal should be referred to or explained in error messages.
      * @since 4.1.0
      * @note defaults to [[NotConfigured `NotConfigured`]]
      * @group text
      */
    def labelCharAscii: LabelWithExplainConfig = NotConfigured
    /** How a Latin1 (extended ASCII) character literal should be referred to or explained in error messages.
      * @since 4.1.0
      * @note defaults to [[NotConfigured `NotConfigured`]]
      * @group text
      */
    def labelCharLatin1: LabelWithExplainConfig = NotConfigured
    /** How a BMP (Basic Multilingual Plane) character literal should be referred to or explained in error messages.
      * @since 4.1.0
      * @note defaults to [[NotConfigured `NotConfigured`]]
      * @group text
      */
    def labelCharBasicMultilingualPlane: LabelWithExplainConfig = NotConfigured
    /** How a UTF-16 character literal should be referred to or explained in error messages.
      * @since 4.1.0
      * @note defaults to [[NotConfigured `NotConfigured`]]
      * @group text
      */
    def labelCharUtf16: LabelWithExplainConfig = NotConfigured

    /** How the closing quote of an ASCII character literal should be referred to in error messages.
      * @since 4.1.0
      * @note defaults to [[NotConfigured `NotConfigured`]]
      * @group text
      */
    def labelCharAsciiEnd: LabelConfig = NotConfigured
    /** How the closing quote of a Latin1 character literal should be referred to in error messages.
      * @since 4.1.0
      * @note defaults to [[NotConfigured `NotConfigured`]]
      * @group text
      */
    def labelCharLatin1End: LabelConfig = NotConfigured
    /** How the closing quote of a BMP character literal should be referred to in error messages.
      * @since 4.1.0
      * @note defaults to [[NotConfigured `NotConfigured`]]
      * @group text
      */
    def labelCharBasicMultilingualPlaneEnd: LabelConfig = NotConfigured
    /** How the closing quote of a UTF-16 character literal should be referred to in error messages.
      * @since 4.1.0
      * @note defaults to [[NotConfigured `NotConfigured`]]
      * @group text
      */
    def labelCharUtf16End: LabelConfig = NotConfigured

    /** How a ASCII-only string literal should be referred to or explained in error messages.
      * @since 4.1.0
      * @param multi whether this is for multi-line strings
      * @param raw whether this is for raw strings
      * @note defaults to [[NotConfigured `NotConfigured`]]
      * @group text
      */
    def labelStringAscii(@unused multi: Boolean, @unused raw: Boolean): LabelWithExplainConfig = NotConfigured
    /** How a Latin1-only string literal should be referred to or explained in error messages.
      * @since 4.1.0
      * @param multi whether this is for multi-line strings
      * @param raw whether this is for raw strings
      * @note defaults to [[NotConfigured `NotConfigured`]]
      * @group text
      */
    def labelStringLatin1(@unused multi: Boolean, @unused raw: Boolean): LabelWithExplainConfig = NotConfigured
    /** How a UTF-16-only string should literal be referred to or explained in error messages.
      * @since 4.1.0
      * @param multi whether this is for multi-line strings
      * @param raw whether this is for raw strings
      * @note defaults to [[NotConfigured `NotConfigured`]]
      * @group text
      */
    def labelStringUtf16(@unused multi: Boolean, @unused raw: Boolean): LabelWithExplainConfig = NotConfigured

    /** How the closing quote(s) of an ASCII string literal should be referred to in error messages.
      * @since 4.1.0
      * @param multi whether this is for multi-line strings
      * @param raw whether this is for raw strings
      * @note defaults to [[NotConfigured `NotConfigured`]]
      * @group text
      */
    def labelStringAsciiEnd(@unused multi: Boolean, @unused raw: Boolean): LabelConfig = NotConfigured
    /** How the closing quote(s) of a Latin1 string literal should be referred to in error messages.
      * @since 4.1.0
      * @param multi whether this is for multi-line strings
      * @param raw whether this is for raw strings
      * @note defaults to [[NotConfigured `NotConfigured`]]
      * @group text
      */
    def labelStringLatin1End(@unused multi: Boolean, @unused raw: Boolean): LabelConfig = NotConfigured
    /**  How the closing quote(s) of a UTF-16 string literal should be referred to in error messages.
      * @since 4.1.0
      * @param multi whether this is for multi-line strings
      * @param raw whether this is for raw strings
      * @note defaults to [[NotConfigured `NotConfigured`]]
      * @group text
      */
    def labelStringUtf16End(@unused multi: Boolean, @unused raw: Boolean): LabelConfig = NotConfigured

    /** How general string characters should be referred to in error messages.
      * @since 4.1.0
      * @note defaults to label of "string character"
      * @note this superscedes [[labelGraphicCharacter `labelGraphicCharacter`]] and [[labelEscapeSequence `labelEscapeSequence`]] within string literals.
      * @group text
      */
    def labelStringCharacter: LabelConfig = Label("string character")
    /** How a graphic character (a regular character in the literal) should be referred to or explained in error messages.
      * @since 4.1.0
      * @note defaults to a label of "graphic character"
      * @note explains for graphic characters do not work in string literals.
      * @group text
      */
    def labelGraphicCharacter: LabelWithExplainConfig = Label("graphic character")
    /** How an escape sequence should be referred to or explained in error messages.
      * @since 4.1.0
      * @note defaults to label of "escape sequence"
      * @note explains for escape characters do not work in string literals.
      * @see [[labelEscapeEnd `labelEscapeEnd`]] for how to explain what valid escape sequences may be when the lead character has been parsed.
      * @group text
      */
    def labelEscapeSequence: LabelWithExplainConfig = Label("escape sequence") //different to "invalid escape sequence"!
    /** How a numeric escape sequence (after the opening character) should be referred to or explained in error messages.
      * @since 4.1.0
      * @param radix the radix this specific configuration applies to
      * @note defaults to [[NotConfigured `NotConfigured`]]
      * @group text
      */
    def labelEscapeNumeric(@unused radix: Int): LabelWithExplainConfig = NotConfigured
    /** How the end of a numeric escape sequence (after a prefix) should be referred to or explained in error messages.
      * @since 4.1.0
      * @param radix the radix this specific configuration applies to
      * @param prefix the character that started this sequence
      * @note defaults to [[NotConfigured `NotConfigured`]]
      * @group text
      */
    def labelEscapeNumericEnd(@unused prefix: Char, @unused radix: Int): LabelWithExplainConfig = NotConfigured
    /** How the end of an escape sequence (anything past the opening character) should be referred to or explained within an error message.
      * @since 4.1.0
      * @note defaults to label of "end of escape sequence" with a reason of "invalid escape sequence"
      * @group text
      */
    def labelEscapeEnd: LabelWithExplainConfig = LabelAndReason(reason = "invalid escape sequence", label = "end of escape sequence")
    /** How zero-width escape characters should be referred to within error messages.
      * @since 4.1.0
      * @note defaults to [[NotConfigured `NotConfigured`]]
      * @group text
      */
    def labelStringEscapeEmpty: LabelConfig = NotConfigured
    /** How string gaps should be referred to within error messages.
      * @since 4.1.0
      * @note defaults to label of "string gap"
      * @group text
      */
    def labelStringEscapeGap: LabelConfig = Label("string gap")
    /** How the end of a string gap (the closing slash) should be referred to within error messages.
      * @since 4.1.0
      * @note defaults to label of "end of string gap"
      * @group text
      */
    def labelStringEscapeGapEnd: LabelConfig = Label("end of string gap")

    /** When a non-BMP character is found in a BMP-only character literal, specifies how this should be reported.
      * @since 4.1.0
      * @note defaults to a filter generating the reason "non-BMP character"
      * @group text
      */
    def filterCharNonBasicMultilingualPlane: VanillaFilterConfig[Int] = new Because[Int] {
        def reason(@unused x: Int) = "non-BMP character"
    }
    /** When a non-ASCII character is found in a ASCII-only character literal, specifies how this should be reported.
      * @since 4.1.0
      * @note defaults to a filter generating the reason "non-ascii character"
      * @group text
      */
    def filterCharNonAscii: VanillaFilterConfig[Int] = new Because[Int] {
        def reason(@unused x: Int) = "non-ascii character"
    }
    /** When a non-Latin1 character is found in a Latin1-only character literal, specifies how this should be reported.
      * @since 4.1.0
      * @note defaults to a filter generating the reason "non-latin1 character"
      * @group text
      */
    def filterCharNonLatin1: VanillaFilterConfig[Int] = new Because[Int] {
        def reason(@unused x: Int) = "non-latin1 character"
    }

    /** When a non-ASCII character is found in a ASCII-only string literal, specifies how this should be reported.
      * @since 4.1.0
      * @note defaults to a filter generating a ''specialised'' message of "non-ascii characters in string literal, this is not allowed"
      * @group text
      */
    def filterStringNonAscii: SpecializedFilterConfig[StringBuilder] = new SpecializedMessage[StringBuilder] {
        def message(@unused s: StringBuilder) = Seq("non-ascii characters in string literal, this is not allowed")
    }

    /** When a non-Latin1 character is found in a Latin1-only string literal, specifies how this should be reported.
      * @since 4.1.0
      * @note defaults to a filter generating a ''specialised'' message of "non-latin1 characters in string literal, this is not allowed"
      * @group text
      */
    def filterStringNonLatin1: SpecializedFilterConfig[StringBuilder] = new SpecializedMessage[StringBuilder] {
        def message(@unused s: StringBuilder) = Seq("non-latin1 characters in string literal, this is not allowed")
    }

    /** When a numeric escape sequence requires a specific number of digits but this was not successfully parsed, this describes how to
      * report that error given the number of successfully parsed digits up this point.
      * @since 4.1.0
      * @param radix the radix used for this numeric escape sequence
      * @param needed the possible numbers of digits required
      * @note defaults to a ''specialised'' message describing how many digits are required but how many were present.
      * @group text
      */
    def filterEscapeCharRequiresExactDigits(@unused radix: Int, needed: Seq[Int]): SpecializedFilterConfig[Int] =
        new SpecializedMessage[Int] {
            def message(got: Int) = {
                assume(needed.nonEmpty, "cannot be empty!")
                val Some(formatted) = parsley.errors.helpers.disjunct(needed.toList.map(_.toString), oxfordComma = true): @unchecked
                Seq(s"numeric escape requires $formatted digits, but only got $got")
            }
        }

    /** When a numeric escape sequence is not legal, this describes how to report that error, given the original illegal character.
      * @since 4.1.0
      * @param maxEscape the largest legal escape character
      * @param radix the radix used for this numeric escape sequence
      * @note defaults to a ''specialised'' message stating if the character is larger than the given maximum, or just an illegal codepoint otherwise.
      * @group text
      */
    def filterEscapeCharNumericSequenceIllegal(maxEscape: Int, radix: Int): SpecializedFilterConfig[BigInt] =
        new SpecializedMessage[BigInt] {
            def message(escapeChar: BigInt) = Seq(
                if (escapeChar > BigInt(maxEscape)) {
                    s"${escapeChar.toString(radix)} is greater than the maximum character value of ${BigInt(maxEscape).toString(radix)}"
                }
                else s"illegal unicode codepoint: ${escapeChar.toString(radix)}"
            )
        }

    /** Character literals parse either graphic characters or escape characters. This configuration allows for individual errors when a character ''not'' part
      * of either graphic characters or escape characters is encountered.
      * @since 4.1.0
      * @note defaults to [[Unverified `Unverified`]]
      * @group text
      */
    def verifiedCharBadCharsUsedInLiteral: VerifiedBadChars = Unverified
    /** String literals parse either graphic characters or escape characters. This configuration allows for individual errors when a character ''not'' part
      * of either graphic characters or escape characters is encountered.
      * @since 4.1.0
      * @note defaults to [[Unverified `Unverified`]]
      * @group text
      */
    def verifiedStringBadCharsUsedInLiteral: VerifiedBadChars = Unverified

    /** Gives names and/or reasons to symbols.
      *
      * Symbols that do not appear in the map are assumed to be `NotConfigured`.
      *
      * @since 5.0.0
      * @note defaults to the empty map
      * @group symbol
      */
    def labelSymbol: Map[String, LabelWithExplainConfig] = Map.empty
    // To unify, or not to unify
    private [parsley] def defaultSymbolKeyword: Labeller = Label
    private [parsley] def defaultSymbolOperator: Labeller = Label
    // Other?
    private [parsley] def defaultSymbolPunctuation: Labeller = NotConfigured
    /** How the required end of a given keyword should be specified in an error.
      * @since 4.1.0
      * @note defaults to "end of symbol"
      * @group symbol
      */
    def labelSymbolEndOfKeyword(symbol: String): String = s"end of $symbol"
    /** How the required end of a given operator should be specified in an error.
      * @since 4.1.0
      * @note defaults to "end of symbol"
      * @group symbol
      */
    def labelSymbolEndOfOperator(symbol: String): String = s"end of $symbol"

    /** How the end of a single-line comment should be described or explained.
      * @since 4.1.0
      * @note defaults to "end of comment"
      * @group space
      */
    def labelSpaceEndOfLineComment: LabelWithExplainConfig = Label("end of comment")
    /** How the end of a multi-line comment should be described or explained.
      * @since 4.1.0
      * @note defaults to "end of comment"
      * @group space
      */
    def labelSpaceEndOfMultiComment: LabelWithExplainConfig = Label("end of comment")
}
