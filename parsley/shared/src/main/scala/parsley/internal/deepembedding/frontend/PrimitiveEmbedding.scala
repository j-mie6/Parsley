/*
 * Copyright 2020 Parsley Contributors <https://github.com/j-mie6/Parsley/graphs/contributors>
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package parsley.internal.deepembedding.frontend

import parsley.debug.{Breakpoint, Profiler}
import parsley.errors.ErrorBuilder
import parsley.state.Ref

import parsley.internal.deepembedding.backend, backend.StrictParsley

private [parsley] final class Atomic[A](p: LazyParsley[A]) extends Unary[A, A](p) {
    override def make(p: StrictParsley[A]): StrictParsley[A] = new backend.Atomic(p)

    // $COVERAGE-OFF$
    override def visit[T, U[+_]](visitor: LazyParsleyIVisitor[T, U], context: T): U[A] = visitor.visit(this, context)(p)

    private [parsley] var debugName = "atomic"
    // $COVERAGE-ON$
}
private [parsley] final class Look[A](p: LazyParsley[A]) extends Unary[A, A](p) {
    override def make(p: StrictParsley[A]): StrictParsley[A] = new backend.Look(p)

    // $COVERAGE-OFF$
    override def visit[T, U[+_]](visitor: LazyParsleyIVisitor[T, U], context: T): U[A] = visitor.visit(this, context)(p)

    private [parsley] var debugName = "lookAhead"
    // $COVERAGE-ON$
}
private [parsley] final class NotFollowedBy[A](p: LazyParsley[A]) extends Unary[A, Unit](p) {
    override def make(p: StrictParsley[A]): StrictParsley[Unit] = new backend.NotFollowedBy(p)

    // $COVERAGE-OFF$
    override def visit[T, U[+_]](visitor: LazyParsleyIVisitor[T, U], context: T): U[Unit] = visitor.visit(this, context)(p)

    private [parsley] var debugName = "notFollowedBy"
    // $COVERAGE-ON$
}
private [parsley] final class Put[S](val ref: Ref[S], _p: LazyParsley[S]) extends Unary[S, Unit](_p) with UsesRef {
    override def make(p: StrictParsley[S]): StrictParsley[Unit] = new backend.Put(ref, p)

    // $COVERAGE-OFF$
    override def visit[T, U[+_]](visitor: LazyParsleyIVisitor[T, U], context: T): U[Unit] = visitor.visit(this, context)(ref, _p)

    private [parsley] var debugName = "Ref.set"
    // $COVERAGE-ON$
}
private [parsley] final class NewReg[S, A](val ref: Ref[S], init: LazyParsley[S], body: =>LazyParsley[A]) extends Binary[S, A, A](init, body) with UsesRef {
    override def make(init: StrictParsley[S], body: StrictParsley[A]): StrictParsley[A] = new backend.NewReg(ref, init, body)

    // $COVERAGE-OFF$
    override def visit[T, U[+_]](visitor: LazyParsleyIVisitor[T, U], context: T): U[A] = visitor.visit(this, context)(ref, init, body)

    private [parsley] var debugName = "fillRef"
    // $COVERAGE-ON$
}
private [parsley] final class Span(p: LazyParsley[_]) extends Unary[Any, String](p) {
    override def make(p: StrictParsley[Any]): StrictParsley[String] = new backend.Span(p)

    // $COVERAGE-OFF$
    override def visit[T, U[+_]](visitor: LazyParsleyIVisitor[T, U], context: T): U[String] = visitor.visit(this, context)(p)

    private [parsley] var debugName: String = "span"
    // $COVERAGE-ON$
}

// $COVERAGE-OFF$
private [parsley] final class Debug[A](p: LazyParsley[A], name: String, ascii: Boolean, break: Breakpoint, watchedRefs: Seq[(Ref[_], String)])
    extends Unary[A, A](p) {
    override def make(p: StrictParsley[A]): StrictParsley[A] = new backend.Debug(p, name, ascii, break, watchedRefs)

    override def visit[T, U[+_]](visitor: LazyParsleyIVisitor[T, U], context: T): U[A] = visitor.visit(this, context)(p, name, ascii, break, watchedRefs)

    private [parsley] var debugName = "debug"
}
private [parsley] final class DebugError[A](p: LazyParsley[A], name: String, ascii: Boolean, errBuilder: ErrorBuilder[_]) extends Unary[A, A](p) {
    override def make(p: StrictParsley[A]): StrictParsley[A] = new backend.DebugError(p, name, ascii, errBuilder)

    override def visit[T, U[+_]](visitor: LazyParsleyIVisitor[T, U], context: T): U[A] = visitor.visit(this, context)(p, name, ascii, errBuilder)

    private [parsley] var debugName = "debugError"
}

private [parsley] final class Profile[A](p: LazyParsley[A], name: String, profiler: Profiler) extends Unary[A, A](p) {
    override def make(p: StrictParsley[A]): StrictParsley[A] = new backend.Profile(p, name, profiler)

    override def visit[T, U[+_]](visitor: LazyParsleyIVisitor[T, U], context: T): U[A] = visitor.visit(this, context)(p, name, profiler)

    private [parsley] var debugName: String = "profile"
}

private [parsley] final class Impure[A](p: LazyParsley[A]) extends Unary[A, A](p) {
    override def make(p: StrictParsley[A]): StrictParsley[A] = new backend.Impure(p)

    override def visit[T, U[+_]](visitor: LazyParsleyIVisitor[T, U], context: T): U[A] = p.visit(visitor, context)

    private [parsley] var debugName: String = "impure"
}
// $COVERAGE-ON$
