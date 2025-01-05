/*
 * Copyright 2020 Parsley Contributors <https://github.com/j-mie6/Parsley/graphs/contributors>
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package parsley.internal.deepembedding.backend

import scala.collection.Factory

import parsley.exceptions.NonProductiveIterationException

import parsley.internal.deepembedding.ContOps, ContOps.{ContAdapter, suspend}
import parsley.internal.deepembedding.singletons._
import parsley.internal.machine.instructions

import StrictParsley.InstrBuffer

private [deepembedding] final class Many[A, C](val p: StrictParsley[A], factory: Factory[A, C]) extends Unary[A, C] {
    final override def optimise: StrictParsley[C] = p match {
        case _: Pure[_] => throw new NonProductiveIterationException("many") // scalastyle:ignore throw
        case _: MZero   => new Pure(factory.newBuilder.result())
        case _          => this
    }
    final override def codeGen[M[_, +_]: ContOps, R](producesResults: Boolean)(implicit instrs: InstrBuffer, state: CodeGenState): M[R, Unit] = {
        val body = state.freshLabel()
        val handler = state.freshLabel()
        if (producesResults) instrs += new instructions.Fresh(factory.newBuilder)
        instrs += new instructions.PushHandler(handler)
        instrs += new instructions.Label(body)
        suspend(p.codeGen[M, R](producesResults)) |> {
            instrs += new instructions.Label(handler)
            instrs += (if (producesResults) new instructions.Many(body) else new instructions.SkipMany(body))
        }
    }

    // $COVERAGE-OFF$
    final override def pretty(p: String): String = s"many($p)"
    // $COVERAGE-ON$
}

private [backend] sealed abstract class ChainLike[A](p: StrictParsley[A], op: StrictParsley[A => A]) extends StrictParsley[A] {
    def inlinable: Boolean = false
    override def optimise: StrictParsley[A] = op match {
        case _: Pure[_] => throw new NonProductiveIterationException("chain") // scalastyle:ignore throw
        case _: MZero   => p
        case _          => this
    }
    // $COVERAGE-OFF$
    final override def pretty: String = pretty(p.pretty, op.pretty)
    protected def pretty(p: String, op: String): String
    // $COVERAGE-ON$
}

private [deepembedding] final class ChainPost[A](p: StrictParsley[A], op: StrictParsley[A => A]) extends ChainLike[A](p, op) {
    override def codeGen[M[_, +_]: ContOps, R](producesResults: Boolean)(implicit instrs: InstrBuffer, state: CodeGenState): M[R, Unit] = {
        val body = state.freshLabel()
        val handler = state.freshLabel()
        suspend(p.codeGen[M, R](producesResults)) >> {
            instrs += new instructions.PushHandler(handler)
            instrs += new instructions.Label(body)
            suspend(op.codeGen[M, R](producesResults)) |> {
                instrs += new instructions.Label(handler)
                instrs += (if (producesResults) new instructions.ChainPost(body) else new instructions.SkipMany(body))
            }
        }
    }
    // $COVERAGE-OFF$
    final override def pretty(p: String, op: String): String = s"chainPost($p, $op)"
    // $COVERAGE-ON$
}

private [deepembedding] final class ChainPre[A](p: StrictParsley[A], op: StrictParsley[A => A]) extends ChainLike[A](p, op) {
    override def codeGen[M[_, +_]: ContOps, R](producesResults: Boolean)(implicit instrs: InstrBuffer, state: CodeGenState): M[R, Unit] = {
        val body = state.freshLabel()
        val handler = state.freshLabel()
        if (producesResults) instrs += new instructions.Push(identity[Any] _)
        instrs += new instructions.PushHandler(handler)
        instrs += new instructions.Label(body)
        suspend(op.codeGen[M, R](producesResults)) >> {
            instrs += new instructions.Label(handler)
            instrs += (if (producesResults) new instructions.ChainPre(body) else new instructions.SkipMany(body))
            suspend(p.codeGen[M, R](producesResults)) |> {
                if (producesResults) instrs += instructions.Apply
            }
        }
    }
    // $COVERAGE-OFF$
    final override def pretty(p: String, op: String): String = s"chainPre($op, $p)"
    // $COVERAGE-ON$
}

private [deepembedding] final class Chainl[A, B](init: StrictParsley[B], p: StrictParsley[A], op: StrictParsley[(B, A) => B]) extends StrictParsley[B] {
    def inlinable: Boolean = false
    override def codeGen[M[_, +_]: ContOps, R](producesResults: Boolean)(implicit instrs: InstrBuffer, state: CodeGenState): M[R, Unit] = {
        val body = state.freshLabel()
        val handler = state.freshLabel()
        suspend(init.codeGen[M, R](producesResults)) >> {
            instrs += new instructions.PushHandler(handler)
            instrs += new instructions.Label(body)
            suspend(op.codeGen[M, R](producesResults)) >>
            suspend(p.codeGen[M, R](producesResults)) |> {
                instrs += new instructions.Label(handler)
                instrs += (if (producesResults) new instructions.Chainl(body) else new instructions.SkipMany(body))
            }
        }
    }
    // $COVERAGE-OFF$
    final override def pretty: String = s"chainl1(${init.pretty}, ${p.pretty}, ${op.pretty})"
    // $COVERAGE-ON$
}

private [deepembedding] final class Chainr[A, B](p: StrictParsley[A], op: StrictParsley[(A, B) => B], private [Chainr] val wrap: A => B)
    extends StrictParsley[B] {
    def inlinable: Boolean = false
    override def codeGen[M[_, +_]: ContOps, R](producesResults: Boolean)(implicit instrs: InstrBuffer, state: CodeGenState): M[R, Unit]= {
        if (producesResults) {
            val body = state.freshLabel()
            val handler = state.freshLabel()
            instrs += new instructions.Push(identity[Any] _)
            instrs += new instructions.Label(body)
            suspend(p.codeGen[M, R](producesResults=true)) >> {
                instrs += new instructions.PushHandler(handler)
                suspend(op.codeGen[M, R](producesResults=true)) |> {
                    instrs += new instructions.ChainrJump(body)
                    instrs += new instructions.Label(handler)
                    instrs += instructions.ChainrOpHandler(wrap)
                }
            }
        }
        // if we don't care about the results, there is no difference between chainl1 and chainr1, and chainl1 is more efficient
        else new Chainl[Nothing, Nothing](p.asInstanceOf[StrictParsley[Nothing]],
                                          p.asInstanceOf[StrictParsley[Nothing]],
                                          op.asInstanceOf[StrictParsley[Nothing]]).codeGen[M, R](producesResults = false)
    }
    // $COVERAGE-OFF$
    final override def pretty: String = s"chainr1(${p.pretty}, ${op.pretty})"
    // $COVERAGE-ON$
}

private [deepembedding] final class SepEndBy1[A, C](p: StrictParsley[A], sep: StrictParsley[_], factory: Factory[A, C]) extends StrictParsley[C] {
    def inlinable: Boolean = false
    override def codeGen[M[_, +_]: ContOps, R](producesResults: Boolean)(implicit instrs: InstrBuffer, state: CodeGenState): M[R, Unit] = {
        val body = state.freshLabel()
        val handler1 = state.freshLabel()
        val handler2 = state.freshLabel()
        instrs += new instructions.Fresh(factory.newBuilder)
        instrs += new instructions.Push(false) // this tracks if p has been consumed
        instrs += new instructions.PushHandler(handler1)
        instrs += new instructions.Label(body)
        suspend(p.codeGen[M, R](producesResults = true)) >> {
            instrs += new instructions.PushHandler(handler2)
            suspend(sep.codeGen[M, R](producesResults = false)) |> {
                instrs += new instructions.SepEndBy1Jump(body) // will set bool to true
                instrs += new instructions.Label(handler2)
                instrs += instructions.SepEndBy1SepHandler // ignores bool, it's true
                instrs += new instructions.Label(handler1)
                instrs += instructions.SepEndBy1WholeHandler // queries the bool
                if (!producesResults) instrs += instructions.Pop
            }
        }
    }
    // $COVERAGE-OFF$
    final override def pretty: String = s"sepEndBy1(${p.pretty}, ${sep.pretty})"
    // $COVERAGE-ON$
}

// TODO: unify :/
private [deepembedding] final class ManyUntil[A, C](val p: StrictParsley[Any], factory: Factory[A, C]) extends Unary[Any, C] {
    override def codeGen[M[_, +_]: ContOps, R](producesResults: Boolean)(implicit instrs: InstrBuffer, state: CodeGenState): M[R, Unit] = {
        val start = state.freshLabel()
        instrs += new instructions.Fresh(factory.newBuilder)
        instrs += new instructions.Label(start)
        suspend(p.codeGen[M, R](producesResults = true)) |> {
            instrs += new instructions.ManyUntil(start)
            if (!producesResults) instrs += instructions.Pop
        }
    }
    // $COVERAGE-OFF$
    final override def pretty(p: String): String = s"manyUntil($p)"
    // $COVERAGE-ON$
}

private [deepembedding] final class SkipManyUntil(val p: StrictParsley[Any]) extends Unary[Any, Unit] {
    override def codeGen[M[_, +_]: ContOps, R](producesResults: Boolean)(implicit instrs: InstrBuffer, state: CodeGenState): M[R, Unit] = {
        val start = state.freshLabel()
        instrs += new instructions.Label(start)
        // requires the control flow through for the end token
        suspend(p.codeGen[M, R](producesResults = true)) |> {
            instrs += new instructions.SkipManyUntil(start)
            if (producesResults) instrs += instructions.Push.Unit
        }
    }
    // $COVERAGE-OFF$
    final override def pretty(p: String): String = s"manyUntil($p)"
    // $COVERAGE-ON$
}
