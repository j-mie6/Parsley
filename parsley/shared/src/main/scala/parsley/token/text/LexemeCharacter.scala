/* SPDX-FileCopyrightText: © 2022 Parsley Contributors <https://github.com/j-mie6/Parsley/graphs/contributors>
 * SPDX-License-Identifier: BSD-3-Clause
 */
package parsley.token.text

import parsley.Parsley
import parsley.token.Lexeme

private [token] final class LexemeCharacter(character: Character, lexeme: Lexeme) extends Character {
    override lazy val fullUtf16: Parsley[Int] = lexeme(character.fullUtf16)
    override lazy val basicMultilingualPlane: Parsley[Char] = lexeme(character.basicMultilingualPlane)
    override lazy val ascii: Parsley[Char] = lexeme(character.ascii)
    override lazy val latin1: Parsley[Char] = lexeme(character.latin1)
}
