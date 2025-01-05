/*
 * Copyright 2020 Parsley Contributors <https://github.com/j-mie6/Parsley/graphs/contributors>
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package parsley.internal.deepembedding.backend

import parsley.debug.{Breakpoint, EntryBreak, ExitBreak, FullBreak, Profiler}
import parsley.errors.ErrorBuilder
import parsley.state.Ref

import parsley.internal.deepembedding.ContOps, ContOps.{ContAdapter, result, suspend}
import parsley.internal.deepembedding.singletons._
import parsley.internal.machine.instructions

import StrictParsley.InstrBuffer
import org.typelevel.scalaccompat.annotation.nowarn3

private [deepembedding] final class Atomic[A](val p: StrictParsley[A]) extends ScopedUnaryWithState[A, A] {
    override val instr: instructions.Instr = instructions.PopHandlerAndState
    override def instrNeedsLabel: Boolean = false
    override def handlerLabel(state: CodeGenState): Int  = state.getLabel(instructions.RestoreAndFail)
    override def optimise: StrictParsley[A] = p match {
        case p: CharTok[_] => p
        case p: Atomic[_] => p
        //case StringTok(s, _) if s.size == 1 => p
        case _ => this
    }
    // $COVERAGE-OFF$
    final override def pretty(p: String): String = s"atomic($p)"
    // $COVERAGE-ON$
}
private [deepembedding] final class Look[A](val p: StrictParsley[A]) extends ScopedUnaryWithState[A, A] {
    override val instr: instructions.Instr = instructions.RestoreHintsAndState
    override def instrNeedsLabel: Boolean = false
    override def handlerLabel(state: CodeGenState): Int  = state.getLabel(instructions.PopStateRestoreHintsAndFail)
    // $COVERAGE-OFF$
    final override def pretty(p: String): String = s"lookAhead($p)"
    // $COVERAGE-ON$
}
private [deepembedding] final class NotFollowedBy[A](val p: StrictParsley[A]) extends Unary[A, Unit] {
    final override def codeGen[M[_, +_]: ContOps, R](producesResults: Boolean)(implicit instrs: InstrBuffer, state: CodeGenState): M[R, Unit] = {
        val handler = state.freshLabel()
        instrs += new instructions.PushHandlerAndState(handler)
        suspend[M, R, Unit](p.codeGen(producesResults = false)) |> {
            instrs += instructions.NegLookFail
            instrs += new instructions.Label(handler)
            instrs += instructions.NegLookGood
            if (producesResults) instrs += instructions.Push.Unit
        }
    }
    // $COVERAGE-OFF$
    final override def pretty(p: String): String = s"notFollowedBy($p)"
    // $COVERAGE-ON$
}

private [deepembedding] final class Let[A] extends StrictParsley[A] {
    def inlinable: Boolean = true
    override def codeGen[M[_, +_]: ContOps, R](producesResults: Boolean)(implicit instrs: InstrBuffer, state: CodeGenState): M[R, Unit] = result {
        instrs += new instructions.Call(state.getLabel(this, producesResults))
    }

    // $COVERAGE-OFF$
    def pretty: String = this.toString
    // $COVERAGE-ON$
}
private [deepembedding] final class Impure[A](p: StrictParsley[A]) extends StrictParsley[A] {
    def inlinable: Boolean = p.inlinable
    override def codeGen[M[_, +_]: ContOps, R](producesResults: Boolean)(implicit instrs: InstrBuffer, state: CodeGenState): M[R,Unit] = {
        // this blocks result suppression, because the ErrorGen combinators have non-inspectible control flow
        p.codeGen(producesResults = true) |> {
            if (!producesResults) instrs += instructions.Pop
        }
    }
    // $COVERAGE-OFF$
    def pretty: String = p.pretty
    // $COVERAGE-ON$
}

private [deepembedding] final class Put[S](reg: Ref[S], val p: StrictParsley[S]) extends Unary[S, Unit] {
    override def codeGen[M[_, +_]: ContOps, R](producesResults: Boolean)(implicit instrs: InstrBuffer, state: CodeGenState): M[R, Unit] = {
        suspend(p.codeGen[M, R](producesResults = true)) |> {
            instrs += new instructions.Put(reg.addr)
            if (producesResults) instrs += instructions.Push.Unit
        }
    }
    // $COVERAGE-OFF$
    final override def pretty(p: String): String = s"put(r${reg.addr}, $p)"
    // $COVERAGE-ON$
}

private [deepembedding] final class NewReg[S, A](reg: Ref[S], init: StrictParsley[S], body: StrictParsley[A]) extends StrictParsley[A] {
    def inlinable: Boolean = false
    override def codeGen[M[_, +_]: ContOps, R](producesResults: Boolean)(implicit instrs: InstrBuffer, state: CodeGenState): M[R, Unit] = {
        val handler = state.getLabelForPutAndFail(reg)
        suspend(init.codeGen[M, R](producesResults = true)) >> {
            instrs += new instructions.Get(reg.addr)
            instrs += new instructions.SwapAndPut(reg.addr)
            instrs += new instructions.PushHandler(handler)
            suspend(body.codeGen[M, R](producesResults)) |> {
                instrs += (if (producesResults) new instructions.SwapAndPut(reg.addr) else new instructions.Put(reg.addr))
                instrs += instructions.PopHandler
            }
        }
    }
    // $COVERAGE-OFF$
    final override def pretty: String = s"newreg(r${reg.addr}, ${init.pretty}, ${body.pretty})"
    // $COVERAGE-ON$
}

private [deepembedding] final class Span(p: StrictParsley[_]) extends StrictParsley[String] {
    def inlinable: Boolean = false
    override def codeGen[M[_, +_]: ContOps, R](producesResults: Boolean)(implicit instrs: InstrBuffer, state: CodeGenState): M[R, Unit] = {
        if (producesResults) {
            val handler = state.getLabel(instructions.PopStateAndFail)
            instrs += new instructions.PushHandlerAndState(handler)
            suspend[M, R, Unit](p.codeGen(producesResults = false)) |> {
                instrs += instructions.Span
            }
        }
        else p.codeGen(producesResults = false)
    }
    // $COVERAGE-OFF$
    final override def pretty: String = s"${p.pretty}.span"
    // $COVERAGE-ON$
}

// $COVERAGE-OFF$
private [deepembedding] final class Debug[A](val p: StrictParsley[A], name: String, ascii: Boolean,
                                             break: Breakpoint, watchedRefs: scala.Seq[(Ref[_], String)] @nowarn3)
    extends Unary[A, A] {
    override def codeGen[M[_, +_]: ContOps, R](producesResults: Boolean)(implicit instrs: InstrBuffer, state: CodeGenState): M[R, Unit] = {
        val watchedAddrs = watchedRefs.map {
            case (r, name) => (r.addr, name)
        }: @nowarn3
        val handler = state.freshLabel()
        instrs += new instructions.LogBegin(handler, name, ascii, (break eq EntryBreak) || (break eq FullBreak), watchedAddrs)
        suspend(p.codeGen[M, R](producesResults)) |> {
            instrs += new instructions.Label(handler)
            instrs += new instructions.LogEnd(name, ascii, (break eq ExitBreak) || (break eq FullBreak), watchedAddrs)
        }
    }
    final override def pretty(p: String): String = p
}

private [deepembedding] final class DebugError[A](val p: StrictParsley[A], name: String, ascii: Boolean, errBuilder: ErrorBuilder[_]) extends Unary[A, A] {
    override def codeGen[M[_, +_]: ContOps, R](producesResults: Boolean)(implicit instrs: InstrBuffer, state: CodeGenState): M[R, Unit] = {
        val handler = state.freshLabel()
        instrs += new instructions.LogErrBegin(handler, name, ascii)(errBuilder)
        suspend(p.codeGen[M, R](producesResults)) |> {
            if (producesResults) instrs += instructions.Swap
            instrs += new instructions.Label(handler)
            instrs += new instructions.LogErrEnd(name, ascii)(errBuilder)
        }
    }
    final override def pretty(p: String): String = p
}

private [deepembedding] final class Profile[A](val p: StrictParsley[A], name: String, profiler: Profiler) extends Unary[A, A] {
    override def codeGen[M[_, +_]: ContOps, R](producesResults: Boolean)(implicit instrs: InstrBuffer, state: CodeGenState): M[R,Unit] = {
        val handler = state.freshLabel()
        instrs += new instructions.ProfileEnter(handler, name, profiler)
        suspend(p.codeGen[M, R](producesResults)) |> {
            instrs += new instructions.Label(handler)
            instrs += new instructions.ProfileExit(name, profiler)
        }
    }
    final override def pretty(p: String): String = p
}
private [backend] object Profile {
    def unapply[A](p: Profile[A]): Some[StrictParsley[A]] = Some(p.p)
}
// $COVERAGE-ON$

private [backend] object Atomic {
    def unapply[A](self: Atomic[A]): Some[StrictParsley[A]] = Some(self.p)
}
