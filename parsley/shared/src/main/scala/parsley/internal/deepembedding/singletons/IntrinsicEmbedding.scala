/*
 * Copyright 2020 Parsley Contributors <https://github.com/j-mie6/Parsley/graphs/contributors>
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package parsley.internal.deepembedding.singletons

import parsley.state.Ref
import parsley.token.errors.LabelConfig

import parsley.internal.deepembedding.backend.StrictParsley, StrictParsley.InstrBuffer
import parsley.internal.deepembedding.frontend.{LazyParsleyIVisitor, UsesRef}
import parsley.internal.machine.instructions

private [parsley] final class CharTok[A](private val c: Char, private val x: A, val expected: LabelConfig) extends Singleton[A] {
    // $COVERAGE-OFF$
    override def pretty: String = s"char($c).as($x)"
    // $COVERAGE-ON$
    override def genInstrs(producesResults: Boolean)(implicit instrs: InstrBuffer): Unit = {
        instrs += new instructions.CharTok(c, expected)
        if (producesResults) instrs += new instructions.Push(x)
    }

    // $COVERAGE-OFF$
    override def visit[T, U[+_]](visitor: LazyParsleyIVisitor[T, U], context: T): U[A] = visitor.visit(this, context)(c, x, expected)

    private [parsley] var debugName: String = s"char($c)" //FIXME: missing x?
    // $COVERAGE-ON$
}

private [parsley] final class SupplementaryCharTok[A](private val codepoint: Int, private val x: A, val expected: LabelConfig) extends Singleton[A] {
    // $COVERAGE-OFF$
    override def pretty: String = s"char(${Character.toChars(codepoint).mkString}).as($x)"
    // $COVERAGE-ON$
    override def genInstrs(producesResults: Boolean)(implicit instrs: InstrBuffer): Unit = {
        instrs += new instructions.SupplementaryCharTok(codepoint, expected)
        if (producesResults) instrs += new instructions.Push(x)
    }

    // $COVERAGE-OFF$
    override def visit[T, U[+_]](visitor: LazyParsleyIVisitor[T, U], context: T): U[A] = visitor.visit(this, context)(codepoint, x, expected)

    private [parsley] var debugName: String = s"char(${Character.toChars(codepoint).mkString})" //FIXME: missing x?
    // $COVERAGE-ON$
}

private [parsley] final class StringTok[A](private val s: String, private val x: A, val expected: LabelConfig) extends Singleton[A] {
    // $COVERAGE-OFF$
    override def pretty: String = s"string($s).as($x)"
    // $COVERAGE-ON$
    override def genInstrs(producesResults: Boolean)(implicit instrs: InstrBuffer): Unit = {
        instrs += new instructions.StringTok(s, expected)
        if (producesResults) instrs += new instructions.Push(x)
    }

    override protected[deepembedding] def optimise: StrictParsley[A] = if (s.length == 1) new CharTok(s.head, x, expected) else this

    // $COVERAGE-OFF$
    override def visit[T, U[+_]](visitor: LazyParsleyIVisitor[T, U], context: T): U[A] = visitor.visit(this, context)(s, x, expected)

    private [parsley] var debugName: String = s"string($s)" //FIXME: missing x?
    // $COVERAGE-ON$
}

private [parsley] object Eof extends Singleton[Unit] {
    // $COVERAGE-OFF$
    override def pretty: String = debugName
    // $COVERAGE-ON$
    override def genInstrs(producesResults: Boolean)(implicit instrs: InstrBuffer): Unit = {
        instrs += instructions.Eof
        if (producesResults) instrs += instructions.Push.Unit
    }

    // $COVERAGE-OFF$
    override def visit[T, U[+_]](visitor: LazyParsleyIVisitor[T, U], context: T): U[Unit] = visitor.visit(this, context)

    private [parsley] var debugName = "eof"
    // $COVERAGE-ON$
}

private [parsley] final class UniSatisfy(private [UniSatisfy] val f: Int => Boolean, val expected: LabelConfig) extends Singleton[Int] {
    // $COVERAGE-OFF$
    override def pretty: String = "satisfyUnicode(?)"
    // $COVERAGE-ON$
    override def genInstrs(producesResults: Boolean)(implicit instrs: InstrBuffer): Unit = {
        instrs += new instructions.UniSat(f, expected)
        if (!producesResults) instrs += instructions.Pop
    }

    // $COVERAGE-OFF$
    override def visit[T, U[+_]](visitor: LazyParsleyIVisitor[T, U], context: T): U[Int] = visitor.visit(this, context)(f, expected)

    private [parsley] var debugName = "satisfyUtf16"
    // $COVERAGE-ON$
}

private [parsley] final class Modify[S](val ref: Ref[S], f: S => S) extends Singleton[Unit] with UsesRef {
    // $COVERAGE-OFF$
    override def pretty: String = s"modify($ref, ?)"
    // $COVERAGE-ON$
    override def genInstrs(producesResults: Boolean)(implicit instrs: InstrBuffer): Unit = {
        instrs += instructions.Modify(ref.addr, f)
        if (producesResults) instrs += instructions.Push.Unit
    }

    // $COVERAGE-OFF$
    override def visit[T, U[+_]](visitor: LazyParsleyIVisitor[T, U], context: T): U[Unit] = visitor.visit(this, context)(ref, f)

    private [parsley] var debugName = "Ref.update"
    // $COVERAGE-ON$
}

private [deepembedding] object CharTok {
    def unapply[A](self: CharTok[A]): Some[(Char, A)] = Some((self.c, self.x))
}
private [deepembedding] object SupplementaryCharTok {
    def unapply[A](self: SupplementaryCharTok[A]): Some[(Int, A)] = Some((self.codepoint, self.x))
}
private [deepembedding] object StringTok {
    def unapply[A](self: StringTok[A]): Some[(String, A)] = Some((self.s, self.x))
}
private [deepembedding] object UniSatisfy {
    def unapply(self: UniSatisfy): Some[Int => Boolean] = Some(self.f)
}
