/*
 * Copyright 2020 Parsley Contributors <https://github.com/j-mie6/Parsley/graphs/contributors>
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package parsley

import parsley.syntax.zipped.zippedSyntax3

import parsley.internal.deepembedding.singletons

// TODO: position grouping?
/** This module contains parsers that provide a way to extract position information during a parse.
  *
  * Position parsers can be important
  * for when the final result of the parser needs to encode position information for later consumption:
  * this is particularly useful for abstract syntax trees. Offset is also exposed by this interface, which
  * may be useful for establishing a caret size in specialised error messages.
  *
  * @since 4.2.0
  */
object position extends position
private [parsley] trait position {
    /** This parser returns the current line number (starting at 1) of the input without having any other effect.
      *
      * When this combinator is ran, no input is required, nor consumed, and
      * the current line number will always be successfully returned. It has no other
      * effect on the state of the parser.
      *
      * @example {{{
      * scala> import parsley.position.line, parsley.character.char
      * scala> line.parse("")
      * val res0 = Success(1)
      * scala> (char('a') *> line).parse("a")
      * val res0 = Success(1)
      * scala> (char('\n') *> line).parse("\n")
      * val res0 = Success(2)
      * }}}
      *
      * @return a parser that returns the line number the parser is currently at.
      */
    final val line: Parsley[Int] = _line
    @inline private def _line = new Parsley(singletons.Line)
    /** This parser returns the current column number (starting at 1) of the input without having any other effect.
      *
      * When this combinator is ran, no input is required, nor consumed, and
      * the current column number will always be successfully returned. It has no other
      * effect on the state of the parser.
      *
      * @example {{{
      * scala> import parsley.position.col, parsley.character.char
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
      */
    final val col: Parsley[Int] = _col
    @inline private def _col = new Parsley(singletons.Col)
    /** This parser returns the current line and column numbers (starting at 1) of the input without having any other effect.
      *
      * When this combinator is ran, no input is required, nor consumed, and
      * the current line and column number will always be successfully returned. It has no other
      * effect on the state of the parser.
      *
      * @example {{{
      * scala> import parsley.position.pos, parsley.character.char
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
      */
    final val pos: Parsley[(Int, Int)] = _pos.uo("pos")
    @inline private def _pos = _line.zip(_col)

    // this is subject to change at the slightest notice, do NOT expose
    private [parsley] final def internalOffset: Parsley[Int] = new Parsley(singletons.Offset)

    /** This parser returns the current offset into the input (starting at 0) without having any other effect.
      *
      * When this combinator is ran, no input is required, nor consumed, and
      * the current offset into the input will always be successfully returned. It has no other
      * effect on the state of the parser.
      *
      * @example {{{
      * scala> import parsley.position.offset, parsley.character.char
      * scala> offset.parse("")
      * val res0 = Success(0)
      * scala> (char('a') *> offset).parse("a")
      * val res0 = Success(1)
      * scala> (char('\n') *> offset).parse("\n")
      * val res0 = Success(1)
      * }}}
      *
      * @return a parser that returns the offset the parser is currently at.
      * @note offset does not take wide unicode codepoints into account.
      */
    final val offset: Parsley[Int] = internalOffset

    private [parsley] final def withSpan[A, S](end: Parsley[S])(p: Parsley[A]): Parsley[(S, A, S)] = (end, p, end).zipped

    /** This combinator returns the result of a given parser and the number of characters it consumed.
      *
      * First records the initial `offset` on entry to given parser `p`, then executes `p`. If `p` succeeds,
      * then the `offset` is taken again, and the two values are subtracted to give width `w`. The result of
      * `p`, `x` is returned along with `w` as `(x, w)`. If `p` fails, this combinator will also fail.
      *
      * @example {{{
      * scala> import parsley.position.withWidth, parsley.character.string
      * scala> withWidth(string("abc")).parse("abc")
      * val res0 = Success(("abc", 3))
      * }}}
      *
      * @param p the parser to compute the width for
      * @return a parser that pairs the result of the parser `p` with the number of characters it consumed
      * @note the value returned is the number of 16-bit ''characters'' consumed, not unicode codepoints.
      * @since 4.4.0
      */
    final def withWidth[A](p: Parsley[A]): Parsley[(A, Int)] = (internalOffset.ut(), p, internalOffset.ut()).zipped((s, x, e) => (x, e-s)).uo("withWidth")
}
