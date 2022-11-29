/* SPDX-FileCopyrightText: © 2020 Parsley Contributors <https://github.com/j-mie6/Parsley/graphs/contributors>
 * SPDX-License-Identifier: BSD-3-Clause
 */
package parsley.internal.machine.instructions

import scala.annotation.tailrec

import parsley.internal.errors.{EndOfInput, ExpectDesc, ExpectItem, ExpectRaw, UnexpectDesc}
import parsley.internal.machine.{Context, Good}
import parsley.internal.machine.XAssert._
import parsley.internal.machine.errors.{ClassicFancyError, EmptyError, EmptyErrorWithReason}
import parsley.internal.machine.errors.ClassicUnexpectedError

private [internal] final class Lift2(f: (Any, Any) => Any) extends Instr {
    override def apply(ctx: Context): Unit = {
        ensureRegularInstruction(ctx)
        val y = ctx.stack.upop()
        ctx.exchangeAndContinue(f(ctx.stack.peek, y))
    }
    // $COVERAGE-OFF$
    override def toString: String = "Lift2(f)"
    // $COVERAGE-ON$
}
private [internal] object Lift2 {
    def apply[A, B, C](f: (A, B) => C): Lift2 = new Lift2(f.asInstanceOf[(Any, Any) => Any])
}

private [internal] final class Lift3(f: (Any, Any, Any) => Any) extends Instr {
    override def apply(ctx: Context): Unit = {
        ensureRegularInstruction(ctx)
        val z = ctx.stack.upop()
        val y = ctx.stack.upop()
        ctx.exchangeAndContinue(f(ctx.stack.peek, y, z))
    }
    // $COVERAGE-OFF$
    override def toString: String = "Lift3(f)"
    // $COVERAGE-ON$
}
private [internal] object Lift3 {
    def apply[A, B, C, D](f: (A, B, C) => D): Lift3 = new Lift3(f.asInstanceOf[(Any, Any, Any) => Any])
}

private [internal] class CharTok(c: Char, x: Any, errorItem: Option[ExpectItem]) extends Instr {
    override def apply(ctx: Context): Unit = {
        ensureRegularInstruction(ctx)
        if (ctx.moreInput && ctx.nextChar == c) {
            ctx.consumeChar()
            ctx.pushAndContinue(x)
        }
        else ctx.expectedFail(errorItem, unexpectedWidth = 1)
    }
    // $COVERAGE-OFF$
    override def toString: String = if (x == c) s"Chr($c)" else s"ChrPerform($c, $x)"
    // $COVERAGE-ON$
}

private [internal] final class StringTok(s: String, x: Any, errorItem: Option[ExpectItem]) extends Instr {
    private [this] val sz = s.length
    private [this] val codePointLength = s.codePointCount(0, sz)

    @tailrec private [this] def compute(i: Int, lineAdjust: Int, colAdjust: StringTok.Adjust): (Int => Int, Int => Int) = {
        if (i < sz) {
            val (partialLineAdjust, partialColAdjust) = build(lineAdjust, colAdjust)
            partialLineAdjusters(i) = partialLineAdjust
            partialColAdjusters(i) = partialColAdjust
            s.charAt(i) match {
                case '\n' => compute(i + 1, lineAdjust + 1, new StringTok.Set)
                case '\t' => compute(i + 1, lineAdjust, colAdjust.tab)
                case _    => colAdjust.next; compute(i + 1, lineAdjust, colAdjust)
            }
        }
        else build(lineAdjust, colAdjust)
    }
    private [this] def build(lineAdjust: Int, colAdjust: StringTok.Adjust): (Int => Int, Int => Int) = {
        (if (lineAdjust == 0) line => line else _ + lineAdjust, colAdjust.toAdjuster)
    }
    private [this] val partialLineAdjusters = new Array[Int => Int](sz)
    private [this] val partialColAdjusters = new Array[Int => Int](sz)
    private [this] val (lineAdjust, colAdjust) = compute(0, 0, new StringTok.Offset)

    @tailrec private def go(ctx: Context, i: Int, j: Int): Unit = {
        if (j < sz && i < ctx.inputsz && ctx.input.charAt(i) == s.charAt(j)) go(ctx, i + 1, j + 1)
        else if (j < sz) {
            // The offset, line and column haven't been edited yet, so are in the right place
            ctx.expectedFail(errorItem, codePointLength)
            ctx.offset = i
            // These help maintain a consistent internal state, this makes the debuggers
            // output less confusing in the string case in particular.
            ctx.col = partialColAdjusters(j)(ctx.col)
            ctx.line = partialLineAdjusters(j)(ctx.line)
        }
        else {
            ctx.col = colAdjust(ctx.col)
            ctx.line = lineAdjust(ctx.line)
            ctx.offset = i
            ctx.pushAndContinue(x)
        }
    }

    override def apply(ctx: Context): Unit = {
        ensureRegularInstruction(ctx)
        go(ctx, ctx.offset, 0)
    }
    // $COVERAGE-OFF$
    override def toString: String = if (x.isInstanceOf[String] && (s eq x.asInstanceOf[String])) s"Str($s)" else s"StrPerform($s, $x)"
    // $COVERAGE-ON$
}

