/*
 * Copyright 2020 Parsley Contributors <https://github.com/j-mie6/Parsley/graphs/contributors>
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package parsley.errors

import org.typelevel.scalaccompat.annotation.unused

// Turn coverage off, because the tests have their own error builder
// We might want to test this on its own though
// $COVERAGE-OFF$
/** This class us used to build Parsley's default error messages.
  *
  * While it compiles with the `ErrorBuilder` typeclass, it should not
  * be considered a stable contract: the formatting can be changed at any
  * time and without notice. The API, however, will remain stable.
  *
  * @since 3.0.0
  * @note this class is abstract as it does not implement `unexpectedToken`: when creating an instance mix-in an appropriate
  *       token extractor from `parsley.errors.tokenextractors`.
  * @group formatting
  */
abstract class DefaultErrorBuilder extends ErrorBuilder[String] {
    /** @inheritdoc */
    override def build(pos: Position, source: Source, lines: ErrorInfoLines): String = DefaultErrorBuilder.build(pos, source, lines)

    //override def build(pos: Position, source: Source, ctxs: NestedContexts, lines: ErrorInfoLines): String = {
    //    DefaultErrorBuilder.blockError(header = s"${DefaultErrorBuilder.mergeScopes(source, ctxs)}$pos", lines, indent = 2)"
    //}

    /** @inheritdoc */
    type Position = String
    /** @inheritdoc */
    type Source = Option[String]
    //type Context = Option[String]
    /** @inheritdoc */
    override def pos(line: Int, col: Int): Position = DefaultErrorBuilder.pos(line, col)
    /** @inheritdoc */
    override def source(sourceName: Option[String]): Source = DefaultErrorBuilder.source(sourceName)
    //override def contexualScope(context: String): Context = ???

    //type NestedContexts = Option[String]
    /*override def nestContexts(contexts: List[Context]): NestedContexts = ???*/

    /** @inheritdoc */
    type ErrorInfoLines = Seq[String]
    /** @inheritdoc */
    override def vanillaError(unexpected: UnexpectedLine, expected: ExpectedLine, reasons: Messages, lines: LineInfo): ErrorInfoLines = {
        DefaultErrorBuilder.vanillaError(unexpected, expected, reasons, lines)
    }
    /** @inheritdoc */
    override def specializedError(msgs: Messages, lines: LineInfo): ErrorInfoLines = DefaultErrorBuilder.specializedError(msgs, lines)

    /** @inheritdoc */
    type ExpectedItems = Option[String]
    /** @inheritdoc */
    type Messages = Seq[Message]
    /** @inheritdoc */
    override def combineExpectedItems(alts: Set[Item]): ExpectedItems = DefaultErrorBuilder.disjunct(alts)
    /** @inheritdoc */
    override def combineMessages(alts: Seq[Message]): Messages = DefaultErrorBuilder.combineMessages(alts)

    /** @inheritdoc */
    type UnexpectedLine = Option[String]
    /** @inheritdoc */
    type ExpectedLine = Option[String]
    /** @inheritdoc */
    type Message = String
    /** @inheritdoc */
    type LineInfo = Seq[String]
    /** @inheritdoc */
    override def unexpected(item: Option[Item]): UnexpectedLine = DefaultErrorBuilder.unexpected(item)
    /** @inheritdoc */
    override def expected(alts: ExpectedItems): ExpectedLine = DefaultErrorBuilder.expected(alts)
    /** @inheritdoc */
    override def reason(reason: String): Message = DefaultErrorBuilder.reason(reason)
    /** @inheritdoc */
    override def message(msg: String): Message = DefaultErrorBuilder.message(msg)

    /** @inheritdoc */
    override val numLinesBefore = DefaultErrorBuilder.NumLinesBefore
    /** @inheritdoc */
    override val numLinesAfter = DefaultErrorBuilder.NumLinesAfter
    /** @inheritdoc */
    override def lineInfo(line: String, linesBefore: Seq[String], linesAfter: Seq[String], lineNum: Int, errorPointsAt: Int, errorWidth: Int): LineInfo = {
        DefaultErrorBuilder.lineInfo(line, linesBefore, linesAfter, lineNum, errorPointsAt, errorWidth)
    }

    /** @inheritdoc */
    type Item = String
    /** @inheritdoc */
    type Raw = String
    /** @inheritdoc */
    type Named = String
    /** @inheritdoc */
    type EndOfInput = String
    /** @inheritdoc */
    override def raw(item: String): Raw = DefaultErrorBuilder.raw(item)
    /** @inheritdoc */
    override def named(item: String): Named = DefaultErrorBuilder.named(item)
    /** @inheritdoc */
    override val endOfInput: EndOfInput = DefaultErrorBuilder.EndOfInput
}
/** Helper functions used to build the `DefaultErrorBuilder` error messages.
  *
  * @since 4.3.0
  * @group formatting
  */
object DefaultErrorBuilder {
    final val Unknown = "unknown parse error"
    final val EndOfInput = "end of input"
    final val ErrorLineStart = ">"
    final val NumLinesBefore = 1
    final val NumLinesAfter = 1

