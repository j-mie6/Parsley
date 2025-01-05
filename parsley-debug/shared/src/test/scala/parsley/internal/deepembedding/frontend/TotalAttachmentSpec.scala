/*
 * Copyright 2020 Parsley Contributors <https://github.com/j-mie6/Parsley/graphs/contributors>
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package parsley.internal.deepembedding.frontend

import scala.util.Random

import org.typelevel.scalaccompat.annotation.unused
import parsley.Parsley
import parsley.ParsleyTest
import parsley.combinator.ifS
import parsley.debug.combinator.attachDebugger
import parsley.expr.chain
import parsley.internal.deepembedding.frontend.debug.TaggedWith
import parsley.internal.deepembedding.singletons

class TotalAttachmentSpec extends ParsleyTest {
    private final class RandomParserCreator(maxDepth: Int) {
        private val rng: Random = new Random

        private val unaryModifiers: Array[Parsley[Unit] => Parsley[Unit]] = Array(
            Parsley.atomic,
            Parsley.lookAhead,
            _.map(identity[Unit]),
            _.flatMap(Parsley.pure),
            chain.left(_)(Parsley.fresh { (_: Unit, _: Unit) => () }, ()),
            chain.right(_)(Parsley.fresh { (_: Unit, _: Unit) => () }, ())
        )

        private val binaryModifiers: Array[(Parsley[Unit], Parsley[Unit]) => Parsley[Unit]] = Array(
            (px, py) => px <|> py,
            (px, py) => ifS(Parsley.pure(true), px, py),
        )

        private val baseParser: Parsley[Unit] = Parsley.unit

        def generate(depth: Int): Parsley[Unit] =
            if (depth >= maxDepth) baseParser
            else {
                rng.nextDouble() match {
                    case d if d <= 0.5 =>
                        val mod = unaryModifiers(rng.nextInt(unaryModifiers.length))
                        mod(generate(depth + 1))
                    case _             =>
                        val mod = binaryModifiers(rng.nextInt(binaryModifiers.length))
                        mod(generate(depth + 1), generate(depth + 1))
                }
            }
    }

    sealed trait ConstUnit[+A]
    object CUnit extends ConstUnit[Nothing]

    private final class AttachmentInspector extends GenericLazyParsleyIVisitor[Boolean, ConstUnit] {
        def failure(msg: String = "Parent parser was not debugged."): Nothing = fail(msg)

        override def visitSingleton[A](self: singletons.Singleton[A], parentIsTag: Boolean): ConstUnit[A] = {
            if (parentIsTag == self.isOpaque) CUnit else failure()
        }

        override def visitUnary[A, B](self: Unary[A, B], parentIsTag: Boolean)(p: LazyParsley[A]): ConstUnit[B] =
            if (parentIsTag  == self.isOpaque) {
                visitUnknown(p, parentIsTag = false): @unused
                CUnit
            } else failure()

        override def visitBinary[A, B, C](self: Binary[A, B, C], parentIsTag: Boolean)(l: LazyParsley[A], r: => LazyParsley[B]): ConstUnit[C] =
            if (parentIsTag == self.isOpaque) {
                visitUnknown(l, parentIsTag = false): @unused
                visitUnknown(r, parentIsTag = false): @unused
                CUnit
            } else failure()

        override def visitTernary[A, B, C, D](self: Ternary[A, B, C, D], parentIsTag: Boolean)(f: LazyParsley[A],
                                                                                               s: => LazyParsley[B],
                                                                                               t: => LazyParsley[C]): ConstUnit[D] =
            if (parentIsTag == self.isOpaque) {
                visitUnknown(f, parentIsTag = false): @unused
                visitUnknown(s, parentIsTag = false): @unused
                visitUnknown(t, parentIsTag = false): @unused
                CUnit
            } else failure()

        override def visit[A](self: <|>[A], parentIsTag: Boolean)(p: LazyParsley[A], q: LazyParsley[A]): ConstUnit[A] =
            if (parentIsTag == self.isOpaque) {
                visitUnknown(p, parentIsTag = false): @unused
                visitUnknown(q, parentIsTag = false): @unused
                CUnit
            } else failure()

        override def visit[A](self: ChainPre[A], parentIsTag: Boolean)(p: LazyParsley[A], op: => LazyParsley[A => A]): ConstUnit[A] =
            if (parentIsTag == self.isOpaque) {
                visitUnknown(p, parentIsTag = false): @unused
                visitUnknown(op, parentIsTag = false): @unused
                CUnit
            } else failure()

        // Somehow IntelliJ Scala thinks this is tail-recursive... but ScalaC does not?
        //noinspection NoTailRecursionAnnotation
        override def visitUnknown[A](self: LazyParsley[A], parentIsTag: Boolean): ConstUnit[A] =
            self match {
                case d: TaggedWith[_] if !parentIsTag => visitUnknown(d.subParser, parentIsTag = true)
                case _: TaggedWith[_]                 => failure("Not allowed to stack debuggers.") // Can't have a debugged on top of another!
                case s: singletons.Singleton[_]       => visitSingleton(s.asInstanceOf[singletons.Singleton[A]], parentIsTag)
                case g: GenericLazyParsley[_]         => visitGeneric(g.asInstanceOf[GenericLazyParsley[A]], parentIsTag)
                case alt: <|>[_]                      => alt.visit(this, parentIsTag)
                case cpre: ChainPre[_]                => cpre.visit(this, parentIsTag)
                case _                                => if (parentIsTag) CUnit else failure()
            }

    }

    behavior of "the debug node attachment visitor"

    it should "attach debuggers to all opaque nodes of a parser and not otherwise" in {
        val verifier = new AttachmentInspector
        val maxDepth = 12
        val width    = 100

        // Testing with ContOps[Id.Impl]
        for (i <- 0 until maxDepth) {
            val parserGenerator = new RandomParserCreator(i)
            for (_ <- 0 until width) {
                val (_, dbg) = attachDebugger(parserGenerator.generate(0))
                dbg.internal match {
                    case seq: *>[_] => verifier.visitUnknown(seq.right, parentIsTag = false)
                    case _          => fail("Debugger not attached.")
                }
            }
        }

        // Testing with ContOps[Cont.Impl]
        for (i <- 0 until maxDepth) {
            val parserGenerator = new RandomParserCreator(i)
            for (_ <- 0 until width) {
                val par = parserGenerator.generate(0)
                par.overflows()

                val (_, dbg) = attachDebugger(par)
                dbg.internal match {
                    case seq: *>[_] => verifier.visitUnknown(seq.right, parentIsTag = false)
                    case _ => fail("Debugger not attached.")
                }
            }
        }
    }

}