private [internal] final class If(var label: Int) extends InstrWithLabel {
    override def apply(ctx: Context): Unit = {
        ensureRegularInstruction(ctx)
        if (ctx.stack.pop()) ctx.pc = label
        else ctx.inc()
    }
    // $COVERAGE-OFF$
    override def toString: String = s"If(true: $label)"
    // $COVERAGE-ON$
}

private [internal] final class Case(var label: Int) extends InstrWithLabel {
    override def apply(ctx: Context): Unit = {
        ensureRegularInstruction(ctx)
        ctx.stack.peek[Either[_, _]] match {
            case Left(x)  =>
                ctx.stack.exchange(x)
                ctx.pc = label
            case Right(y) => ctx.exchangeAndContinue(y)
        }
    }
    // $COVERAGE-OFF$
    override def toString: String = s"Case(left: $label)"
    // $COVERAGE-ON$
}

private [internal] final class Filter[A](_pred: A => Boolean) extends Instr {
    private [this] val pred = _pred.asInstanceOf[Any => Boolean]
    override def apply(ctx: Context): Unit = {
        ensureRegularInstruction(ctx)
        ctx.handlers = ctx.handlers.tail
        if (pred(ctx.stack.upeek)) ctx.inc()
        else {
            val state = ctx.states
            ctx.fail(new EmptyError(state.offset, state.line, state.col))
        }
        ctx.states = ctx.states.tail
    }
    // $COVERAGE-OFF$
    override def toString: String = "Filter(?)"
    // $COVERAGE-ON$
}

private [internal] final class MapFilter[A, B](_f: A => Option[B]) extends Instr {
    private [this] val f = _f.asInstanceOf[Any => Option[Any]]
    override def apply(ctx: Context): Unit = {
        ensureRegularInstruction(ctx)
        ctx.handlers = ctx.handlers.tail
        f(ctx.stack.upeek) match {
            case Some(x) => ctx.exchangeAndContinue(x)
            case None =>
                val state = ctx.states
                ctx.fail(new EmptyError(state.offset, state.line, state.col))
        }
        ctx.states = ctx.states.tail
    }
    // $COVERAGE-OFF$
    override def toString: String = "MapFilter(?)"
    // $COVERAGE-ON$
}