    /** Forms an error message with `blockError`, with two spaces of indentation and
      * incorporating the source file and position into the header.
      *
      * @since 4.3.0
      */
    def build(pos: String, source: Option[String], lines: Seq[String]): String = {
        blockError(header = s"${source.fold("")(name => s"In $name ")}$pos", lines, indent = 2)
    }
    /** If the `sourceName` exists, wraps it in quotes and adds `file` onto the front.
      *
      * @since 4.3.0
      */
    def source(sourceName: Option[String]): Option[String] = sourceName.map(name => s"file '$name'")
    /** Forms a vanilla error by combining all the components in sequence, if there is no information
      * other than the `lines`, [[Unknown `Unknown`]] is used instead.
      *
      * @since 4.3.0
      */
    def vanillaError(unexpected: Option[String], expected: Option[String], reasons: Iterable[String], lines: Seq[String]): Seq[String] = {
        DefaultErrorBuilder.combineInfoWithLines(Seq.concat(unexpected, expected, reasons), lines)
    }
    /** Forms a specialized error by combining all components in sequence, if there are no `msgs`, then
      * [[Unknown `Unknown`]] is used instead.
      *
      * @since 4.3.0
      */
    def specializedError(msgs: Seq[String], lines: Seq[String]): Seq[String] = DefaultErrorBuilder.combineInfoWithLines(msgs, lines)

    /** Forms an error with the given `header` followed by a colon, a newline, then the remainder of the lines indented.
      *
      * @since 4.3.0
      */
    def blockError(header: String, lines: Iterable[String], indent: Int): String = s"$header:\n${indentAndUnlines(lines, indent)}"
    /** Indents and concatenates the given lines by the given depth.
      *
      * @since 4.3.0
      */
    def indentAndUnlines(lines: Iterable[String], indent: Int): String = lines.mkString(" " * indent, "\n" + " " * indent, "")

    /** Pairs the line and column up in the form `(line m, column n)`.
      *
      * @since 4.3.0
      */
    def pos(line: Int, col: Int): String = s"(line ${Integer.toUnsignedString(line)}, column ${Integer.toUnsignedString(col)})"

    /** Combines the alternatives, separated by commas/semicolons, with the final two separated
      * by "or". An ''Oxford comma'' is added if there are more than two elements, as this
      * helps prevent ambiguity in the list. If the elements contain a comma, then semicolon
      * is used as the list separator.
      *
      * @since 4.3.0
      */
    def disjunct(alts: Iterable[String]): Option[String] = disjunct(alts, oxfordComma = true)
    /** Combines the alternatives, separated by commas/semicolons, with the final two separated
      * by "or". If the elements contain a comma, then semicolon
      * is used as the list separator.
      *
      * @param oxfordComma decides whether or not to employ an ''Oxford comma'' when there
      *                    more than two elements to join: this helps prevent ambiguity in the list.
      * @since 4.3.0
      */
    def disjunct(alts: Iterable[String], oxfordComma: Boolean): Option[String] = helpers.disjunct(alts.toList.filter(_.nonEmpty), oxfordComma)

    /** Filters out any empty messages and returns the rest.
      *
      * @since 4.3.0
      */
    def combineMessages(alts: Seq[String]): Seq[String] = alts.filter(_.nonEmpty)

    /** Joins together the given sequences: if the first is empty, then [[Unknown `Unknown`]]
      * is prepended onto `lines` instead.
      *
      * @since 4.3.0
      */
    def combineInfoWithLines(info: Seq[String], lines: Seq[String]): Seq[String] = {
        if (info.isEmpty) Unknown +: lines
        else info ++: lines
    }

    /** Adds "unexpected " before the given item should it exist.
      *
      * @since 4.3.0
      */
    def unexpected(item: Option[String]): Option[String] = item.map("unexpected " + _)
    /** Adds "expected " before the given alternatives should they exist.
      *
      * @since 4.3.0
      */
    def expected(alts: Option[String]): Option[String] = alts.map("expected " + _)
    /** Returns the given reason unchanged.
      *
      * @since 4.3.0
      */
    def reason(reason: String): String = reason
    /** Returns the given message unchanged.
      *
      * @since 4.3.0
      */
    def message(msg: String): String = msg

    /** If the given item is either a whitespace character or is otherwise "unprintable",
      * a special name is given to it, otherwise the item is enclosed in double-quotes.
      *
      * @since 4.3.0
      */
    def raw(item: String): String = helpers.renderRawString(item)
    /** Returns the given item unchanged.
      *
      * @since 4.3.0
      */
    def named(item: String): String = item

    /** Constructs error context by concatenating them together with a "caret line" underneath the
      * focus line, `line`, where the error occurs.
      *
      * @since 4.3.0
      */
    def lineInfo(line: String, linesBefore: Seq[String], linesAfter: Seq[String], @unused lineNum: Int, errorPointsAt: Int, errorWidth: Int): Seq[String] = {
        Seq.concat(linesBefore.map(inputLine), Seq(inputLine(line), caretLine(errorPointsAt, errorWidth)), linesAfter.map(inputLine))
    }

    /** Adds the [[ErrorLineStart `ErrorLineStart`]] character to the front of the given line.
      *
      * @since 4.3.0
      */
    def inputLine(line: String): String = s"$ErrorLineStart$line"
    /** Generates a line of `^` characters as wide as specified starting as seen in as the given
      * position, accounting for the length of the [[ErrorLineStart `ErrorLineStart`]] too.
      *
      * @since 4.3.0
      */
    def caretLine(caretAt: Int, caretWidth: Int): String = s"${" " * (ErrorLineStart.length + caretAt)}${"^" * caretWidth}"

    /*def mergeScopes(source: Option[String], ctxs: Option[String]): String = (source, ctxs) match {
        case (None, None) => ""
        case (Some(name), None) => s"In $name "
        case (None, Some(ctxs)) => s"In $ctxs "
        case (Some(name), Some(ctxs)) => s"In $name, $ctxs "
    }*/

    /*def nestContexts(contexts: List[Option[String]]): Option[String] = {
        val nonEmptyContexts = contexts.flatten
        if (nonEmptyContexts.nonEmpty) Some(nonEmptyContexts.mkString(", "))
        else None
    }*/
    /*def contexualScope(context: String): Option[String] = Some(context)*/
}
// $COVERAGE-ON$
