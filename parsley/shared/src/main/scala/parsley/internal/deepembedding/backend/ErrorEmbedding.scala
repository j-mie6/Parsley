/*
 * Copyright 2020 Parsley Contributors <https://github.com/j-mie6/Parsley/graphs/contributors>
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package parsley.internal.deepembedding.backend

import parsley.token.errors.Label

import parsley.internal.deepembedding.singletons._
import parsley.internal.machine.instructions

private [deepembedding] final class ErrorLabel[A](val p: StrictParsley[A], private val label: String, private val labels: scala.Seq[String])
    extends ScopedUnary[A, A] {
    override def setup(label: Int): instructions.Instr = new instructions.PushHandler(label) // was AndClearHints
    override def instr: instructions.Instr = new instructions.RelabelHints(label +: labels)
    override def instrNeedsLabel: Boolean = false
    override def handlerLabel(state: CodeGenState): Int = state.getLabelForRelabelError(label +: labels)
    // don't need to be limited to not hidden when the thing can never internally generate hints
    final override def optimise: StrictParsley[A] = p match {
        case CharTok(c, x) => new CharTok(c, x, Label(label, labels: _*)).asInstanceOf[StrictParsley[A]]
        case SupplementaryCharTok(c, x) => new SupplementaryCharTok(c, x, Label(label, labels: _*)).asInstanceOf[StrictParsley[A]]
        case StringTok(s, x) => new StringTok(s, x, Label(label, labels: _*)).asInstanceOf[StrictParsley[A]]
        case Satisfy(f) => new Satisfy(f, Label(label, labels: _*)).asInstanceOf[StrictParsley[A]]
        case UniSatisfy(f) => new UniSatisfy(f, Label(label, labels: _*)).asInstanceOf[StrictParsley[A]]
        case ErrorLabel(p, _, _) => ErrorLabel(p, label, labels)
        case _ => this
    }

    // $COVERAGE-OFF$
    final override def pretty(p: String): String = s"$p.label(${label +: labels})"
    // $COVERAGE-ON$
}
private [deepembedding] final class ErrorHide[A](val p: StrictParsley[A]) extends ScopedUnary[A, A] {
    override def setup(label: Int): instructions.Instr = new instructions.PushHandler(label)
    override def instr: instructions.Instr = instructions.HideHints
    override def instrNeedsLabel: Boolean = false
    override def handlerLabel(state: CodeGenState): Int = state.getLabel(instructions.HideErrorAndFail)

    // $COVERAGE-OFF$
    final override def pretty(p: String): String = s"$p.hide"
    // $COVERAGE-ON$
}
private [deepembedding] final class ErrorExplain[A](val p: StrictParsley[A], reason: String) extends ScopedUnary[A, A] {
    override def setup(label: Int): instructions.Instr = new instructions.PushHandler(label)
    override def instr: instructions.Instr = instructions.PopHandler
    override def instrNeedsLabel: Boolean = false
    override def handlerLabel(state: CodeGenState): Int  = state.getLabelForApplyReason(reason)
    // $COVERAGE-OFF$
    final override def pretty(p: String): String = s"$p.explain($reason)"
    // $COVERAGE-ON$
}

private [deepembedding] final class ErrorAmend[A](val p: StrictParsley[A], partial: Boolean) extends ScopedUnaryWithState[A, A] {
    override val instr: instructions.Instr = instructions.PopHandlerAndState
    override def instrNeedsLabel: Boolean = false
    override def handlerLabel(state: CodeGenState): Int  = state.getLabel(instructions.AmendAndFail(partial))
    // $COVERAGE-OFF$
    final override def pretty(p: String): String = s"amend($p)"
    // $COVERAGE-ON$
}
private [deepembedding] final class ErrorEntrench[A](val p: StrictParsley[A]) extends ScopedUnary[A, A] {
    override def setup(label: Int): instructions.Instr = new instructions.PushHandler(label)
    override val instr: instructions.Instr = instructions.PopHandler
    override def instrNeedsLabel: Boolean = false
    override def handlerLabel(state: CodeGenState): Int  = state.getLabel(instructions.EntrenchAndFail)
    // $COVERAGE-OFF$
    final override def pretty(p: String): String = s"entrench($p)"
    // $COVERAGE-ON$
}
private [deepembedding] final class ErrorDislodge[A](n: Int, val p: StrictParsley[A]) extends ScopedUnary[A, A] {
    override def setup(label: Int): instructions.Instr = new instructions.PushHandler(label)
    override val instr: instructions.Instr = instructions.PopHandler
    override def instrNeedsLabel: Boolean = false
    override def handlerLabel(state: CodeGenState): Int  = state.getLabelForDislodgeAndFail(n)
    // $COVERAGE-OFF$
    final override def pretty(p: String): String = s"dislodge($p)"
    // $COVERAGE-ON$
}

private [deepembedding] final class ErrorLexical[A](val p: StrictParsley[A]) extends ScopedUnary[A, A] {
    // This needs to save the hints because error label will relabel the first hint, which because the list is ordered would be the hints that came _before_
    // entering labels context. Instead label should relabel the first hint generated _within_ its context, then merge with the originals after
    override def setup(label: Int): instructions.Instr = new instructions.PushHandler(label)
    override def instr: instructions.Instr = instructions.PopHandler
    override def instrNeedsLabel: Boolean = false
    override def handlerLabel(state: CodeGenState): Int = state.getLabel(instructions.SetLexicalAndFail)

    // $COVERAGE-OFF$
    final override def pretty(p: String): String = s"$p.markAsToken"
    // $COVERAGE-ON$
}

private [backend] object ErrorLabel {
    def apply[A](p: StrictParsley[A], label: String, labels: scala.Seq[String]): ErrorLabel[A] = new ErrorLabel(p, label, labels)
    def unapply[A](self: ErrorLabel[A]): Some[(StrictParsley[A], String, scala.Seq[String])] = Some((self.p, self.label, self.labels))
}
private [backend] object ErrorHide {
    def unapply[A](self: ErrorHide[A]): Some[StrictParsley[A]] = Some(self.p)
}
private [backend] object ErrorExplain {
    def apply[A](p: StrictParsley[A], reason: String): ErrorExplain[A] = new ErrorExplain(p, reason)
}

private [backend] object TablableErrors {
    def unapply[A](self: StrictParsley[A]): Option[StrictParsley[A]] = self match {
        case self: ErrorAmend[_] => Some(self.p)
        case self: ErrorLexical[_] => Some(self.p) // is this correct?
        case _ => None
    }
}