private [internal] final class FilterOut[A](_pred: PartialFunction[A, String]) extends Instr {
    private [this] val pred = _pred.asInstanceOf[PartialFunction[Any, String]]
    override def apply(ctx: Context): Unit = {
        ensureRegularInstruction(ctx)
        ctx.handlers = ctx.handlers.tail
        if (pred.isDefinedAt(ctx.stack.upeek)) {
            val reason = pred(ctx.stack.upop())
            val state = ctx.states
            ctx.fail(new EmptyErrorWithReason(state.offset, state.line, state.col, reason))
        }
        else ctx.inc()
        ctx.states = ctx.states.tail
    }
    // $COVERAGE-OFF$
    override def toString: String = "FilterOut(?)"
    // $COVERAGE-ON$
}

private [internal] final class GuardAgainst(pred: PartialFunction[Any, Seq[String]]) extends Instr {
    override def apply(ctx: Context): Unit = {
        ensureRegularInstruction(ctx)
        ctx.handlers = ctx.handlers.tail
        if (pred.isDefinedAt(ctx.stack.upeek)) {
            val state = ctx.states
            val caretWidth = ctx.offset - state.offset
            ctx.fail(new ClassicFancyError(state.offset, state.line, state.col, caretWidth, pred(ctx.stack.upop()): _*))
        }
        else ctx.inc()
        ctx.states = ctx.states.tail
    }
    // $COVERAGE-OFF$
    override def toString: String = "GuardAgainst(?)"
    // $COVERAGE-ON$
}
private [internal] object GuardAgainst {
    def apply[A](pred: PartialFunction[A, Seq[String]]): GuardAgainst = new GuardAgainst(pred.asInstanceOf[PartialFunction[Any, Seq[String]]])
}

private [internal] final class UnexpectedWhen(pred: PartialFunction[Any, String]) extends Instr {
    override def apply(ctx: Context): Unit = {
        ensureRegularInstruction(ctx)
        ctx.handlers = ctx.handlers.tail
        if (pred.isDefinedAt(ctx.stack.upeek)) {
            val state = ctx.states
            val caretWidth = ctx.offset - state.offset
            ctx.fail(new ClassicUnexpectedError(state.offset, state.line, state.col, None, new UnexpectDesc(pred(ctx.stack.upop()), caretWidth)))
        }
        else ctx.inc()
        ctx.states = ctx.states.tail
    }

    // $COVERAGE-OFF$
    override def toString: String = "UnexpectedWhen(?)"
    // $COVERAGE-ON$
}
private [internal] object UnexpectedWhen {
    def apply[A](pred: PartialFunction[A, String]): UnexpectedWhen = new UnexpectedWhen(pred.asInstanceOf[PartialFunction[Any, String]])
}

private [internal] object NegLookFail extends Instr {
    override def apply(ctx: Context): Unit = {
        ensureRegularInstruction(ctx)
        val reached = ctx.offset
        // Recover the previous state; notFollowedBy NEVER consumes input
        ctx.restoreState()
        ctx.restoreHints()
        // A previous success is a failure
        ctx.handlers = ctx.handlers.tail
        ctx.expectedFail(None, reached - ctx.offset)
    }
    // $COVERAGE-OFF$
    override def toString: String = "NegLookFail"
    // $COVERAGE-ON$
}

private [internal] object NegLookGood extends Instr {
    override def apply(ctx: Context): Unit = {
        ensureHandlerInstruction(ctx)
        // Recover the previous state; notFollowedBy NEVER consumes input
        ctx.restoreState()
        ctx.restoreHints()
        // A failure is what we wanted
        ctx.good = true
        ctx.errs = ctx.errs.tail
        ctx.pushAndContinue(())
    }
    // $COVERAGE-OFF$
    override def toString: String = "NegLookGood"
    // $COVERAGE-ON$
}

private [internal] object Eof extends Instr {
    private [this] final val expected = Some(EndOfInput)
    override def apply(ctx: Context): Unit = {
        ensureRegularInstruction(ctx)
        if (ctx.offset == ctx.inputsz) ctx.pushAndContinue(())
        else ctx.expectedFail(expected, unexpectedWidth = 1)
    }
    // $COVERAGE-OFF$
    override final def toString: String = "Eof"
    // $COVERAGE-ON$
}

