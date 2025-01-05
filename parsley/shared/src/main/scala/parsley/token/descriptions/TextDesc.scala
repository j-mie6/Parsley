/*
 * Copyright 2020 Parsley Contributors <https://github.com/j-mie6/Parsley/graphs/contributors>
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package parsley.token.descriptions

import parsley.token.{CharPred, Unicode}

import parsley.internal.collection.immutable.Trie

/** This class, and its subtypes, describe how many digits a numeric escape sequence is allowed.
  *
  * @since 4.0.0
  */
sealed abstract class NumberOfDigits
/** This object contains the concrete subtypes of `NumberOfDigits`.
  *
  * @since 4.0.0
  */
object NumberOfDigits {
    /** There must be at most `n` digits in the numeric escape literal, up to and including the value given.
      *
      * @param n the maximum (inclusive) number of digits allowed in the literal.
      * @since 4.0.0
      */
    final case class AtMost(n: Int) extends NumberOfDigits {
        require(n > 0, "AtMost may only be passed a number of digits greater than 0")
    }
    /** The number of digits in the literal must be one of the given values.
      *
      * @param n0 the first possible digit width.
      * @param ns the other possible digit widths.
      * @since 4.0.0
      */
    final case class Exactly(n0: Int, ns: Int*) extends NumberOfDigits {
        require(n0 > 0, "Exactly may only be passed a number of digits greater than 0")
        require(ns.forall(_ > 0), "Exactly may only be passed a number of digits greater than 0")
        require(ns.lengthCompare(ns.toSet.size) == 0 && !ns.contains(n0), "Exactly may only be provided unique elements") // FIXME: change post-2.12
    }
    /** There is no limit on the number of digits that may appear in this sequence.
      *
      * @since 4.0.0
      */
    case object Unbounded extends NumberOfDigits
}

/** This class, and its subtypes, describe how numeric escape sequences should work for a specific base.
  *
  * @since 4.0.0
  */
sealed abstract class NumericEscape
/** This object contains the concrete subtypes of `NumericEscape`.
  *
  * @since 4.0.0
  */
object NumericEscape {
    /** Numeric literals are supported for this specific base.
      *
      * @param prefix the character, if any, that is required to start the literal (like `x` for hexadecimal escapes in some languages).
      * @param numDigits the number of digits required for this literal: this may be unbounded, an exact number, or up to a specific number.
      * @param maxValue the largest character value that can be expressed by this numeric escape.
      * @since 4.0.0
      */
    final case class Supported(prefix: Option[Char], numDigits: NumberOfDigits, maxValue: Int) extends NumericEscape
    /** Numeric literals are disallowed for this specific base.
      *
      * @since 4.0.0
      */
    case object Illegal extends NumericEscape
}

/** This class describes the valid escape sequences within character and string literals.
  *
  * This allows for the definition of different escape sequences as direct literals, mapping
  * from single or multiple characters to specific values, numeric escape sequences with different
  * bases, as well as supporting zero-width escapes and line continuations via string gaps.
  *
  * @param escBegin the character that starts an escape sequence, very often this is `'\\'`.
  * @param literals the characters that can be directly escaped, but still represent themselves, for instance `'"'`, or `'\\'`.
  * @param mapping the possible escape sequences that map to a character other than themselves and the (full UTF-16) character they map to, for instance `"n" -> 0xa`.
  * @param decimalEscape $numericEscape 10.
  * @param hexadecimalEscape $numericEscape 16.
  * @param octalEscape $numericEscape 8.
  * @param binaryEscape $numericEscape 2.
  * @param emptyEscape if one should exist, the character which has no effect on the string but can be used to disambiguate other
                       escape sequences: in Haskell this would be `\&`.
  * @param gapsSupported specifies whether or not ''string gaps'' are supported: this is where whitespace can be injected between two
  *                      `escBegin` characters and this will all be ignored in the final string, such that `"hello \      \world"` is `"hello world"`.
  *
  * @define numericEscape if allowed, the description of how numeric escape sequences work for base
  * @since 4.0.0
  */
final case class EscapeDesc (escBegin: Char,
                             literals: Set[Char],
                             mapping: Map[String, Int],
                             decimalEscape: NumericEscape,
                             hexadecimalEscape: NumericEscape,
                             octalEscape: NumericEscape,
                             binaryEscape: NumericEscape,
                             emptyEscape: Option[Char],
                             gapsSupported: Boolean,
                            ) {
    private [parsley] val escs = locally {
        val mappingKeys = mapping.keySet
        val literalsStr = literals.map(c => s"$c")
        require(mappingKeys.forall(_.nonEmpty), "empty strings cannot be escape sequences")
        val litAndMapping = literalsStr & mappingKeys
        require(litAndMapping.isEmpty, "there can be no overlap between literals and mapping")
        literalsStr | mappingKeys
    }
    // TODO: ensure that at most one numeric sequence has an empty prefix
    private [token] val escTrie = {
        val escMap = mapping ++ literals.map(c => s"$c" -> c.toInt)
        val badChar = escMap.find(kv => !Character.isValidCodePoint(kv._2))
        require(badChar.isEmpty, s"Escape characters cannot map to invalid characters: ${badChar.get} is not a valid character")
        Trie(escMap)
    }
}
/** This object contains default implementations of the `EscapeDesc` class, which align with
  * different languages or styles.
  *
  * @since 4.0.0
  */
