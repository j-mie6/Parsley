/* SPDX-FileCopyrightText: © 2020 Parsley Contributors <https://github.com/j-mie6/Parsley/graphs/contributors>
 * SPDX-License-Identifier: BSD-3-Clause
 */
package parsley.internal.machine.instructions

import scala.collection.mutable.ListBuffer

import parsley.internal.deepembedding
import parsley.internal.machine.{Context, Good}
import parsley.internal.machine.stacks.{HandlerStack, Stack}, Stack.StackExt

private [internal] final class Many(var label: Int) extends InstrWithLabel with Stateful {
    private [this] val acc: ListBuffer[Any] = ListBuffer.empty
    override def apply(ctx: Context): Unit = {
        if (ctx.status eq Good) {
            acc += ctx.stack.upop()
            ctx.updateCheckOffsetAndHints()
            ctx.pc = label
        }
        // If the head of input stack is not the same size as the head of check stack, we fail to next handler
        else {
            ctx.catchNoConsumed {
                ctx.addErrorToHintsAndPop()
                ctx.pushAndContinue(acc.toList)
            }
            acc.clear()
        }
    }
    // $COVERAGE-OFF$
    override def toString: String = s"Many($label)"
    // $COVERAGE-ON$
    override def copy: Many = new Many(label)
}
private [internal] final class SkipMany(var label: Int) extends InstrWithLabel {
    override def apply(ctx: Context): Unit = {
        if (ctx.status eq Good) {
            ctx.stack.pop_()
            ctx.updateCheckOffsetAndHints()
            ctx.pc = label
        }
        // If the head of input stack is not the same size as the head of check stack, we fail to next handler
        else ctx.catchNoConsumed {
            ctx.addErrorToHintsAndPop()
            ctx.pushAndContinue(())
        }
    }
    // $COVERAGE-OFF$
    override def toString: String = s"SkipMany($label)"
    // $COVERAGE-ON$
}

private [internal] final class ChainPost(var label: Int) extends InstrWithLabel with Stateful {
    private [this] var acc: Any = _
    override def apply(ctx: Context): Unit = {
        if (ctx.status eq Good) {
            // When acc is null, we are entering the instruction for the first time, a p will be on the stack
            if (acc == null) {
                // after this point, the inputCheck will roll back one too many items on the stack, because this item
                // was consumed. It should be adjusted
                val op = ctx.stack.upop()
                acc = ctx.stack.upeek
                ctx.stack.exchange(op)
                ctx.handlers.stacksz -= 1
            }
            acc = ctx.stack.pop[Any => Any]()(acc)
            ctx.updateCheckOffsetAndHints()
            ctx.pc = label
        }
        // If the head of input stack is not the same size as the head of check stack, we fail to next handler
        else {
            ctx.catchNoConsumed {
                ctx.addErrorToHintsAndPop()
                // When acc is null, we have entered for first time but the op failed, so the result is already on the stack
                if (acc != null) ctx.stack.push(acc)
                ctx.inc()
            }
            acc = null
        }
    }
    // $COVERAGE-OFF$
    override def toString: String = s"ChainPost($label)"
    // $COVERAGE-ON$
    override def copy: ChainPost = new ChainPost(label)
}

private [internal] final class ChainPre(var label: Int) extends InstrWithLabel with Stateful {
    private var acc: Any => Any = _
    override def apply(ctx: Context): Unit = {
        if (ctx.status eq Good) {
            // If acc is null we are entering the instruction, so nothing to compose, this saves on an identity call
            acc = if (acc == null) ctx.stack.pop[Any => Any]()
                  // We perform the acc after the tos function; the tos function is "closer" to the final p
                  else ctx.stack.pop[Any => Any]().andThen(acc)
            ctx.updateCheckOffsetAndHints()
            ctx.pc = label
        }
        // If the head of input stack is not the same size as the head of check stack, we fail to next handler
        else {
            ctx.catchNoConsumed {
                ctx.addErrorToHintsAndPop()
                ctx.pushAndContinue(if (acc == null) identity[Any] _ else acc)
            }
            acc = null
        }
    }
    // $COVERAGE-OFF$
    override def toString: String = s"ChainPre($label)"
    // $COVERAGE-ON$
    override def copy: ChainPre = new ChainPre(label)
}
private [internal] final class Chainl(var label: Int) extends InstrWithLabel with Stateful {
    private [this] var acc: Any = _
    override def apply(ctx: Context): Unit = {
        if (ctx.status eq Good) {
            val y = ctx.stack.upop()
            val op = ctx.stack.pop[(Any, Any) => Any]()
            // When acc is null, we are entering the instruction for the first time, a p will be on the stack
            if (acc == null) {
                // after this point, the inputCheck will roll back one too many items on the stack, because this item
                // was consumed. It should be adjusted
                acc = op(ctx.stack.upop(), y)
                ctx.handlers.stacksz -= 1
            }
            else acc = op(acc, y)
            ctx.updateCheckOffsetAndHints()
            ctx.pc = label
        }
        // If the head of input stack is not the same size as the head of check stack, we fail to next handler
        else {
            ctx.catchNoConsumed {
                ctx.addErrorToHintsAndPop()
                // if acc is null this is first entry, p already on the stack!
                if (acc != null) ctx.pushAndContinue(acc)
                else ctx.inc()
            }
            acc = null
        }
    }
    // $COVERAGE-OFF$
    override def toString: String = s"Chainl($label)"
    // $COVERAGE-ON$
    override def copy: Chainl = new Chainl(label)
}