private [internal] final class Modify(reg: Int, f: Any => Any) extends Instr {
    override def apply(ctx: Context): Unit = {
        ensureRegularInstruction(ctx)
        ctx.writeReg(reg, f(ctx.regs(reg)))
        ctx.pushAndContinue(())
    }
    // $COVERAGE-OFF$
    override def toString: String = s"Modify($reg, f)"
    // $COVERAGE-ON$
}
private [internal] object Modify {
    def apply[S](reg: Int, f: S => S): Modify = new Modify(reg, f.asInstanceOf[Any => Any])
}

private [internal] final class SwapAndPut(reg: Int) extends Instr {
    override def apply(ctx: Context): Unit = {
        ensureRegularInstruction(ctx)
        ctx.writeReg(reg, ctx.stack.peekAndExchange(ctx.stack.upop()))
        ctx.inc()
    }
    // $COVERAGE-OFF$
    override def toString: String = s"SwapAndPut(r$reg)"
    // $COVERAGE-ON$
}

// Companion Objects
private [internal] object CharTok {
    def apply(c: Char, expected: Option[String]): CharTok = apply(c, c, expected)
    def apply(c: Char, x: Any, expected: Option[String]): CharTok = new CharTok(c, x, expected match {
        case Some("") => None
        case Some(e)  => Some(ExpectDesc(e))
        case None     => Some(ExpectRaw(c))
    })
}

private [internal] object StringTok {
    def apply(s: String, expected: Option[String]): StringTok = apply(s, s, expected)
    def apply(s: String, x: Any, expected: Option[String]): StringTok = new StringTok(s, x, expected match {
        case Some("") => None
        case Some(e)  => Some(ExpectDesc(e))
        case None     => Some(ExpectRaw(s))
    })

    private [StringTok] abstract class Adjust {
        private [StringTok] def tab: Adjust
        private [StringTok] def next: Unit
        private [StringTok] def toAdjuster: Int => Int
    }
    // A line has been read, so any updates are fixed
    private [StringTok] class Set extends Adjust {
        private [this] var at = 1
        // Round up to the nearest multiple of 4 /+1/
        private [StringTok] def tab = { at = ((at + 3) & -4) | 1; this } // scalastyle:ignore magic.number
        private [StringTok] def next = at += 1
        private [StringTok] def toAdjuster = {
            val x = at // capture it now, so it doesn't need to hold the object later
            _ => x
        }
    }
    // No information about alignment: a line or a tab hasn't been read
    private [StringTok] class Offset extends Adjust {
        private [this] var by = 0
        private [StringTok] def tab = new OffsetAlignOffset(by)
        private [StringTok] def next = by += 1
        private [StringTok] def toAdjuster = {
            val x = by // capture it now, so it doesn't need to hold the object later
            if (x == 0) col => col else _ + x
        }
    }
    // A tab was read, and no lines, so we adjust first, then align, and work with an aligned value
    private [StringTok] class OffsetAlignOffset(firstBy: Int) extends Adjust {
        private [this] var thenBy = 0
        // Round up to nearest multiple of /4/ (offset from aligned, not real value)
        private [StringTok] def tab = { thenBy = (thenBy | 3) + 1; this }
        private [StringTok] def next = thenBy += 1
        // Round up to the nearest multiple of 4 /+1/
        private [StringTok] def toAdjuster = {
            val x = firstBy // provide an indirection to the object
            val y = thenBy  // capture it now, so it doesn't need to hold the object later
            col => (((col + x + 3) & -4) | 1) + y // scalastyle:ignore magic.number
        }
    }
}

private [internal] object CharTokFastPerform {
    def apply[A >: Char, B](c: Char, f: A => B, expected: Option[String]): CharTok = CharTok(c, f(c), expected)
}

private [internal] object StringTokFastPerform {
    def apply(s: String, f: String => Any, expected: Option[String]): StringTok = StringTok(s, f(s), expected)
}
