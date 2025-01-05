/*
 * Copyright 2020 Parsley Contributors <https://github.com/j-mie6/Parsley/graphs/contributors>
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package parsley.internal.deepembedding

import scala.annotation.tailrec

import org.typelevel.scalaccompat.annotation.uncheckedVariance212

// Trampoline for CPS
private [deepembedding] sealed abstract class Bounce[A] {
    @tailrec final def run: A = this match {
        case thunk: Thunk[A] => thunk.cont().run
        case chunk: Chunk[A] => chunk.x
    }
}
private [deepembedding] final class Chunk[A](val x: A) extends Bounce[A]
private [deepembedding] final class Thunk[A](val cont: () => Bounce[A]) extends Bounce[A]

private [deepembedding] abstract class ContOps[Cont[_, +_]] {
    def wrap[R, A](x: A): Cont[R, A]
    def unwrap[R](wrapped: Cont[R, R]): R
    def map[R, A, B](c: Cont[R, A], f: A => B): Cont[R, B]
    def flatMap[R, A, B](c: Cont[R, A], f: A => Cont[R, B]): Cont[R, B]
    def suspend[R, A](x: =>Cont[R, A]): Cont[R, A]

    // Add more zips as needed by internal code. Hand-write each implementation if you can.
    def zipWith[R, A, B, C](xa: Cont[R, A], xb: =>Cont[R, B], f: (A, B) => C): Cont[R, C]
    def zipWith3[R, A, B, C, D](xa: Cont[R, A], xb: =>Cont[R, B], xc: =>Cont[R, C], f: (A, B, C) => D): Cont[R, D]

    def isStackSafe: Boolean
    // $COVERAGE-OFF$
    // This needs to be lazy, because I'm an idiot when I use it
    def `then`[R, A, B](c: Cont[R, A], k: =>Cont[R, B]): Cont[R, B] = flatMap[R, A, B](c, _ => k)
    def as[R, A, B](c: Cont[R, A], x: =>B): Cont[R, B] = map[R, A, B](c, _ => x)
    // $COVERAGE-ON$

}
private [deepembedding] object ContOps {
    implicit class ContAdapter[R, A, Cont[_, +_]](val c: Cont[R, A]) extends AnyVal {
        @inline def map[B](f: A => B)(implicit ops: ContOps[Cont]): Cont[R, B] = ops.map(c, f)
        @inline def flatMap[B](f: A => Cont[R, B])(implicit ops: ContOps[Cont]): Cont[R, B] = ops.flatMap(c, f)
        // This needs to be lazy, because I'm an idiot when I use it
        @inline def >>[B](k: =>Cont[R, B])(implicit ops: ContOps[Cont]): Cont[R, B] = ops.`then`(c, k)
        @inline def |>[B](x: =>B)(implicit ops: ContOps[Cont]): Cont[R, B] = ops.as(c, x)
    }
    @inline def result[R, A, Cont[_, +_]](x: A)(implicit canWrap: ContOps[Cont]): Cont[R, A] = canWrap.wrap(x)
    @inline def perform[Cont[_, +_], R](wrapped: Cont[R, R])(implicit canUnwrap: ContOps[Cont]): R = canUnwrap.unwrap(wrapped)
    @inline def suspend[Cont[_, +_], R, A](x: =>Cont[R, A])(implicit ops: ContOps[Cont]): Cont[R, A] = ops.suspend(x)
    // Zips. Add more as needed.
    @inline def zipWith[Cont[_, +_], R, A, B, C](xa: Cont[R, A], xb: =>Cont[R, B])(f: (A, B) => C)(implicit ops: ContOps[Cont]): Cont[R, C] = {
        ops.zipWith(xa, xb, f)
    }
    @inline def zipWith3[Cont[_, +_], R, A, B, C, D](xa: Cont[R, A], xb: =>Cont[R, B], xc: =>Cont[R, C])(f: (A, B, C) => D)
                                                    (implicit ops: ContOps[Cont]): Cont[R, D] = ops.zipWith3(xa, xb, xc, f)
}

private [deepembedding] object Cont {
    type Impl[R, +A] = (A => Bounce[R]) => Bounce[R]
    val ops: ContOps[Impl] = new ContOps[Impl] {
        override def wrap[R, A](x: A): Impl[R, A] = k => new Thunk(() => k(x))
        override def unwrap[R](wrapped: Impl[R, R]): R = wrapped(x => new Chunk(x)).run
        override def map[R, A, B](mx: Impl[R, A], f: A => B): Impl[R, B] = k => new Thunk(() => mx(x => new Thunk(() => k(f(x)))))
        override def flatMap[R, A, B](mx: Impl[R, A], f: A => Impl[R, B]): Impl[R, B] = k => new Thunk(() => mx(x => f(x)(k)))
        override def suspend[R, A](x: =>Impl[R, A]): Impl[R, A] = k => new Thunk(() => x(k))
        override def `then`[R, A, B](mx: Impl[R, A], my: =>Impl[R, B]): Impl[R, B] = k => new Thunk(() => mx(_ => my(k)))
        override def zipWith[R, A, B, C](xa: Impl[R, A], xb: => Impl[R, B], f: (A, B) => C): Impl[R, C] =
            k => new Thunk(() => xa(a => new Thunk(() => xb(b => new Thunk(() => k(f(a, b)))))))
        override def zipWith3[R, A, B, C, D](xa: Impl[R, A], xb: =>Impl[R, B], xc: =>Impl[R, C], f: (A, B, C) => D): Impl[R, D] =
            k => new Thunk(() => xa(a => new Thunk(() => xb(b => new Thunk(() => xc(c => new Thunk(() => k(f(a, b, c)))))))))
        override def isStackSafe: Boolean = true
    }
}

private [deepembedding] object Id {
    type Impl[R, +A] = A
    val ops: ContOps[Impl @uncheckedVariance212] = new ContOps[Impl] {
        override def wrap[R, A](x: A): Impl[R, A] = x
        override def unwrap[R](wrapped: Impl[R, R]): R = wrapped
        override def map[R, A, B](c: Impl[R, A], f: A => B): Impl[R, B] = f(c)
        override def flatMap[R, A, B](c: Impl[R, A], f: A => Impl[R, B]): Impl[R, B] = f(c)
        override def suspend[R, A](x: =>Impl[R, A]): Impl[R, A] = x
        override def as[R, A, B](c: Impl[R, A], x: =>B): Impl[R, B] = x
        override def zipWith[R, A, B, C](xa: Impl[R, A], xb: =>Impl[R, B], f: (A, B) => C): Impl[R, C] = f(xa, xb)
        override def zipWith3[R, A, B, C, D](xa: Impl[R, A], xb: =>Impl[R, B], xc: =>Impl[R, C], f: (A, B, C) => D): Impl[R, D] = f(xa, xb, xc)
        override def isStackSafe: Boolean = false
    }
}
