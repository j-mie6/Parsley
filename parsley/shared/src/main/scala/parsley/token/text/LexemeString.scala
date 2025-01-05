/*
 * Copyright 2020 Parsley Contributors <https://github.com/j-mie6/Parsley/graphs/contributors>
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package parsley.token.text

import parsley.Parsley
import parsley.token.Lexeme

private [token] final class LexemeString(string: StringParsers, lexeme: Lexeme) extends StringParsers {
    override lazy val fullUtf16: Parsley[String] = lexeme(string.fullUtf16)
    override lazy val ascii: Parsley[String] = lexeme(string.ascii)
    override lazy val latin1: Parsley[String] = lexeme(string.latin1)
}