object EscapeDesc {
    /** This is a minimal description of escape characters with the only supported sequence as `\\`.
      *
      * {{{
      * escBegin = '\\'
      * literals = Set('\\')
      * mapping = Map.empty
      * decimalEscape = NumericEscape.Illegal
      * hexadecimalEscape = NumericEscape.Illegal
      * octalEscape = NumericEscape.Illegal
      * binaryEscape = NumericEscape.Illegal
      * emptyEscape = None
      * gapsSupported = false
      * }}}
      *
      * @since 4.0.0
      */
    val plain = EscapeDesc(escBegin = '\\',
                           literals = Set('\\'),
                           mapping = Map.empty,
                           decimalEscape = NumericEscape.Illegal,
                           hexadecimalEscape = NumericEscape.Illegal,
                           octalEscape = NumericEscape.Illegal,
                           binaryEscape = NumericEscape.Illegal,
                           emptyEscape = None,
                           gapsSupported = false)

    /** This description of escape sequences is compliant with the Haskell Report.
      *
      * @since 4.0.0
      */
    val haskell = EscapeDesc(escBegin = '\\',
                             literals = Set('\'', '\"', '\\'),
                             mapping = Map("0" -> 0x0000,
                                           "a" -> 0x0007,
                                           "b" -> 0x0008,
                                           "f" -> 0x000c,
                                           "n" -> 0x000a,
                                           "r" -> 0x000d,
                                           "t" -> 0x0009,
                                           "v" -> 0x000b,
                                           "NUL" -> 0x0000,
                                           "SOH" -> 0x0001,
                                           "STX" -> 0x0002,
                                           "ETX" -> 0x0003,
                                           "EOT" -> 0x0004,
                                           "ENQ" -> 0x0005,
                                           "ACK" -> 0x0006,
                                           "BEL" -> 0x0007,
                                           "BS"  -> 0x0008,
                                           "HT"  -> 0x0009,
                                           "LF"  -> 0x000a,
                                           "VT"  -> 0x000b,
                                           "FF"  -> 0x000c,
                                           "CR"  -> 0x000d,
                                           "SO"  -> 0x000e,
                                           "SI"  -> 0x000f,
                                           "DLE" -> 0x0010,
                                           "DC1" -> 0x0011,
                                           "DC2" -> 0x0012,
                                           "DC3" -> 0x0013,
                                           "DC4" -> 0x0014,
                                           "NAK" -> 0x0015,
                                           "SYN" -> 0x0016,
                                           "ETB" -> 0x0017,
                                           "CAN" -> 0x0018,
                                           "EM"  -> 0x0019,
                                           "SUB" -> 0x001a,
                                           "ESC" -> 0x001b,
                                           "FS"  -> 0x001c,
                                           "GS"  -> 0x001d,
                                           "RS"  -> 0x001e,
                                           "US"  -> 0x001f,
                                           "SP"  -> 0x0020,
                                           "DEL" -> 0x007f) ++
                                           // Control escape sequences
                                           ('@' to '_').map(c => s"^$c" -> (c - '@')).toMap,
                             decimalEscape = NumericEscape.Supported(prefix = None, NumberOfDigits.Unbounded, maxValue = Character.MAX_CODE_POINT),
                             hexadecimalEscape = NumericEscape.Supported(prefix = Some('x'), NumberOfDigits.Unbounded, maxValue = Character.MAX_CODE_POINT),
                             octalEscape = NumericEscape.Supported(prefix = Some('o'), NumberOfDigits.Unbounded, maxValue = Character.MAX_CODE_POINT),
                             binaryEscape = NumericEscape.Illegal,
                             emptyEscape = Some('&'),
                             gapsSupported = true)
}

/** This class describes how textual literals like strings and characters
  * should be processed lexically.
  *
  * @param escapeSequences the description of how escape sequences in literals.
  * @param characterLiteralEnd what character starts and ends a character literal.
  * @param stringEnds what sequences may begin and end a string literal.
  * @param multiStringEnds what sequences may begin and end a multi-line string literal.
  * @param graphicCharacter what characters can be written verbatim into a character or string literal.
  * @since 4.0.0
  */
final case class TextDesc (escapeSequences: EscapeDesc,
                           characterLiteralEnd: Char,
                           stringEnds: Set[(String, String)],
                           multiStringEnds: Set[(String, String)],
                           graphicCharacter: CharPred) {
    require(stringEnds.forall { case (begin, end) => begin.nonEmpty && end.nonEmpty }, "string ends cannot be empty")
    require(multiStringEnds.forall { case (begin, end) => begin.nonEmpty && end.nonEmpty }, "multiline string ends cannot be empty")
}

/** This object contains any preconfigured text definitions.
  *
  * @since 4.0.0
  */
object TextDesc {
    /** Plain definition of text.
      *
      * {{{
      * escapeSequences = EscapeDesc.plain
      * characterLiteralEnd = '\''
      * stringEnds = Set(("\"", "\""))
      * multiStringEnds = Set.empty
      * graphicCharacter = Unicode(_ >= ' '.toInt)
      * }}}
      *
      * @since 4.0.0
      */
    val plain = TextDesc(escapeSequences = EscapeDesc.plain,
                         characterLiteralEnd = '\'',
                         stringEnds = Set(("\"", "\"")),
                         multiStringEnds = Set.empty,
                         graphicCharacter = Unicode(_ >= ' '.toInt))
}
