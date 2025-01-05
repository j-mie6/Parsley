/*
 * Copyright 2020 Parsley Contributors <https://github.com/j-mie6/Parsley/graphs/contributors>
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package parsley.debug

import ParseAttempt._ // scalastyle:ignore underscore.import

/** A representation of the attempts a parser has made during parse-time.
  *
  * @since 4.5.0
  */
private [parsley] final class ParseAttempt private [parsley] (inp: Input, fof: Offset, tof: Offset, fps: Pos, tps: Pos, res: Result) {
    /** The input parsed, as raw text. */ //TODO: I think this can be removed as it can be derived from the full input... (apparently, these are used for the "augments")
    val rawInput: Input = inp

    /** This offset is where the parse attempt started in the input. */
    val fromOffset: Offset = fof

    /** This offset is where the parse attempt finished in the input. */
    val toOffset: Offset = tof

    /** [[fromOffset]] represented as a (line, column) pair. */
    val fromPos: Pos = fps

    /** [[toOffset]] represented as a (line, column pair). */
    val toPos: Pos = tps

    /** Was this parse attempt successful?
      *
      * @note [[success]] if and only if [[result]] is defined (contains a value).
      */
    def success: Success = result.isDefined

    /** If this parse attempt was successful, what did it return? It is guaranteed that `result.isDefined` is true
      * if and only if the attempt is successful.
      *
      * @note [[success]] if and only if [[result]] is defined (contains a value).
      */
    val result: Result = res

    // Utility copy method only to be used internally.
    private [parsley] def copy(inp: Input = rawInput, fof: Offset = fromOffset, tof: Offset = toOffset, fps: Pos = fromPos, tps: Pos = toPos,
                               res: Result = result): ParseAttempt = new ParseAttempt(inp, fof, tof, fps, tps, res)
}

// Ideally, this would be public. However, that introduces potential binary incompatibilities later down the line
// should the return type of unapply change at all in the future.
private [parsley] object ParseAttempt {
    // TODO: Can we get these type aliases from somewhere that already defines them in parsley?
    type Input   = String
    type Offset  = Int
    type Line    = Int
    type Column  = Int
    type Pos     = (Line, Column)
    type Success = Boolean
    type Result  = Option[Any]

    // This gives you everything you need for inspecting a parse attempt made by a parser.
    // Anything extra are most likely internal fields only.
    // To stop warnings about refutable / non-exhaustive matches, the return type must be Some[_].
    // You'd think that'd be in <https://docs.scala-lang.org/tour/extractor-objects.html>, but no.
    // $COVERAGE-OFF$
    def unapply(att: ParseAttempt): Some[(Input, Offset, Offset, Pos, Pos, Success, Result)] = {
        Some((att.rawInput, att.fromOffset, att.toOffset, att.fromPos, att.toPos, att.success, att.result))
    }
    // $COVERAGE-ON$
}
