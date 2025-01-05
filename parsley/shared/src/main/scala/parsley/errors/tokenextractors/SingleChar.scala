/*
 * Copyright 2020 Parsley Contributors <https://github.com/j-mie6/Parsley/graphs/contributors>
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package parsley.errors.tokenextractors

import parsley.errors.{helpers, ErrorBuilder, Token}

import org.typelevel.scalaccompat.annotation.unused

/** This extractor mixin provides an implementation for
  * [[parsley.errors.ErrorBuilder.unexpectedToken `ErrorBuilder.unexpectedToken`]] when mixed into
  * an error builder: it will unconditionally report the first character in the remaining input
  * as the problematic token.
  * @since 4.0.0
  * @note In the case of unprintable characters or whitespace, this extractor will favour reporting
  *       a more meaningful name.
  */
trait SingleChar { this: ErrorBuilder[_] =>
    /** @see [[parsley.errors.ErrorBuilder.unexpectedToken `ErrorBuilder.unexpectedToken`]] */
    override final def unexpectedToken(cs: Iterable[Char], @unused amountOfInputParserWanted: Int, @unused lexicalError: Boolean): Token = {
        SingleChar.unexpectedToken(cs)
    }
}

/** Contains the functionality of `SingleChar` as a function.
  * @since 4.0.0
  */
object SingleChar {
    /** The implementation of `unexpectedToken` as done by `SingleChar`, with redundant arguments removed.
      * @since 4.0.0
      */
    def unexpectedToken(cs: Iterable[Char]): Token = {
        val s = cs.take(2).mkString
        s.codePointAt(0) match {
            case helpers.WhitespaceOrUnprintable(name) => Token.Named(name, 1)
            case cp if Character.isSupplementaryCodePoint(cp) => Token.Raw(s)
            case cp => Token.Raw(s"${cp.toChar}")
        }
    }
}
