/* SPDX-FileCopyrightText: © 2022 Parsley Contributors <https://github.com/j-mie6/Parsley/graphs/contributors>
 * SPDX-License-Identifier: BSD-3-Clause
 */
package parsley.internal.deepembedding.backend

import parsley.internal.deepembedding.ContOps, ContOps.{suspend, ContAdapter}
import parsley.internal.deepembedding.singletons._
import parsley.internal.machine.instructions

import Branch.FlipApp
import StrictParsley.InstrBuffer

private [backend] sealed abstract class BranchLike[A, B, C, D](finaliser: Option[instructions.Instr]) extends StrictParsley[D] {
    val b: StrictParsley[A]
    val p: StrictParsley[B]
    val q: StrictParsley[C]
    def instr(label: Int): instructions.Instr

    def inlinable: Boolean = false
    final override def codeGen[Cont[_, +_]: ContOps, R](implicit instrs: InstrBuffer, state: CodeGenState): Cont[R, Unit] = {
        val toP = state.freshLabel()
        val end = state.freshLabel()
        suspend(b.codeGen[Cont, R]) >> {
            instrs += instr(toP)
            suspend(q.codeGen[Cont, R]) >> {
                for (instr <- finaliser) instrs += instr
                instrs += new instructions.Jump(end)
                instrs += new instructions.Label(toP)
                suspend(p.codeGen[Cont, R]) |> {
                    for (instr <- finaliser) instrs += instr
                    instrs += new instructions.Label(end)
                }
            }
        }
    }
}

private [deepembedding] final class Branch[A, B, C](val b: StrictParsley[Either[A, B]], val p: StrictParsley[A => C], val q: StrictParsley[B => C])
    extends BranchLike[Either[A, B], A => C, B => C, C](Some(FlipApp)) {
    override def instr(label: Int): instructions.Instr = new instructions.Case(label)
    override def optimise: StrictParsley[C] = b match {
        case Pure(Left(x)) => <*>(p, new Pure(x)).optimise
        case Pure(Right(y)) => <*>(q, new Pure(y)).optimise
        case _ => (p, q) match {
            case (Pure(f), Pure(g)) => <*>(new Pure((x: Either[A, B]) => x.fold(f, g)), b)
            case _ => this
        }
    }
    // $COVERAGE-OFF$
    final override def pretty[Cont[_, +_]: ContOps, R]: Cont[R,String] =
        for {
            s1 <- b.pretty
            s2 <- p.pretty
            s3 <- q.pretty
        } yield s"branch($s1, $s2, $s3)"
    // $COVERAGE-ON$
}

private [deepembedding] final class If[A](val b: StrictParsley[Boolean], val p: StrictParsley[A], val q: StrictParsley[A])
    extends BranchLike[Boolean, A, A, A](None) {
    override def instr(label: Int): instructions.Instr = new instructions.If(label)
    override def optimise: StrictParsley[A] = b match {
        case Pure(true) => p
        case Pure(false) => q
        case _ => this
    }
    // $COVERAGE-OFF$
    final override def pretty[Cont[_, +_]: ContOps, R]: Cont[R,String] =
        for {
            s1 <- b.pretty
            s2 <- p.pretty
            s3 <- q.pretty
        } yield s"if($s1, $s2, $s3)"
    // $COVERAGE-ON$
}

// TODO: Code generation for FastZero and FilterLike is shared
private [backend] sealed abstract class FastZero[A](fail: A => StrictParsley[Nothing], instr: instructions.Instr) extends Unary[A, Nothing] {

    final override def optimise: StrictParsley[Nothing] = p match {
        case Pure(x) => fail(x)
        case z: MZero => z
        case _ => this
    }
    final override def codeGen[Cont[_, +_], R](implicit ops: ContOps[Cont], instrs: InstrBuffer, state: CodeGenState): Cont[R, Unit] = {
        val handler = state.getLabel(instructions.PopStateAndFail)
        instrs += new instructions.PushHandlerAndState(handler, saveHints = false, hideHints = false)
        suspend(p.codeGen[Cont, R]) |> {
            instrs += instr
        }
    }
}
private [deepembedding] final class FastFail[A](val p: StrictParsley[A], msggen: A => String)
    extends FastZero[A](x => new Fail(0, msggen(x)), instructions.FastFail(msggen)) with MZero {
    // $COVERAGE-OFF$
    final override def pretty(p: String): String = s"$p.fail(?)"
    // $COVERAGE-ON$
}
private [deepembedding] final class FastUnexpected[A](val p: StrictParsley[A], msggen: A => String)
    extends FastZero[A](x => new Unexpected(msggen(x), 0), new instructions.FastUnexpected(msggen)) with MZero {
    // $COVERAGE-OFF$
    final override def pretty(p: String): String = s"$p.unexpected(?)"
    // $COVERAGE-ON$
}

