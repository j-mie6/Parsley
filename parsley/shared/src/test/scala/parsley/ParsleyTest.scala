/*
 * Copyright 2020 Parsley Contributors <https://github.com/j-mie6/Parsley/graphs/contributors>
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package parsley

import org.scalatest.Assertions
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

import parsley.Parsley.eof
import parsley.errors.{ErrorBuilder, tokenextractors}
import org.scalatest.Inside
import org.scalactic.source.Position

case class TestError(pos: (Int, Int), lines: TestErrorLines)

sealed trait TestErrorLines
case class VanillaError(unexpected: Option[TestErrorItem], expecteds: Set[TestErrorItem], reasons: Set[String], width: Int) extends TestErrorLines
case class SpecializedError(msgs: Set[String], width: Int) extends TestErrorLines

sealed trait TestErrorItem
case class Raw(item: String) extends TestErrorItem
case class Named(item: String) extends TestErrorItem
case object EndOfInput extends TestErrorItem

abstract class TestErrorBuilder extends ErrorBuilder[TestError] {
    override def build(pos: Position, source: Source, lines: ErrorInfoLines): TestError = TestError(pos, lines)

    type Position = (Int, Int)
    override def pos(line: Int, col: Int): Position = (line, col)

    type Source = Unit
    override def source(sourceName: Option[String]): Source = ()

    type ErrorInfoLines = TestErrorLines
    override def vanillaError(unexpected: UnexpectedLine, expected: ExpectedLine, reasons: Messages, line: LineInfo): ErrorInfoLines = {
        VanillaError(unexpected, expected, reasons, line)
    }
    override def specializedError(msgs: Messages, line: LineInfo): ErrorInfoLines = SpecializedError(msgs, line)

    type ExpectedItems = Set[Item]
    override def combineExpectedItems(alts: Set[Item]): ExpectedItems = alts

    type Messages = Set[Message]
    override def combineMessages(alts: Seq[Message]): Messages = alts.toSet

    type UnexpectedLine = Option[Item]
    override def unexpected(item: Option[Item]): UnexpectedLine = item
    type ExpectedLine = ExpectedItems
    override def expected(alts: ExpectedItems): ExpectedLine = alts

    type Message = String
    override def reason(reason: String): Message = reason
    override def message(msg: String): Message = msg

    type LineInfo = Int
    override def lineInfo(line: String, linesBefore: Seq[String], linesAfter: Seq[String], lineNum: Int, errorPointsAt: Int, errorWidth: Int): Int = errorWidth

    override val numLinesBefore: Int = 2
    override val numLinesAfter: Int = 2

    type Item = TestErrorItem
    type Raw = parsley.Raw
    type Named = parsley.Named
    type EndOfInput = parsley.EndOfInput.type
    override def raw(item: String): Raw = Raw(item)
    override def named(item: String): Named = Named(item)
    override val endOfInput: EndOfInput = EndOfInput
}

abstract class ParsleyTest extends AnyFlatSpec with Matchers with Assertions with Inside {
    val trivialError = Symbol("trivialError")
    val expectedEmpty = Symbol("expectedEmpty")

    final def cases[A](p: Parsley[A], noEof: Boolean = false)(tests: (String, Option[A], Position)*): Unit = {
        for ((input, res, _pos) <- tests) {
            implicit val pos: Position = _pos
            res match {
                case None if noEof => p.parse(input) shouldBe a [Failure[_]]
                case None => p.parseAll(input) shouldBe a [Failure[_]]
                case Some(x) if noEof => p.parse(input) shouldBe Success(x)
                case Some(x)=> p.parseAll(input) shouldBe Success(x)
            }
        }
    }

    implicit val eb: ErrorBuilder[TestError] = new TestErrorBuilder with tokenextractors.MatchParserDemand

    implicit class FullParse[A](val p: Parsley[A]) {
        def parseAll[Err: ErrorBuilder](input: String): Result[Err, A] = (p <* eof).parse(input)
    }

    implicit class TestCase[A](val x: A) {
        def ->[B](xs: B)(implicit pos: Position): (A, B, Position) = (x, xs, pos)
    }

    implicit class MultiPair[A](val x: A) {
        def -->[B](xs: B*): (A, Seq[B]) = (x, xs)
    }
}
