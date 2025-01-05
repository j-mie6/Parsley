/*
 * Copyright 2020 Parsley Contributors <https://github.com/j-mie6/Parsley/graphs/contributors>
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package parsley.internal.machine.instructions.debug

import parsley.debug.ParseAttempt
import parsley.debug.internal.{DebugContext, DivergenceContext}

import parsley.internal.deepembedding.frontend.LazyParsley
import parsley.internal.machine.Context
import parsley.internal.machine.instructions.{Instr, InstrWithLabel}
import parsley.internal.machine.XAssert._

import parsley.internal.machine.stacks.Stack.StackExt

// Enter into the scope of a parser in the current context.
private [internal] class EnterParser(var label: Int, origin: LazyParsley[_], userAssignedName: Option[String])(dbgCtx: DebugContext) extends InstrWithLabel {
    override def apply(ctx: Context): Unit = {
        ensureRegularInstruction(ctx)
        // Uncomment to debug entries and exits.
        // println(s"Entering ${origin.prettyName} (@ ${ctx.pc} -> Exit @ $label)")

        // I think we can get away with executing this unconditionally.
        dbgCtx.push(ctx.input, origin, userAssignedName)
        // Using my own state tracker instead.
        dbgCtx.pushPos(ctx.offset, ctx.line, ctx.col)
        ctx.pushHandler(label) // Mark the AddAttempt instruction as an exit handler.
        ctx.inc()
    }

    // $COVERAGE-OFF$
    override def toString: String = s"EnterParser(exit: $label)"
    // $COVERAGE-ON$
}

// Add a parse attempt to the current context at the current callstack point, and leave the current
// parser's scope.
private [internal] class AddAttemptAndLeave(dbgCtx: DebugContext) extends Instr {
    override def apply(ctx: Context): Unit = {
        // Uncomment to debug entries and exits.
        // println(s"Leaving ${if (ctx.good) "OK" else "FAIL" } (@ ${ctx.pc})")

        // These offsets will be needed to slice the specific part of the input that the parser has
        // attempted to parse during its attempt.
        val (prevOffset, prevLine, prevCol) = dbgCtx.popPos()
        val currentOff = ctx.offset
        val prevPos = (prevLine, prevCol)

        // Slice based on current offset to see what a parser has attempted to parse,
        // and the 'good' member should indicate whether the previous parser has succeeded or not.
        val success = ctx.good
        val input = ctx.input.slice(prevOffset, currentOff)

        // Construct a new parse attempt and add it in.
        // XXX: Cast to Any required as otherwise the Some creation is treated as dead code.
        dbgCtx.addParseAttempt {
            val res =
                if (success) Some(ctx.stack.upeek match {
                    case f if dbgCtx.shouldString(f) => f.toString // Closures and lambdas are expensive!
                    case x                           => x
                }) else None
            new ParseAttempt(inp = input, fof = prevOffset, tof = currentOff, fps = prevPos, tps = (ctx.line, ctx.col), res = res)
        }

        dbgCtx.pop()

        // Fail if the current context is not good, as required by how Parsley's machine functions.
        ctx.handlers = ctx.handlers.tail
        if (success) ctx.inc() else ctx.fail()
    }

    // $COVERAGE-OFF$
    override def toString: String = "AddAttemptAndLeave"
    // $COVERAGE-ON$
}

private [internal] class TakeSnapshot(var label: Int, origin: LazyParsley[_], userAssignedName: Option[String])(dtx: DivergenceContext) extends InstrWithLabel {
    override def apply(ctx: Context): Unit = {
        ensureRegularInstruction(ctx)
        val handler = ctx.handlers
        ctx.pushHandler(label)
        val ctxSnap = dtx.CtxSnap(ctx.pc, ctx.instrs, ctx.offset, ctx.regs.toList)
        dtx.takeSnapshot(origin, userAssignedName, ctxSnap, if (handler.isEmpty) None else Some(dtx.HandlerSnap(handler.pc, handler.instrs)))
        ctx.inc()
    }

    // $COVERAGE-OFF$
    override def toString: String = s"TakeSnapshot(until: $label)"
    // $COVERAGE-ON$
}

private [internal] class DropSnapshot(dtx: DivergenceContext) extends Instr {
    override def apply(ctx: Context): Unit = {
        dtx.dropSnapshot()
        ctx.handlers = ctx.handlers.tail
        if (ctx.good) ctx.inc() else ctx.fail()
    }

    // $COVERAGE-OFF$
    override def toString: String = "DropSnapshot"
    // $COVERAGE-ON$
}
