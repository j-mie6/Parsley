/*
 * Copyright 2020 Parsley Contributors <https://github.com/j-mie6/Parsley/graphs/contributors>
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package parsley.internal.errors

import parsley.XAssert._
import parsley.errors, errors.{ErrorBuilder, Token}

private [internal] sealed abstract class UnexpectItem {
    private [internal] def formatUnexpect(lexicalError: Boolean)(implicit builder: ErrorBuilder[_]): (builder.Item, Int)
    private [internal] def higherPriority(other: UnexpectItem): Boolean
    protected [errors] def lowerThanRaw(other: UnexpectRaw): Boolean
    protected [errors] def lowerThanDesc(other: UnexpectDesc): Boolean
    private [internal] def isFlexible: Boolean
    private [internal] def widen(caret: Int): UnexpectItem
}
private [parsley] sealed trait ExpectItem {
    private [internal] def formatExpect(implicit builder: ErrorBuilder[_]): builder.Item
}

private [internal] final case class UnexpectRaw(val cs: Iterable[Char], val amountOfInputParserWanted: Int) extends UnexpectItem {
    assert(cs.nonEmpty, "we promise that unexpectedToken never receives empty input")
    assert(amountOfInputParserWanted > 0, "we promise not to make the user format 0-width tokens, nor negative ones")
    private [internal] def formatUnexpect(lexicalError: Boolean)(implicit builder: ErrorBuilder[_]): (builder.Item, Int) = {
        builder.unexpectedToken(cs, amountOfInputParserWanted, lexicalError) match {
            case t@Token.Raw(tok) => (builder.raw(tok), t.caretWidth)
            case Token.Named(name, span) => (builder.named(name), span)
        }
    }
    private [internal] override def higherPriority(other: UnexpectItem): Boolean = other.lowerThanRaw(this)
    protected [errors] override def lowerThanRaw(other: UnexpectRaw): Boolean = this.amountOfInputParserWanted < other.amountOfInputParserWanted
    protected [errors] override def lowerThanDesc(other: UnexpectDesc): Boolean = true
    private [internal] override def isFlexible: Boolean = true
    private [internal] override def widen(caret: Int): UnexpectItem = this.copy(amountOfInputParserWanted = math.max(caret, amountOfInputParserWanted))
}

private [parsley] final case class ExpectRaw(cs: String) extends ExpectItem {
    def this(c: Char) = this(s"$c")
    private [internal] def formatExpect(implicit builder: ErrorBuilder[_]): builder.Item = builder.raw(cs)
}
private [parsley] final case class ExpectDesc(msg: String) extends ExpectItem {
    assert(msg.nonEmpty, "Desc cannot contain empty things!")
    private [internal] def formatExpect(implicit builder: ErrorBuilder[_]): builder.Item = builder.named(msg)
}

private [parsley] final case class UnexpectDesc(msg: String, val width: CaretWidth) extends UnexpectItem {
    assert(msg.nonEmpty, "Desc cannot contain empty things!")
    // FIXME: When this is formatted, the width should really be normalised to the number of code points... this information is not readily available
    private [internal] def formatUnexpect(lexicalError: Boolean)(implicit builder: ErrorBuilder[_]): (builder.Item, Int) = (builder.named(msg), width.width)
    private [internal] override def higherPriority(other: UnexpectItem): Boolean = other.lowerThanDesc(this)
    protected [errors] override def lowerThanRaw(other: UnexpectRaw): Boolean = false
    protected [errors] override def lowerThanDesc(other: UnexpectDesc): Boolean = {
        if (this.isFlexible != other.isFlexible) !other.isFlexible
        else this.width.width < other.width.width
    }
    private [internal] override def isFlexible: Boolean = width.isFlexible
    private [internal] override def widen(caret: Int): UnexpectItem = {
        assert(width.isFlexible, "can only widen flexible carets!")
        this.copy(width = new FlexibleCaret(math.max(width.width, caret)))
    }
}
private [internal] object EndOfInput extends UnexpectItem with ExpectItem {
    private [internal] def formatExpect(implicit builder: ErrorBuilder[_]): builder.Item = builder.endOfInput
    private [internal] def formatUnexpect(lexicalError: Boolean)(implicit builder: ErrorBuilder[_]): (builder.Item, Int) = (builder.endOfInput, 1)
    private [internal] override def higherPriority(other: UnexpectItem): Boolean = true
    protected [errors] override def lowerThanRaw(other: UnexpectRaw): Boolean = false
    protected [errors] override def lowerThanDesc(other: UnexpectDesc): Boolean = false
    private [internal] override def isFlexible: Boolean = false
    private [internal] override def widen(caret: Int): UnexpectItem = this
}
