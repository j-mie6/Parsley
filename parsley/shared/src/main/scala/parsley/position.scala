/* SPDX-FileCopyrightText: © 2023 Parsley Contributors <https://github.com/j-mie6/Parsley/graphs/contributors>
 * SPDX-License-Identifier: BSD-3-Clause
 */
package parsley

import parsley.implicits.zipped.Zipped3

import parsley.internal.deepembedding.singletons

/** TODO: Document
  * These parsers provide a way to extract position information during a parse. This can be important
  * for when the final result of the parser needs to encode position information for later consumption:
  * this is particularly useful for abstract syntax trees.
  */
object position {
    /** This parser returns the current line number of the input without having any other effect.
      *
      * When this combinator is ran, no input is required, nor consumed, and
      * the current line number will always be successfully returned. It has no other
      * effect on the state of the parser.
      *
      * @example {{{
      * scala> import parsley.Parsley.line, parsley.character.char
      * scala> line.parse("")
      * val res0 = Success(1)
      * scala> (char('a') *> line).parse("a")
      * val res0 = Success(1)
      * scala> (char('\n') *> line).parse("\n")
      * val res0 = Success(2)
      * }}}
      *
      * @return a parser that returns the line number the parser is currently at.
      * @group pos
      */
    val line: Parsley[Int] = new Parsley(singletons.Line)
    /** This parser returns the current column number of the input without having any other effect.
      *
      * When this combinator is ran, no input is required, nor consumed, and
      * the current column number will always be successfully returned. It has no other
      * effect on the state of the parser.
      *
      * @example {{{
      * scala> import parsley.Parsley.col, parsley.character.char
      * scala> col.parse("")
      * val res0 = Success(1)
      * scala> (char('a') *> col).parse("a")
      * val res0 = Success(2)
      * scala> (char('\n') *> col).parse("\n")
      * val res0 = Success(1)
      * }}}
      *
      * @return a parser that returns the column number the parser is currently at.
      * @note in the presence of wide unicode characters, the value returned may be inaccurate.
      * @group pos
      */
    val col: Parsley[Int] = new Parsley(singletons.Col)
    /** This parser returns the current line and column numbers of the input without having any other effect.
      *
      * When this combinator is ran, no input is required, nor consumed, and
      * the current line and column number will always be successfully returned. It has no other
      * effect on the state of the parser.
      *
      * @example {{{
      * scala> import parsley.Parsley.pos, parsley.character.char
      * scala> pos.parse("")
      * val res0 = Success((1, 1))
      * scala> (char('a') *> pos).parse("a")
      * val res0 = Success((1, 2))
      * scala> (char('\n') *> pos).parse("\n")
      * val res0 = Success((2, 1))
      * }}}
      *
      * @return a parser that returns the line and column number the parser is currently at.
      * @note in the presence of wide unicode characters, the column value returned may be inaccurate.
      * @group pos
      */
    val pos: Parsley[(Int, Int)] = line <~> col

    // this is subject to change at the slightest notice, do NOT expose
    private [parsley] val internalOffset: Parsley[Int] = new Parsley(singletons.Offset)
    /** TODO: Document */
    val offset: Parsley[Int] = internalOffset

    private [parsley] def spanWith[A, S](end: Parsley[S])(p: Parsley[A]): Parsley[(S, A, S)] = (end, p, end).zipped
    // this is subject to change at the slightest notice, do NOT expose
    private [parsley] def internalOffsetSpan[A](p: Parsley[A]): Parsley[(Int, A, Int)] = spanWith(internalOffset)(p)
}
