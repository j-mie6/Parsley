/*
 * Copyright 2020 Parsley Contributors <https://github.com/j-mie6/Parsley/graphs/contributors>
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package parsley.internal.errors

import parsley.errors.ErrorBuilder

private [internal] sealed trait ParseError {
    val offset: Int
    val col: Int
    val line: Int

    protected def format(line: String, beforeLines: List[String], afterLines: List[String], caret: Int)
                        (implicit builder: ErrorBuilder[_]): builder.ErrorInfoLines
    private [internal] final def format[Err](sourceName: Option[String])(implicit helper: LineBuilder, builder: ErrorBuilder[Err]): Err = {
        val Some((errLine, caret)) = helper.getLineWithCaret(offset): @unchecked
        val beforeLines = helper.getLinesBefore(offset, builder.numLinesBefore)
        val afterLines = helper.getLinesAfter(offset, builder.numLinesAfter)
        val lines = format(errLine, beforeLines, afterLines, caret)
        builder.build(builder.pos(line, col), builder.source(sourceName), lines)
    }
}
// The reasons here are lightweight, two errors can merge their messages, but messages do not get converted to hints
private [internal] case class TrivialError(offset: Int, line: Int, col: Int,
                                           unexpected: Either[Int, UnexpectItem], expecteds: Set[ExpectItem], reasons: Set[String], lexicalError: Boolean)
    extends ParseError {
    def format(line: String, beforeLines: List[String], afterLines: List[String], caret: Int)(implicit builder: ErrorBuilder[_]): builder.ErrorInfoLines = {
        val unexpectedTok = unexpected.map(_.formatUnexpect(lexicalError))
        // TODO: could we support multi-line carets?
        // FIXME: This should probably use the number of codepoints and not length
        val caretSize = unexpectedTok.fold(identity[Int], _._2)
        builder.vanillaError(
            builder.unexpected(unexpectedTok.toOption.map(_._1)),
            builder.expected(builder.combineExpectedItems(expecteds.map(_.formatExpect))),
            builder.combineMessages(reasons.map(builder.reason(_)).toSeq),
            // this was changed to +1 to allow EoF caret, does this need more nuance?
            // FIXME: this should use the number of codepoints and not length
            builder.lineInfo(line, beforeLines, afterLines, this.line, caret, math.min(caretSize, line.length - caret + 1)))
    }
}
private [internal] case class FancyError(offset: Int, line: Int, col: Int, msgs: List[String], caretWidth: Int) extends ParseError {
    def format(line: String, beforeLines: List[String], afterLines: List[String], caret: Int)(implicit builder: ErrorBuilder[_]): builder.ErrorInfoLines = {
        builder.specializedError(
            builder.combineMessages(msgs.map(builder.message(_))),
            builder.lineInfo(line, beforeLines, afterLines, this.line, caret, math.min(caretWidth, line.length - caret + 1)))
    }
}