private [instructions] sealed trait DualHandler {
    final protected def checkForFirstHandlerAndPop(ctx: Context, otherwise: =>Unit)(action: =>Unit) = {
        if (!ctx.handlers.isEmpty && ctx.handlers.pc == ctx.pc) {
            ctx.handlers = ctx.handlers.tail
            action
        }
        else otherwise
        ctx.checkStack = ctx.checkStack.tail
    }
    final protected def popSecondHandlerAndJump(ctx: Context, label: Int) = {
        ctx.handlers = ctx.handlers.tail
        ctx.checkStack = ctx.checkStack.tail
        ctx.updateCheckOffsetAndHints()
        ctx.pc = label
    }
}
private [internal] final class Chainr[A, B](var label: Int, _wrap: A => B) extends InstrWithLabel with DualHandler with Stateful {
    private [this] val wrap: Any => B = _wrap.asInstanceOf[Any => B]
    private [this] var acc: Any => Any = _
    override def apply(ctx: Context): Unit = {
        if (ctx.status eq Good) {
            val f = ctx.stack.pop[(Any, Any) => Any]()
            val x = ctx.stack.upop()
            // If acc is null we are entering the instruction, so nothing to compose, this saves on an identity call
            if (acc == null) acc = (y: Any) => f(x, y)
            // We perform the acc after the tos function; the tos function is "closer" to the final p
            else {
                // This must be bound here to avoid late binding issues
                val acc_ = acc
                acc = (y: Any) => acc_(f(x, y))
            }
            popSecondHandlerAndJump(ctx, label)
        }
        // If the head of input stack is not the same size as the head of check stack, we fail to next handler
        else {
            // presence of first handler indicates p succeeded and op didn't
            checkForFirstHandlerAndPop(ctx, ctx.fail()) {
                ctx.catchNoConsumed {
                    ctx.addErrorToHintsAndPop()
                    ctx.exchangeAndContinue(if (acc != null) acc(wrap(ctx.stack.upeek)) else wrap(ctx.stack.upeek))
                }
            }
            acc = null
        }
    }
    // $COVERAGE-OFF$
    override def toString: String = s"Chainr($label)"
    // $COVERAGE-ON$
    override def copy: Chainr[A, B] = new Chainr(label, wrap)
}

private [internal] final class SepEndBy1(var label: Int) extends InstrWithLabel with DualHandler with Stateful {
    private [this] val acc: ListBuffer[Any] = ListBuffer.empty
    override def apply(ctx: Context): Unit = {
        if (ctx.status eq Good) {
            ctx.stack.pop_()
            acc += ctx.stack.upop()
            popSecondHandlerAndJump(ctx, label)
        }
        else {
            val check = ctx.checkStack.offset
            // presence of first handler indicates p succeeded and sep didn't
            checkForFirstHandlerAndPop(ctx, ()) {
                acc += ctx.stack.upop()
                ctx.checkStack = ctx.checkStack.tail
            }
            if (ctx.offset != check || acc.isEmpty) ctx.fail()
            else {
                ctx.addErrorToHintsAndPop()
                ctx.status = Good
                ctx.pushAndContinue(acc.toList)
            }
            acc.clear()
        }
    }
    // $COVERAGE-OFF$
    override def toString: String = s"SepEndBy1($label)"
    // $COVERAGE-ON$
    override def copy: SepEndBy1 = new SepEndBy1(label)
}

private [internal] final class ManyUntil(var label: Int) extends InstrWithLabel with Stateful {
    private [this] val acc: ListBuffer[Any] = ListBuffer.empty
    override def apply(ctx: Context): Unit = {
        if (ctx.status eq Good) {
            val x = ctx.stack.upop()
            if (x == parsley.combinator.ManyUntil.Stop) {
                ctx.unsafePushAndContinue(acc.toList)
                acc.clear()
                ctx.handlers = ctx.handlers.tail
            }
            else {
                acc += x
                ctx.pc = label
            }
        }
        // ManyUntil is a fallthrough handler, it must be visited during failure, but does nothing to the external state
        else { acc.clear(); ctx.fail() }
    }
    // $COVERAGE-OFF$
    override def toString: String = s"ManyUntil($label)"
    // $COVERAGE-ON$
    override def copy: ManyUntil = new ManyUntil(label)
}
