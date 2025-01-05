/*
 * Copyright 2020 Parsley Contributors <https://github.com/j-mie6/Parsley/graphs/contributors>
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package parsley.token.descriptions


import org.scalacheck.Gen
import org.scalacheck.Arbitrary
import parsley.token.{Unicode, NotRequired, CharPred, Basic}

object DescGen {
    // NAMES
    val identifierLetterGen = Gen.oneOf[CharPred](
        Basic(_.isLetter),
        Basic(_.isLetterOrDigit),
        NotRequired,
        Unicode(Character.isLetter(_)),
        Unicode(Character.isLetterOrDigit(_)),
        Basic('$'),
    )

    private val opCharGen = Gen.nonEmptyContainerOf[Set, Char](Gen.oneOf('+', '*', '/', 'a'))
    private val opUniGen = Gen.nonEmptyContainerOf[Set, Int](Gen.oneOf(0x1F642, 0x1F643, '£', '$'))
    val operatorLetterGen = Gen.frequency(
        3 -> opCharGen.map(Basic.apply),
        3 -> Gen.oneOf(opCharGen.map(_.map(_.toInt)), opUniGen).map(Unicode.apply),
        1 -> Gen.const(NotRequired)
    )

    val nameDescGen = for {
        identifierStart <- identifierLetterGen
        identifierLetter <- identifierLetterGen
        operatorStart <- operatorLetterGen
        operatorLetter <- operatorLetterGen
    } yield NameDesc(identifierStart, identifierLetter, operatorStart, operatorLetter)

    // SYMBOL
    val keywordGen = Gen.oneOf("if", "foo", "while", "key", "a", "bar7")
    private val keywordsGen = Gen.containerOf[Set, String](keywordGen)
    val operatorGen = Gen.oneOf("+", "*", "**", "***", "::", "*:*", "ba")
    private val operatorsGen = Gen.containerOf[Set, String](operatorGen)

    val symbolDescGen = for {
        ks <- keywordsGen
        ops <- operatorsGen
        caseSensitive <- Arbitrary.arbitrary[Boolean]
    } yield SymbolDesc(ks, ops, caseSensitive)

    // TEXT
    private val codepointGen = Gen.choose(0, java.lang.Character.MAX_CODE_POINT)

    private val literalGen = Gen.containerOf[Set, Char](Gen.oneOf('\'', '\"', '\\'))
    private val multiGen = Gen.mapOf(Gen.zip(Gen.nonEmptyListOf(Gen.alphaChar).map(_.mkString), codepointGen))


    def numericEscapeOf(prefix: Option[Char])(numDigits: NumberOfDigits): NumericEscape = {
        NumericEscape.Supported(prefix, numDigits, java.lang.Character.MAX_CODE_POINT)
    }

    private val digitsRange = Gen.choose(1, 16)
    private def numericEscapeGen(prefix: Option[Char]): Gen[NumericEscape] = Gen.oneOf(
        Gen.const(NumericEscape.Illegal),
        Gen.const(NumberOfDigits.Unbounded).map(numericEscapeOf(prefix)),
        digitsRange.map(NumberOfDigits.AtMost(_)).map(numericEscapeOf(prefix)),
        Gen.containerOfN[Set, Int](4, digitsRange).suchThat(_.nonEmpty).map { set =>
            val x::xs = set.toList: @unchecked
            numericEscapeOf(prefix)(NumberOfDigits.Exactly(x, xs: _*))
        }
    )

    val escDescGen = for {
        literals <- literalGen
        mapping <- multiGen
        decimalEscape <- numericEscapeGen(None)
        hexadecimalEscape <- numericEscapeGen(Some('x'))
        octalEscape <- numericEscapeGen(Some('o'))
        binaryEscape <- numericEscapeGen(Some('b'))
        emptyEscape <- Gen.oneOf(None, Some('&'))
        gapsSupported <- Arbitrary.arbitrary[Boolean]
    } yield EscapeDesc('\\', literals, mapping, decimalEscape, hexadecimalEscape, octalEscape, binaryEscape, emptyEscape, gapsSupported)
}
