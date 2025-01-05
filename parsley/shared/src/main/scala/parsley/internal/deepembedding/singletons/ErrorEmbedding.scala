/*
 * Copyright 2020 Parsley Contributors <https://github.com/j-mie6/Parsley/graphs/contributors>
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package parsley.internal.deepembedding.singletons

import parsley.internal.deepembedding.backend.MZero
import parsley.internal.deepembedding.backend.StrictParsley.InstrBuffer
import parsley.internal.deepembedding.frontend.LazyParsleyIVisitor
import parsley.internal.errors.CaretWidth
import parsley.internal.machine.instructions

// This doesn't need to be a CaretWidth, because empty generates errors that are the least dominating kind anyway
private [parsley] final class Empty private (val width: Int) extends Singleton[Nothing] with MZero {
    // $COVERAGE-OFF$
    override def pretty: String = debugName
    // $COVERAGE-ON$
    override def genInstrs(producesResults: Boolean)(implicit instrs: InstrBuffer): Unit = instrs += new instructions.Empty(width)

    // $COVERAGE-OFF$
    override def visit[T, U[+_]](visitor: LazyParsleyIVisitor[T, U], context: T): U[Nothing] = visitor.visit(this, context)(width)

    private [parsley] var debugName = "empty"
    // $COVERAGE-ON$
}

private [parsley] final class Fail(width: CaretWidth, msgs: String*) extends Singleton[Nothing] with MZero {
    // $COVERAGE-OFF$
    override def pretty: String = s"fail(${msgs.mkString(", ")})"
    // $COVERAGE-ON$
    override def genInstrs(producesResults: Boolean)(implicit instrs: InstrBuffer): Unit = instrs += new instructions.Fail(width, msgs: _*)

    // $COVERAGE-OFF$
    override def visit[T, U[+_]](visitor: LazyParsleyIVisitor[T, U], context: T): U[Nothing] = visitor.visit(this, context)(width, msgs)

    private [parsley] var debugName = "fail"
    // $COVERAGE-ON$
}

private [parsley] final class Unexpected(msg: String, width: CaretWidth) extends Singleton[Nothing] with MZero {
    // $COVERAGE-OFF$
    override def pretty: String = s"unexpected($msg)"
    // $COVERAGE-ON$
    override def genInstrs(producesResults: Boolean)(implicit instrs: InstrBuffer): Unit = instrs += new instructions.Unexpected(msg, width)

    // $COVERAGE-OFF$
    override def visit[T, U[+_]](visitor: LazyParsleyIVisitor[T, U], context: T): U[Nothing] = visitor.visit(this, context)(msg, width)

    private [parsley] var debugName = "unexpected"
    // $COVERAGE-ON$
}

// From the thesis
private [parsley] final class VanillaGen[A](gen: parsley.errors.VanillaGen[A]) extends Singleton[((A, Int)) => Nothing] {
    // $COVERAGE-OFF$
    override def pretty: String = "VanillaGen"
    // $COVERAGE-ON$

    override def genInstrs(producesResults: Boolean)(implicit instrs: InstrBuffer): Unit = instrs += new instructions.VanillaGen(gen)

    // $COVERAGE-OFF$
    override def visit[T, U[+_]](visitor: LazyParsleyIVisitor[T, U], context: T): U[((A, Int)) => Nothing] = visitor.visit(this, context)(gen)

    private [parsley] var debugName: String = pretty
    // $COVERAGE-ON$
}

private [parsley] final class SpecializedGen[A](gen: parsley.errors.SpecializedGen[A]) extends Singleton[((A, Int)) => Nothing] {
    // $COVERAGE-OFF$
    override def pretty: String = "SpecializedGen"
    // $COVERAGE-ON$

    override def genInstrs(producesResults: Boolean)(implicit instrs: InstrBuffer): Unit = instrs += new instructions.SpecializedGen(gen)

    // $COVERAGE-OFF$
    override def visit[T, U[+_]](visitor: LazyParsleyIVisitor[T, U], context: T): U[((A, Int)) => Nothing] = visitor.visit(this, context)(gen)

    private [parsley] var debugName: String = pretty
    // $COVERAGE-ON$
}

private [parsley] object Empty {
    val Zero = new Empty(0)
    def apply(width: Int): Empty = if (width == 0) Zero else new Empty(width)
}
