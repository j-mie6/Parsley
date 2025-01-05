/*
 * Copyright 2020 Parsley Contributors <https://github.com/j-mie6/Parsley/graphs/contributors>
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package parsley.internal.machine.instructions

import parsley.token.errors.LabelConfig

import parsley.internal.errors.ExpectDesc
import parsley.internal.machine.Context
import parsley.internal.machine.XAssert._

private [internal] final class Satisfies(f: Char => Boolean, expected: Iterable[ExpectDesc]) extends Instr {
    def this(f: Char => Boolean, expected: LabelConfig) = this(f, expected.asExpectDescs)
    override def apply(ctx: Context): Unit = {
        ensureRegularInstruction(ctx)
        if (ctx.moreInput && f(ctx.peekChar)) ctx.pushAndContinue(ctx.consumeChar())
        else ctx.expectedFail(expected, unexpectedWidth = 1)
    }
    // $COVERAGE-OFF$
    override def toString: String = "Sat(?)"
    // $COVERAGE-ON$
}

private [internal] object RestoreAndFail extends Instr {
    override def apply(ctx: Context): Unit = {
        ensureHandlerInstruction(ctx)
        ctx.handlers = ctx.handlers.tail
        // Pop input off head then fail to next handler
        ctx.restoreState()
        ctx.fail()
    }
    // $COVERAGE-OFF$
    override def toString: String = "RestoreAndFail"
    // $COVERAGE-ON$
}

private [internal] object RestoreHintsAndState extends Instr {
    override def apply(ctx: Context): Unit = {
        ensureRegularInstruction(ctx)
        ctx.restoreHints()
        ctx.restoreState()
        ctx.handlers = ctx.handlers.tail
        ctx.inc()
    }
    // $COVERAGE-OFF$
    override def toString: String = "RestoreHintsAndState"
    // $COVERAGE-ON$
}

private [internal] object PopStateAndFail extends Instr {
    override def apply(ctx: Context): Unit = {
        ensureHandlerInstruction(ctx)
        ctx.handlers = ctx.handlers.tail
        ctx.states = ctx.states.tail
        ctx.fail()
    }
    // $COVERAGE-OFF$
    override def toString: String = "PopStateAndFail"
    // $COVERAGE-ON$
}

private [internal] object PopStateRestoreHintsAndFail extends Instr {
    override def apply(ctx: Context): Unit = {
        ensureHandlerInstruction(ctx)
        ctx.restoreHints()
        ctx.handlers = ctx.handlers.tail
        ctx.states = ctx.states.tail
        ctx.fail()
    }
    // $COVERAGE-OFF$
    override def toString: String = "PopStateRestoreHintsAndFail"
    // $COVERAGE-ON$
}

// Position Extractors
private [internal] object Line extends Instr {
    override def apply(ctx: Context): Unit = {
        ensureRegularInstruction(ctx)
        ctx.pushAndContinue(ctx.line)
    }
    // $COVERAGE-OFF$
    override def toString: String = "Line"
    // $COVERAGE-ON$
}

private [internal] object Col extends Instr {
    override def apply(ctx: Context): Unit = {
        ensureRegularInstruction(ctx)
        ctx.pushAndContinue(ctx.col)
    }
    // $COVERAGE-OFF$
    override def toString: String = "Col"
    // $COVERAGE-ON$
}

private [internal] object Offset extends Instr {
    override def apply(ctx: Context): Unit = {
        ensureRegularInstruction(ctx)
        ctx.pushAndContinue(ctx.offset)
    }
    // $COVERAGE-OFF$
    override def toString: String = "Offset"
    // $COVERAGE-ON$
}

// Register-Manipulators
private [internal] final class Get(reg: Int) extends Instr {
    override def apply(ctx: Context): Unit = {
        ensureRegularInstruction(ctx)
        ctx.pushAndContinue(ctx.regs(reg))
    }
    // $COVERAGE-OFF$
    override def toString: String = s"Get(r$reg)"
    // $COVERAGE-ON$
}

private [internal] final class Put(reg: Int) extends Instr {
    override def apply(ctx: Context): Unit = {
        ensureRegularInstruction(ctx)
        ctx.writeReg(reg, ctx.stack.upop())
        ctx.inc()
    }
    // $COVERAGE-OFF$
    override def toString: String = s"Put(r$reg)"
    // $COVERAGE-ON$
}

private [internal] final class PutAndFail(reg: Int) extends Instr {
    override def apply(ctx: Context): Unit = {
        ensureHandlerInstruction(ctx)
        ctx.handlers = ctx.handlers.tail
        ctx.writeReg(reg, ctx.stack.upeek)
        ctx.fail()
    }
    // $COVERAGE-OFF$
    override def toString: String = s"PutAndFail(r$reg)"
    // $COVERAGE-ON$
}

private [internal] object Span extends Instr {
    override def apply(ctx: Context): Unit = {
        // this uses the state stack because post #132 we will need a save point to obtain the start of the input
        ensureRegularInstruction(ctx)
        val startOffset = ctx.states.offset
        ctx.states = ctx.states.tail
        ctx.handlers = ctx.handlers.tail
        ctx.pushAndContinue(ctx.input.substring(startOffset, ctx.offset))
    }
    // $COVERAGE-OFF$
    override def toString: String = "Span"
    // $COVERAGE-ON$
}

private [parsley] final class ExpandRefs(newSz: Int) extends Instr {
    override def apply(ctx: Context): Unit = {
        if (newSz > ctx.regs.size) {
            ctx.regs = java.util.Arrays.copyOf(ctx.regs, newSz)
        }
        ctx.inc()
    }
}
