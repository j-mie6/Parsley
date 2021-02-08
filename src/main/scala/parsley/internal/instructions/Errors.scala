package parsley.internal.instructions

import ParseError.Unknown
import Raw.Unprintable
import scala.util.matching.Regex

sealed trait ParseError {
    val offset: Int
    val col: Int
    val line: Int

    final def merge(that: ParseError): ParseError = {
        if (this.offset < that.offset) that
        else if (this.offset > that.offset) this
        else (this, that) match {
            case (_: FailError, _: TrivialError) => this
            case (_: TrivialError, _: FailError) => that
            case (_this: FailError, _that: FailError) => FailError(offset, line, col, _this.msgs union _that.msgs)
            case (TrivialError(_, _, _, u1, es1), TrivialError(_, _, _, u2, es2)) =>
                val u = (u1, u2) match {
                    case (Some(u1), Some(u2)) => Some(ErrorItem.higherPriority(u1, u2))
                    case _ => u1.orElse(u2)
                }
                TrivialError(offset, line, col, u, es1 union es2)
        }
    }

    def withHints(hints: Iterable[Hint]): ParseError
    def pretty(sourceName: Option[String], helper: Context#InputHelper): String

    protected final def posStr(sourceName: Option[String]): String = {
        val scopeName = sourceName.fold("")(name => s"In file '$name' ")
        s"$scopeName(line $line, column $col)"
    }

    protected final def disjunct(alts: List[String]): Option[String] = alts.sorted.reverse.filter(_.nonEmpty) match {
        case Nil => None
        case List(alt) => Some(alt)
        case List(alt1, alt2) => Some(s"$alt2 or $alt1")
        case alt::alts => Some(s"${alts.reverse.mkString(", ")}, or $alt")
    }

    protected final def getLineWithCaret(helper: Context#InputHelper): (String, String) = {
        // FIXME: Tabs man... tabs
        val startOffset = helper.nearestNewlineBefore(offset)
        val endOffset = helper.nearestNewlineAfter(offset)
        val segment = helper.segmentBetween(startOffset, endOffset)
        val caretAt = offset - startOffset
        val caretPad = " " * caretAt
        (segment.replace('\t', ' '), s"$caretPad^")
    }

    protected final def assemble(sourceName: Option[String], helper: Context#InputHelper, infoLines: List[String]): String = {
        val topStr = posStr(sourceName)
        val (line, caret) = getLineWithCaret(helper)
        val info = infoLines.filter(_.nonEmpty).mkString("\n  ")
        s"""$topStr:
           |  ${if (info.isEmpty) Unknown else info}
           |  >${line}
           |  >${caret}""".stripMargin
    }
}
case class TrivialError(offset: Int, line: Int, col: Int, unexpected: Option[ErrorItem], expecteds: Set[ErrorItem]) extends ParseError {
    def withHints(hints: Iterable[Hint]): ParseError = copy(expecteds = hints.foldLeft(expecteds)((es, h) => es union h.hint))

    def pretty(sourceName: Option[String], helper: Context#InputHelper): String = {
        assemble(sourceName, helper, List(unexpectedInfo, expectedInfo).flatten)
    }

    private def unexpectedInfo: Option[String] = unexpected.map(u => s"unexpected ${u.msg}")
    private def expectedInfo: Option[String] = disjunct(expecteds.map(_.msg).toList).map(es => s"expected $es")
}
case class FailError(offset: Int, line: Int, col: Int, msgs: Set[String]) extends ParseError {
    def withHints(hints: Iterable[Hint]): ParseError = this
    def pretty(sourceName: Option[String], helper: Context#InputHelper): String = {
        assemble(sourceName, helper, msgs.toList)
    }
}

object ParseError {
    def fail(msg: String, offset: Int, line: Int, col: Int): ParseError = FailError(offset, line, col, Set(msg))
    val Unknown = "unknown parse error"
}

sealed trait ErrorItem {
    val msg: String
}
object ErrorItem {
    def higherPriority(e1: ErrorItem, e2: ErrorItem): ErrorItem = (e1, e2) match {
        case (EndOfInput, _) => EndOfInput
        case (_, EndOfInput) => EndOfInput
        case (e: Desc, _) => e
        case (_, e: Desc) => e
        case (Raw(r1), Raw(r2)) => if (r1.length >= r2.length) e1 else e2
    }
}
case class Raw(cs: String) extends ErrorItem {
    override val msg = cs match {
        case "\n"            => "newline"
        case "\t"            => "tab"
        case " "             => "space"
        case Unprintable(up) => s"unprintable character (${up.head.toInt})"
        case cs              => "\"" + cs.takeWhile(_ != '\n') + "\""
    }
}
object Raw {
    val Unprintable: Regex = "(\\p{C})".r
    def apply(c: Char): Raw = new Raw(s"$c")
}
case class Desc(msg: String) extends ErrorItem
case object EndOfInput extends ErrorItem {
    override val msg = "end of input"
}

final class Hint(val hint: Set[ErrorItem]) extends AnyVal {
    override def toString: String = hint.toString
}