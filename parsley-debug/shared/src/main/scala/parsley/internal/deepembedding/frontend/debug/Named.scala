/*
 * Copyright 2020 Parsley Contributors <https://github.com/j-mie6/Parsley/graphs/contributors>
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package parsley.internal.deepembedding.frontend.debug

import parsley.XAssert

import parsley.internal.deepembedding.backend.StrictParsley
import parsley.internal.deepembedding.frontend.{LazyParsley, LazyParsleyIVisitor, Unary}

// $COVERAGE-OFF$
// Wrapper parser class indicating explicitly named parsers.
private [parsley] final class Named[A](_p: LazyParsley[A], val name: String) extends Unary[A, A](_p) {
    XAssert.assert(!p.isInstanceOf[Named[_]], "Named parsers should not be nested within each other directly.")
    def make(p: StrictParsley[A]): StrictParsley[A] = p
    override def visit[T, U[+_]](visitor: LazyParsleyIVisitor[T, U], context: T): U[A] = visitor.visitUnknown(this, context)
    private [parsley] var debugName = name
}

private [parsley] object Named {
    def apply[A](par: LazyParsley[A], name: String): Named[A] = new Named(par, name)

    def unapply[A](p: LazyParsley[A]): Option[(LazyParsley[A], String)] = p match {
        case n: Named[A @unchecked] => Some((n.p, n.name))
        case _                      => None
    }
}
// $COVERAGE-ON$
