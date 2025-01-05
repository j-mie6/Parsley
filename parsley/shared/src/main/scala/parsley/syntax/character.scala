/*
 * Copyright 2020 Parsley Contributors <https://github.com/j-mie6/Parsley/graphs/contributors>
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package parsley.syntax

import parsley.Parsley
import parsley.character.{char, string}

/** Provides implicit conversions for characters and strings into parsers.
  *
  * The use of `char` and `string` can be distracting to the overall structure
  * of the parser with respect to the grammar. This module exposes combinators
  * that can implicitly convert Scala's string and character literals so that
  * they represent parsers. These will not be whitespace sensitive.
  *
  * @since 4.5.0
  */
object character {
    // $COVERAGE-OFF$
    /** Converts a string literal into a parser that reads that string.
      *
      * Allows for the implicit application of the `string` combinator to a
      * string literal.
      *
      * @see [[parsley.character.string `character.string`]]
      */
    @inline implicit def stringLift(str: String): Parsley[String] = string(str).uo(s""""$str"""")
    /** Converts a character literal into a parser that reads that character.
      *
      * Allows for the implicit application of the `char` combinator to a
      * character literal.
      *
      * @see [[parsley.character.char `character.char`]]
      */
    @inline implicit def charLift(c: Char): Parsley[Char] = char(c).uo(s"\'$c\'")
    // $COVERAGE-ON$
}
