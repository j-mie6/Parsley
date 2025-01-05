/*
 * Copyright 2020 Parsley Contributors <https://github.com/j-mie6/Parsley/graphs/contributors>
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package parsley.internal.deepembedding.singletons

import parsley.token.descriptions.{PlusSignPresence, SpaceDesc}
import parsley.token.errors.ErrorConfig

import parsley.internal.deepembedding.Sign.SignType
import parsley.internal.deepembedding.backend.StrictParsley.InstrBuffer
import parsley.internal.deepembedding.frontend.LazyParsleyIVisitor
import parsley.internal.machine.instructions

private [parsley] final class WhiteSpace(ws: Char => Boolean, desc: SpaceDesc, errConfig: ErrorConfig)
    extends Singleton[Unit] {
    // $COVERAGE-OFF$
    override val pretty: String = "whiteSpace"
    override def genInstrs(producesResults: Boolean)(implicit instrs: InstrBuffer): Unit = {
        instrs += new instructions.TokenWhiteSpace(ws, desc, errConfig)
        if (producesResults) instrs += instructions.Push.Unit
    }

    // $COVERAGE-OFF$
    override def visit[T, U[+_]](visitor: LazyParsleyIVisitor[T, U], context: T): U[Unit] = visitor.visit(this, context)(ws, desc, errConfig)

    private [parsley] var debugName = pretty
    // $COVERAGE-ON$
}

private [parsley] final class SkipComments(desc: SpaceDesc, errConfig: ErrorConfig) extends Singleton[Unit] {
    // $COVERAGE-OFF$
    override def pretty: String = debugName
    override def genInstrs(producesResults: Boolean)(implicit instrs: InstrBuffer): Unit = {
        instrs += new instructions.TokenSkipComments(desc, errConfig)
        if (producesResults) instrs += instructions.Push.Unit
    }

    // $COVERAGE-OFF$
    override def visit[T, U[+_]](visitor: LazyParsleyIVisitor[T, U], context: T): U[Unit] = visitor.visit(this, context)(desc, errConfig)

    private [parsley] var debugName = "skipComments"
    // $COVERAGE-ON$
}

private [parsley] final class Comment(desc: SpaceDesc, errConfig: ErrorConfig) extends Singleton[Unit] {
    // $COVERAGE-OFF$
    override def pretty: String = debugName
    override def genInstrs(producesResults: Boolean)(implicit instrs: InstrBuffer): Unit = {
        instrs += new instructions.TokenComment(desc, errConfig)
        if (producesResults) instrs += instructions.Push.Unit
    }

    // $COVERAGE-OFF$
    override def visit[T, U[+_]](visitor: LazyParsleyIVisitor[T, U], context: T): U[Unit] = visitor.visit(this, context)(desc, errConfig)

    private [parsley] var debugName = "comment"
    // $COVERAGE-ON$
}

private [parsley] final class Sign[A](ty: SignType, signPresence: PlusSignPresence) extends Singleton[A => A] {
    // $COVERAGE-OFF$
    override val pretty: String = "sign"
    override def genInstrs(producesResults: Boolean)(implicit instrs: InstrBuffer): Unit = {
        instrs += new instructions.TokenSign(ty, signPresence)
        if (!producesResults) instrs += instructions.Pop
    }

    // $COVERAGE-OFF$
    override def visit[T, U[+_]](visitor: LazyParsleyIVisitor[T, U], context: T): U[A => A] = visitor.visit(this, context)(ty, signPresence)

    private [parsley] var debugName: String = null // this is never exposed to the top
    // $COVERAGE-ON$
}

private [parsley] final class NonSpecific(name: String, unexpectedIllegal: String => String, var debugName: String,
                                          start: Char => Boolean, letter: Char => Boolean, illegal: String => Boolean) extends Singleton[String] {
    // $COVERAGE-OFF$
    override def pretty: String = "nonspecificName"
    // $COVERAGE-ON$
    override def genInstrs(producesResults: Boolean)(implicit instrs: InstrBuffer): Unit = {
        instrs += new instructions.TokenNonSpecific(name, unexpectedIllegal)(start, letter, illegal)
        if (!producesResults) instrs += instructions.Pop
    }

    // $COVERAGE-OFF$
    override def visit[T, U[+_]](visitor: LazyParsleyIVisitor[T, U], context: T): U[String] = {
        visitor.visit(this, context)(name, unexpectedIllegal, start, letter, illegal)
    }
    // $COVERAGE-ON$
}
