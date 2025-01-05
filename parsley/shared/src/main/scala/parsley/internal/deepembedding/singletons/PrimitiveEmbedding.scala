/*
 * Copyright 2020 Parsley Contributors <https://github.com/j-mie6/Parsley/graphs/contributors>
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package parsley.internal.deepembedding.singletons

import parsley.state.Ref
import parsley.token.errors.LabelConfig

import parsley.internal.deepembedding.backend.StrictParsley.InstrBuffer
import parsley.internal.deepembedding.frontend.LazyParsleyIVisitor
import parsley.internal.machine.instructions

private [parsley] final class Satisfy(private val f: Char => Boolean, val expected: LabelConfig) extends Singleton[Char] {
    // $COVERAGE-OFF$
    override val pretty: String = "satisfy(f)"
    // $COVERAGE-ON$
    override def genInstrs(producesResults: Boolean)(implicit instrs: InstrBuffer): Unit = {
        instrs += new instructions.Satisfies(f, expected)
        if (!producesResults) instrs += instructions.Pop
    }

    // $COVERAGE-OFF$
    override def visit[T, U[+_]](visitor: LazyParsleyIVisitor[T, U], context: T): U[Char] = visitor.visit(this, context)(f, expected)

    private [parsley] var debugName = "satisfy"
    // $COVERAGE-ON$
}

private [parsley] object Line extends Singleton[Int] {
    // $COVERAGE-OFF$
    override def pretty: String = debugName
    // $COVERAGE-ON$
    override def genInstrs(producesResults: Boolean)(implicit instrs: InstrBuffer): Unit = if (producesResults) instrs += instructions.Line

    // $COVERAGE-OFF$
    override def visit[T, U[+_]](visitor: LazyParsleyIVisitor[T, U], context: T): U[Int] = visitor.visit(this, context)

    private [parsley] var debugName = "line"
    // $COVERAGE-ON$
}
private [parsley] object Col extends Singleton[Int] {
    // $COVERAGE-OFF$
    override def pretty: String = debugName
    // $COVERAGE-ON$
    override def genInstrs(producesResults: Boolean)(implicit instrs: InstrBuffer): Unit = if (producesResults) instrs += instructions.Col

    // $COVERAGE-OFF$
    override def visit[T, U[+_]](visitor: LazyParsleyIVisitor[T, U], context: T): U[Int] = visitor.visit(this, context)

    private [parsley] var debugName = "col"
    // $COVERAGE-ON$
}
private [parsley] object Offset extends Singleton[Int] {
    // $COVERAGE-OFF$
    override def pretty: String = debugName
    // $COVERAGE-ON$
    override def genInstrs(producesResults: Boolean)(implicit instrs: InstrBuffer): Unit = if (producesResults) instrs += instructions.Offset

    // $COVERAGE-OFF$
    override def visit[T, U[+_]](visitor: LazyParsleyIVisitor[T, U], context: T): U[Int] = visitor.visit(this, context)

    private [parsley] var debugName = "offset"
    // $COVERAGE-ON$
}

// This should really have UsesRegister, however, if it doesn't, this has the nice effect of catching
// registers that have never been filled in some way!
private [parsley] final class Get[S](ref: Ref[S]) extends Singleton[S] {
    // $COVERAGE-OFF$
    override def pretty: String = s"get($ref)"
    // $COVERAGE-ON$
    override def genInstrs(producesResults: Boolean)(implicit instrs: InstrBuffer): Unit = if (producesResults) instrs += new instructions.Get(ref.addr)

    // $COVERAGE-OFF$
    override def visit[T, U[+_]](visitor: LazyParsleyIVisitor[T, U], context: T): U[S] = visitor.visit(this, context)(ref)

    private [parsley] var debugName = "Ref.get"
    // $COVERAGE-ON$
}

private [deepembedding] object Satisfy {
    def unapply(self: Satisfy): Some[Char => Boolean] = Some(self.f)
}