private [backend] sealed abstract class FilterLike[A](fail: A => StrictParsley[Nothing], instr: instructions.Instr, pred: A => Boolean)
    extends Unary[A, A] {
    final override def optimise: StrictParsley[A] = p match {
        case Pure(x) if pred(x) => fail(x)
        case px: Pure[_] => px
        case z: MZero => z
        case _ => this
    }
    final override def codeGen[Cont[_, +_]: ContOps, R](implicit instrs: InstrBuffer, state: CodeGenState): Cont[R, Unit] = {
        val handler = state.getLabel(instructions.PopStateAndFail)
        instrs += new instructions.PushHandlerAndState(handler, saveHints = false, hideHints = false)
        suspend(p.codeGen[Cont, R]) |> {
            instrs += instr
        }
    }
}
private [deepembedding] final class Filter[A](val p: StrictParsley[A], pred: A => Boolean)
    extends FilterLike[A](_ => Empty, new instructions.Filter(pred), !pred(_)) {
    // $COVERAGE-OFF$
    final override def pretty(p: String): String = s"$p.filter(?)"
    // $COVERAGE-ON$
}
private [deepembedding] final class MapFilter[A, B](val p: StrictParsley[A], f: A => Option[B]) extends Unary[A, B] {
    final override def optimise: StrictParsley[B] = p match {
        case Pure(x) => f(x).map(new Pure(_)).getOrElse(Empty)
        case z: MZero => z
        case _ => this
    }

    final override def codeGen[Cont[_, +_]: ContOps, R](implicit instrs: InstrBuffer, state: CodeGenState): Cont[R, Unit] = {
        val handler = state.getLabel(instructions.PopStateAndFail)
        instrs += new instructions.PushHandlerAndState(handler, saveHints = false, hideHints = false)
        suspend(p.codeGen[Cont, R]) |> {
            instrs += new instructions.MapFilter(f)
        }
    }

    // $COVERAGE-OFF$
    final override def pretty(p: String): String = s"$p.mapFilter(?)"
    // $COVERAGE-ON$
}

private [deepembedding] final class FilterOut[A](val p: StrictParsley[A], pred: PartialFunction[A, String])
    extends FilterLike[A](x => ErrorExplain(Empty, pred(x)), new instructions.FilterOut(pred), pred.isDefinedAt(_)) {
    // $COVERAGE-OFF$
    final override def pretty(p: String): String = s"$p.filterOut(?)"
    // $COVERAGE-ON$
}
private [deepembedding] final class GuardAgainst[A](val p: StrictParsley[A], pred: PartialFunction[A, scala.Seq[String]])
    extends FilterLike[A](x => new Fail(0, pred(x): _*), instructions.GuardAgainst(pred), pred.isDefinedAt(_)) {
    // $COVERAGE-OFF$
    final override def pretty(p: String): String = s"$p.guardAgainst(?)"
    // $COVERAGE-ON$
}

private [deepembedding] final class UnexpectedWhen[A](val p: StrictParsley[A], pred: PartialFunction[A, String])
    extends FilterLike[A](x => new Unexpected(pred(x), 0), instructions.UnexpectedWhen(pred), pred.isDefinedAt(_)) {
    // $COVERAGE-OFF$
    final override def pretty(p: String): String = s"$p.unexpectedWhen(?)"
    // $COVERAGE-ON$
}

private [backend] object Branch {
    val FlipApp = instructions.Lift2[Any, Any => Any, Any]((x, f) => f(x))
}