/*
 * Copyright 2020 Parsley Contributors <https://github.com/j-mie6/Parsley/graphs/contributors>
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package parsley.internal.machine

import scala.annotation.tailrec

//import parsley.{Failure, Result, Success} // not sure why this fails scalacheck, but I guess we'll leave it until I can submit a bug report
import parsley.Failure
import parsley.Result
import parsley.Success
import parsley.XAssert._
import parsley.errors.ErrorBuilder

import parsley.internal.errors.{CaretWidth, ExpectItem, LineBuilder, UnexpectDesc}
import parsley.internal.machine.errors.{ClassicFancyError, DefuncError, DefuncHints, EmptyHints,
                                        ErrorItemBuilder, ExpectedError, ExpectedErrorWithReason, UnexpectedError}

import instructions.Instr
import stacks.{ArrayStack, CallStack, ErrorStack, HandlerStack, Stack, StateStack}, Stack.StackExt

private [parsley] final class Context(private [machine] var instrs: Array[Instr],
                                      private [machine] val input: String,
                                      numRegs: Int,
                                      private val sourceFile: Option[String]) {
    /** This is the operand stack, where results go to live  */
    private [machine] val stack: ArrayStack[Any] = new ArrayStack()
    /** Current offset into the input */
    private [machine] var offset: Int = 0
    /** The length of the input, stored for whatever reason */
    private [machine] val inputsz: Int = input.length
    /** Call stack consisting of Frames that track the return position and the old instructions */
    private var calls: CallStack = Stack.empty
    /** State stack consisting of offsets and positions that can be rolled back */
    private [machine] var states: StateStack = Stack.empty
    /** Stack consisting of offsets at previous checkpoints, which may query to test for consumed input */
    /** Current operational status of the machine */
    private [machine] var good: Boolean = true
    private [machine] var running: Boolean = true
    /** Stack of handlers, which track the call depth, program counter and stack size of error handlers */
    private [machine] var handlers: HandlerStack = Stack.empty
    /** Current offset into program instruction buffer */
    private [machine] var pc: Int = 0
    /** Current line number */
    private [machine] var line: Int = 1
    /** Current column number */
    private [machine] var col: Int = 1
    /** State held by the registers, AnyRef to allow for `null` */
    private [machine] var regs: Array[AnyRef] = new Array[AnyRef](numRegs)
    /** Amount of indentation to apply to debug combinators output */
    private [machine] var debuglvl: Int = 0

    // NEW ERROR MECHANISMS
    private [machine] var hints: DefuncHints = EmptyHints
    private var hintsValidOffset = 0
    private [machine] var errs: ErrorStack = Stack.empty

    private [machine] def restoreHints(): Unit = {
        val hintFrame = this.handlers
        this.hintsValidOffset = hintFrame.hintOffset
        this.hints = hintFrame.hints
    }

    /* Error Debugging Info */
    private [machine] def inFlightHints: DefuncHints = hints
    private [machine] def inFlightError: DefuncError = errs.error
    private [machine] def currentHintsValidOffset: Int = hintsValidOffset

    /* ERROR RELABELLING BEGIN */
    private [machine] def mergeHints(): Unit = {
        val hintFrame = this.handlers
        if (hintFrame.hintOffset == offset) this.hints = hintFrame.hints.merge(this.hints)
    }
    private [machine] def replaceHint(labels: Iterable[String]): Unit = hints = hints.rename(labels)
    private [machine] def popHints(): Unit = hints = hints.pop
    /* ERROR RELABELLING END */

    def invalidateHints(): Unit = {
        if (hintsValidOffset < offset) {
            hints = EmptyHints
            hintsValidOffset = offset
        }
    }

    private def addErrorToHints(err: DefuncError): Unit = {
        assume(!(!err.isExpectedEmpty) || err.isTrivialError, "not having an empty expected implies you are a trivial error")
        if (/*err.isTrivialError && */ !err.isExpectedEmpty && err.presentationOffset == offset) { // scalastyle:ignore disallow.space.after.token
            // If our new hints have taken place further in the input stream, then they must invalidate the old ones
            invalidateHints()
            hints = hints.addError(err)
        }
    }
    private [machine] def addErrorToHintsAndPop(): Unit = {
        this.addErrorToHints(errs.error)
        this.errs = this.errs.tail
    }
    private [machine] def addHints(expecteds: Set[ExpectItem], unexpectedWidth: Int) = {
        assume(expecteds.nonEmpty, "hints must always be non-empty")
        invalidateHints()
        hints = hints.addError(new ExpectedError(this.offset, this.line, this.col, expecteds, unexpectedWidth)) // TODO: this can be optimised further
    }

    private [machine] def updateCheckOffset() = {
        this.handlers.check = this.offset
    }

    // $COVERAGE-OFF$
    private [machine] def pretty: String = {
        s"""[
           |  stack     = [${stack.mkString(", ")}]
           |  instrs    = ${instrs.toList.mkString("; ")}
           |  input     = ${input.drop(offset)}
           |  pos       = ($line, $col)
           |  status    = $status
           |  pc        = $pc
           |  rets      = ${calls.mkString(", ")}
           |  handlers  = ${handlers.mkString(", ")}
           |  recstates = ${states.mkString(", ")}
           |  registers = ${regs.zipWithIndex.map{case (r, i) => s"r$i = $r"}.toList.mkString("\n              ")}
           |  errors    = ${errs.mkString(", ")}
           |]""".stripMargin
    }
    // $COVERAGE-ON$

    private [parsley] def run[Err: ErrorBuilder, A](): Result[Err, A] = go[Err, A]()
    @tailrec private def go[Err: ErrorBuilder, A](): Result[Err, A] = {
        //println(pretty)
        if (running) { // this is the likeliest branch, so should be executed with fewest comparisons
            instrs(pc)(this)
            go[Err, A]()
        }
        else if (good) {
            assert(stack.size == 1, s"stack must end a parse with exactly one item, it has ${stack.size}")
            assert(calls.isEmpty, "there must be no more calls to unwind on end of parser")
            assert(handlers.isEmpty, "there must be no more handlers on end of parse")
            assert(states.isEmpty, "there must be no residual states left at end of parse")
            assert(errs.isEmpty, "there should be no parse errors remaining at end of parse")
            Success(stack.peek[A])
        }
        else {
            assert(!errs.isEmpty && errs.tail.isEmpty, "there should be exactly 1 parse error remaining at end of parse")
            assert(handlers.isEmpty, "there must be no more handlers on end of parse")
            assert(states.isEmpty, "there must be no residual states left at end of parse")
            Failure(errs.error.asParseError.format(sourceFile))
        }
    }

    private [machine] def call(newInstrs: Array[Instr]): Unit = {
        call(0)
        instrs = newInstrs
    }

    private [machine] def call(at: Int): Unit = {
        calls = new CallStack(pc + 1, instrs, at, calls)
        pc = at
    }

    private [machine] def ret(): Unit = {
        assert(calls != null, "cannot return when no calls are made")
        instrs = calls.instrs
        pc = calls.ret
        calls = calls.tail
    }

    private [machine] def catchNoConsumed(check: Int)(handler: =>Unit): Unit = {
        assert(!good, "catching can only be performed in a handler")
        if (offset != check) {
            handlers = handlers.tail
            fail()
        }
        else {
            good = true
            handler
        }
    }

    private [machine] def pushError(err: DefuncError): Unit = this.errs = new ErrorStack(this.useHints(err), this.errs)
    private [machine] def useHints(err: DefuncError): DefuncError = {
        if (hintsValidOffset == err.presentationOffset) err.withHints(hints)
        else {
            hintsValidOffset = err.presentationOffset
            hints = EmptyHints
            err
        }
    }

    private [machine] def failWithMessage(caretWidth: CaretWidth, msgs: String*): Unit = {
        this.fail(new ClassicFancyError(offset, line, col, caretWidth, msgs: _*))
    }
    private [machine] def unexpectedFail(expected: Iterable[ExpectItem], unexpected: UnexpectDesc): Unit = {
        this.fail(new UnexpectedError(offset, line, col, expected, unexpected))
    }
    private [machine] def expectedFail(expected: Iterable[ExpectItem], unexpectedWidth: Int): Unit = {
        this.fail(new ExpectedError(offset, line, col, expected, unexpectedWidth))
    }
    private [machine] def expectedFailWithReason(expected: Iterable[ExpectItem], reason: String, unexpectedWidth: Int): Unit = {
        this.fail(new ExpectedErrorWithReason(offset, line, col, expected, reason, unexpectedWidth))
    }
    private [machine] def expectedFailWithReason(expected: Iterable[ExpectItem], reason: Option[String], unexpectedWidth: Int): Unit = {
        if (reason.isEmpty) this.expectedFail(expected, unexpectedWidth)
        else this.expectedFailWithReason(expected, reason.get, unexpectedWidth)
    }

    private [machine] def fail(error: DefuncError): Unit = {
        good = false
        this.pushError(error)
        this.fail()
    }
    private [machine] def fail(): Unit = {
        assert(!good, "fail() may only be called in a failing context, use `fail(err)` or set `good = false`")
        if (handlers.isEmpty) running = false
        else {
            val handler = handlers
            instrs = handler.instrs
            calls = handler.calls
            pc = handler.pc
            val diffstack = stack.usize - handler.stacksz
            if (diffstack > 0) stack.drop(diffstack)
        }
    }

    private [machine] def pushAndContinue(x: Any) = {
        stack.push(x)
        inc()
    }
    private [machine] def unsafePushAndContinue(x: Any) = {
        stack.upush(x)
        inc()
    }
    private [machine] def exchangeAndContinue(x: Any) = {
        stack.exchange(x)
        inc()
    }
    private [machine] def inc(): Unit = pc += 1
    private [machine] def peekChar: Char = input.charAt(offset)
    private [machine] def peekChar(lookAhead: Int): Char = input.charAt(offset + lookAhead)
    private [machine] def moreInput: Boolean = offset < inputsz
    private [machine] def moreInput(n: Int): Boolean = offset + (n - 1) < inputsz
    private [machine] def updatePos(c: Char) = c match {
        case '\n' => line += 1; col = 1
        case '\t' => col = ((col + 3) & -4) | 1//((col - 1) | 3) + 2 // scalastyle:ignore magic.number
        case _    => col += 1
    }
    private [machine] def consumeChar(): Char = {
        val c = peekChar
        updatePos(c)
        offset += 1
        c
    }
    private [machine] def fastConsumeSupplementaryChar(): Unit = {
        assert(this.peekChar.isHighSurrogate, "must have a high surrogate to consume supplementary")
        // not going to be a tab or newline
        offset += 2
        col += 1
    }
    private [machine] def fastUncheckedConsumeChars(n: Int): Unit = {
        offset += n
        col += n
    }
    private [machine] def pushHandler(label: Int): Unit = {
        handlers = new HandlerStack(calls, instrs, label, stack.usize, offset, hints, hintsValidOffset, handlers)
    }
    private [machine] def saveState(): Unit = states = new StateStack(offset, line, col, states)
    private [machine] def restoreState(): Unit = {
        val state = states
        states = states.tail
        offset = state.offset
        line = state.line
        col = state.col
    }
    private [machine] def writeReg(reg: Int, x: Any): Unit = {
        regs(reg) = x.asInstanceOf[AnyRef]
    }

    private [machine] def status: Status = {
        if (running) if (good) Good else Recover
        else if (good) Finished else Failed
    }

    private implicit val lineBuilder: LineBuilder = new LineBuilder {
        def nearestNewlineBefore(off: Int): Option[Int] = {
            if (off < 0) None
            else Some {
                val idx = Context.this.input.lastIndexOf('\n', off-1)
                if (idx == -1) 0 else idx + 1
            }
        }
        def nearestNewlineAfter(off: Int): Option[Int] = {
            if (off > Context.this.inputsz) None
            else Some {
                val idx = Context.this.input.indexOf('\n', off)
                if (idx == -1) Context.this.inputsz else idx
            }
        }
        def segmentBetween(start: Int, end: Int): String = {
            Context.this.input.substring(start, end)
        }
    }

    private [machine] implicit val errorItemBuilder: ErrorItemBuilder = new ErrorItemBuilder {
        def inRange(offset: Int): Boolean = offset < Context.this.inputsz
        def codePointAt(offset: Int): Int = Context.this.input.codePointAt(offset)
        //def substring(offset: Int, size: Int): String = Context.this.input.substring(offset, Math.min(offset + size, Context.this.inputsz))
        def iterableFrom(offset: Int): IndexedSeq[Char] = Context.this.input.substring(offset)
    }
}
