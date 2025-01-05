/*
 * Copyright 2020 Parsley Contributors <https://github.com/j-mie6/Parsley/graphs/contributors>
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package parsley.internal.machine.instructions

import scala.collection.mutable

import parsley.XAssert._

import parsley.internal.machine.Context
import parsley.internal.machine.XAssert._

private [internal] final class Many(var label: Int) extends InstrWithLabel {
    override def apply(ctx: Context): Unit = {
        if (ctx.good) {
            val x = ctx.stack.upop()
            ctx.stack.peek[mutable.Builder[Any, Any]] += x
            ctx.updateCheckOffset()
            ctx.pc = label
        }
        // If the head of input stack is not the same size as the head of check stack, we fail to next handler
        else ctx.catchNoConsumed(ctx.handlers.check) {
            ctx.handlers = ctx.handlers.tail
            ctx.addErrorToHintsAndPop()
            ctx.exchangeAndContinue(ctx.stack.peek[mutable.Builder[Any, Any]].result())
        }
    }
    // $COVERAGE-OFF$
    override def toString: String = s"Many($label)"
    // $COVERAGE-ON$
}

// TODO: Factor these handlers out!
private [internal] final class SkipMany(var label: Int) extends InstrWithLabel {
    override def apply(ctx: Context): Unit = {
        if (ctx.good) {
            ctx.updateCheckOffset()
            ctx.pc = label
        }
        // If the head of input stack is not the same size as the head of check stack, we fail to next handler
        else ctx.catchNoConsumed(ctx.handlers.check) {
            ctx.handlers = ctx.handlers.tail
            ctx.addErrorToHintsAndPop()
            ctx.inc()
        }
    }
    // $COVERAGE-OFF$
    override def toString: String = s"SkipMany($label)"
    // $COVERAGE-ON$
}

private [internal] final class ChainPost(var label: Int) extends InstrWithLabel {
    override def apply(ctx: Context): Unit = {
        if (ctx.good) {
            val op = ctx.stack.pop[Any => Any]()
            ctx.stack.exchange(op(ctx.stack.upeek))
            ctx.updateCheckOffset()
            ctx.pc = label
        }
        // If the head of input stack is not the same size as the head of check stack, we fail to next handler
        else ctx.catchNoConsumed(ctx.handlers.check) {
            ctx.handlers = ctx.handlers.tail
            ctx.addErrorToHintsAndPop()
            ctx.inc()
        }
    }
    // $COVERAGE-OFF$
    override def toString: String = s"ChainPost($label)"
    // $COVERAGE-ON$
}

private [internal] final class ChainPre(var label: Int) extends InstrWithLabel {
    override def apply(ctx: Context): Unit = {
        if (ctx.good) {
            val f = ctx.stack.pop[Any => Any]()
            ctx.stack.exchange(f.andThen(ctx.stack.peek[Any => Any]))
            ctx.updateCheckOffset()
            ctx.pc = label
        }
        // If the head of input stack is not the same size as the head of check stack, we fail to next handler
        else ctx.catchNoConsumed(ctx.handlers.check) {
            ctx.handlers = ctx.handlers.tail
            ctx.addErrorToHintsAndPop()
            ctx.inc()
        }
    }
    // $COVERAGE-OFF$
    override def toString: String = s"ChainPre($label)"
    // $COVERAGE-ON$
}
private [internal] final class Chainl(var label: Int) extends InstrWithLabel {
    override def apply(ctx: Context): Unit = {
        if (ctx.good) {
            val y = ctx.stack.upop()
            val op = ctx.stack.pop[(Any, Any) => Any]()
            ctx.stack.exchange(op(ctx.stack.peek[Any], y))
            ctx.updateCheckOffset()
            ctx.pc = label
        }
        // If the head of input stack is not the same size as the head of check stack, we fail to next handler
        else ctx.catchNoConsumed(ctx.handlers.check) {
            ctx.handlers = ctx.handlers.tail
            ctx.addErrorToHintsAndPop()
            ctx.inc()
        }
    }
    // $COVERAGE-OFF$
    override def toString: String = s"Chainl($label)"
    // $COVERAGE-ON$
}

private [internal] final class ChainrJump(var label: Int) extends InstrWithLabel {
    override def apply(ctx: Context): Unit = {
        ensureRegularInstruction(ctx)
        val f = ctx.stack.pop[(Any, Any) => Any]()
        val x = ctx.stack.upop()
        val acc = ctx.stack.peek[Any => Any]
        // We perform the acc after the tos function; the tos function is "closer" to the final p
        ctx.stack.exchange((y: Any) => acc(f(x, y)))
        ctx.handlers = ctx.handlers.tail
        ctx.pc = label
    }

    // $COVERAGE-OFF$
    override def toString: String = s"ChainrJump($label)"
    // $COVERAGE-ON$
}

private [internal] final class ChainrOpHandler(wrap: Any => Any) extends Instr {
    override def apply(ctx: Context): Unit = {
        ensureHandlerInstruction(ctx)
        ctx.catchNoConsumed(ctx.handlers.check) {
            ctx.handlers = ctx.handlers.tail
            ctx.addErrorToHintsAndPop()
            val y = ctx.stack.upop()
            ctx.exchangeAndContinue(ctx.stack.peek[Any => Any](wrap(y)))
        }
    }

    // $COVERAGE-OFF$
    override def toString: String = "ChainrOpHandler"
    // $COVERAGE-ON$
}
private [internal] object ChainrOpHandler  {
    def apply[A, B](wrap: A => B): ChainrOpHandler = new ChainrOpHandler(wrap.asInstanceOf[Any => Any])
}

private [internal] final class SepEndBy1Jump(var label: Int) extends InstrWithLabel {
    override def apply(ctx: Context): Unit = {
        ensureRegularInstruction(ctx)
        val x = ctx.stack.upop()
        ctx.stack.pop_() // the bool
        ctx.stack.peek[mutable.Builder[Any, Any]] += x
        ctx.stack.upush(true)
        // pop second handler and jump
        ctx.handlers = ctx.handlers.tail
        ctx.updateCheckOffset()
        ctx.pc = label
    }

    // $COVERAGE-OFF$
    override def toString: String = s"SepEndBy1Jump($label)"
    // $COVERAGE-ON$
}

private [instructions] object SepEndBy1Handlers {
    def pushAccWhenCheckValidAndContinue(ctx: Context, check: Int, acc: mutable.Builder[Any, Any], readP: Boolean): Unit = {
        if (ctx.offset != check || !readP) ctx.fail()
        else {
            ctx.addErrorToHintsAndPop()
            ctx.good = true
            ctx.exchangeAndContinue(acc.result())
        }
    }
}

private [internal] object SepEndBy1SepHandler extends Instr {
    override def apply(ctx: Context): Unit = {
        ensureHandlerInstruction(ctx)
        val check = ctx.handlers.check
        ctx.handlers = ctx.handlers.tail
        // p succeeded and sep didn't, so push p and fall-through to the whole handler
        val x = ctx.stack.upop()
        ctx.stack.pop_() // the bool is no longer needed
        val acc = ctx.stack.peek[mutable.Builder[Any, Any]]
        acc += x
        // discard the other handler and increment so that we are sat on the other handler
        assert(ctx.instrs(ctx.pc + 1) eq SepEndBy1WholeHandler, "the next instruction from the sep handler must be the whole handler")
        assert(ctx.handlers.pc == ctx.pc + 1, "the top-most handler must be the whole handler in the sep handler")
        ctx.handlers = ctx.handlers.tail
        ctx.inc()
        SepEndBy1Handlers.pushAccWhenCheckValidAndContinue(ctx, check, acc, readP = true)
    }

    // $COVERAGE-OFF$
    override def toString: String = "SepEndBy1SepHandler"
    // $COVERAGE-ON$
}

private [internal] object SepEndBy1WholeHandler extends Instr {
    override def apply(ctx: Context): Unit = {
        ensureHandlerInstruction(ctx)
        val check = ctx.handlers.check
        ctx.handlers = ctx.handlers.tail
        val readP = ctx.stack.pop[Boolean]()
        SepEndBy1Handlers.pushAccWhenCheckValidAndContinue(ctx, check, ctx.stack.peek[mutable.Builder[Any, Any]], readP)
    }

    // $COVERAGE-OFF$
    override def toString: String = "SepEndBy1WholeHandler"
    // $COVERAGE-ON$
}

private [internal] final class ManyUntil(var label: Int) extends InstrWithLabel {
    override def apply(ctx: Context): Unit = {
        ensureRegularInstruction(ctx)
        ctx.stack.upop() match {
            case parsley.combinator.ManyUntil.Stop => ctx.exchangeAndContinue(ctx.stack.peek[mutable.Builder[Any, Any]].result())
            case x =>
                ctx.stack.peek[mutable.Builder[Any, Any]] += x
                ctx.pc = label
        }
    }
    // $COVERAGE-OFF$
    override def toString: String = s"ManyUntil($label)"
    // $COVERAGE-ON$
}

private [internal] final class SkipManyUntil(var label: Int) extends InstrWithLabel {
    override def apply(ctx: Context): Unit = {
        ensureRegularInstruction(ctx)
        ctx.stack.upop() match {
            case parsley.combinator.ManyUntil.Stop => ctx.inc()
            case _ => ctx.pc = label
        }
    }
    // $COVERAGE-OFF$
    override def toString: String = s"SkipManyUntil($label)"
    // $COVERAGE-ON$
}
