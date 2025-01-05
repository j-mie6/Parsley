/*
 * Copyright 2020 Parsley Contributors <https://github.com/j-mie6/Parsley/graphs/contributors>
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package parsley.internal.deepembedding.frontend

import parsley.internal.deepembedding.ContOps, ContOps.{suspend, ContAdapter}
import parsley.internal.deepembedding.backend, backend.StrictParsley

private [parsley] final class <|>[A](p: LazyParsley[A], q: LazyParsley[A], var debugName: String) extends LazyParsley[A] {
    final override def findLetsAux[M[_, +_]: ContOps, R](seen: Set[LazyParsley[_]])(implicit state: LetFinderState): M[R,Unit] = {
        suspend(p.findLets[M, R](seen)) >> suspend(q.findLets(seen))
    }
    final override def preprocess[M[_, +_]: ContOps, R, A_ >: A](implicit lets: LetMap): M[R, StrictParsley[A_]] =
        for {
            p <- suspend(p.optimised[M, R, A])
            q <- suspend(q.optimised[M, R, A])
        } yield backend.<|>(p, q)

    // $COVERAGE-OFF$
    override def visit[T, U[+_]](visitor: LazyParsleyIVisitor[T, U], context: T): U[A] = visitor.visit(this, context)(p, q)
    // $COVERAGE-ON$
}
