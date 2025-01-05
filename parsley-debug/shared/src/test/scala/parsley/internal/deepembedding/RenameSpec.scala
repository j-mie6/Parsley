/*
 * Copyright 2020 Parsley Contributors <https://github.com/j-mie6/Parsley/graphs/contributors>
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package parsley.internal.deepembedding

import scala.annotation.experimental

import org.scalatest.Assertions.fail
import parsley.ParsleyTest
import parsley.debug.internal.{DebugContext, Renamer}
import parsley.debug.util.Collector
import parsley.internal.deepembedding.backend.StrictParsley
import parsley.internal.deepembedding.frontend.{LazyParsley, LazyParsleyIVisitor, LetFinderState, LetMap}
import parsley.internal.deepembedding.frontend.debug.TaggedWith
import parsley.token.Lexer
import parsley.token.descriptions.LexicalDesc
import parsley.internal.deepembedding.backend.debug.Debugging

@experimental
class RenameSpec extends ParsleyTest {
    "the Renamer object" should "not rename a parser it does not know of" in {
        val exampleParser = new DummyParser
        Renamer.nameOf(None, exampleParser) shouldNot be("exampleParser")
    }

    it should "rename a parser it is aware of" in {
        val exampleParser: LazyParsley[_] = new DummyParser
        Renamer.addNames(Map((exampleParser, "exampleParser")))

        Renamer.nameOf(None, exampleParser) shouldBe "exampleParser"
    }

    it should "translate a symbolic parser's name" in {
        val symbolic = new <**>
        Renamer.nameOf(None, symbolic) shouldBe "<**>"
    }

    it should "pass through Debugged parsers and get the inner parser's name" in {
        val symbolic = new <**>
        val debugged = new TaggedWith[Any](new Debugging(new DebugContext(parsley.debug.combinator.DefaultStringRules)))(symbolic, symbolic, None)

        Renamer.nameOf(None, debugged) shouldBe "<**>"
    }

    it should "override a known name if a parser is named." in {
        val exampleParser: LazyParsley[_] = new DummyParser
        Renamer.addNames(Map((exampleParser, "exampleParser")))

        Renamer.nameOf(Some("knownName"), exampleParser) shouldBe "knownName"
    }

    /*"the Collector implementations" should "collect names of parsers from objects (on supported platforms)" in {
        if (Collector.isSupported) {
            Renamer.nameOf(None, DebuggerUsageSpec.Arithmetic.prog.internal) shouldBe "prog"

            info("it should also allow overriding the name")
            Collector.assignName(DebuggerUsageSpec.Arithmetic.prog, "foo")
            Renamer.nameOf(None, DebuggerUsageSpec.Arithmetic.prog.internal) shouldBe "foo"
        } else alert("the current platform does not support Collector")
    }*/

    it should "collect names of parsers from lexers (on supported platforms)" in {
        val lexer = new Lexer(LexicalDesc.plain)
        if (Collector.isSupported) {
            Collector.lexer(lexer)
            // TODO: more tests here, we want to test across all aspects of the lexer
            Renamer.nameOf(None, lexer.lexeme.names.identifier.internal) shouldBe "identifier"
        } else alert("the current platform does not support Collector")
    }
}

object Utils {
    def crash(): Nothing = fail("Should not have been run.")
}

// These are dummy parsers used for the above tests.
// We don't actually care that they don't implement anything.
private class DummyParser extends LazyParsley[Any] {
    override protected def findLetsAux[M[_, +_] : ContOps, R](seen: Set[LazyParsley[_]])(implicit state: LetFinderState): M[R, Unit] = Utils.crash()
    override protected def preprocess[M[_, +_] : ContOps, R, A_ >: Any](implicit lets: LetMap): M[R, StrictParsley[A_]] = Utils.crash()
    override def visit[T, U[+_]](visitor: LazyParsleyIVisitor[T, U], context: T): U[Any] = visitor.visitUnknown(this, context)
    private [parsley] var debugName = "dummyParser"
}

private class <**> extends LazyParsley[Any] {
    override protected def findLetsAux[M[_, +_] : ContOps, R](seen: Set[LazyParsley[_]])(implicit state: LetFinderState): M[R, Unit] = Utils.crash()
    override protected def preprocess[M[_, +_] : ContOps, R, A_ >: Any](implicit lets: LetMap): M[R, StrictParsley[A_]] = Utils.crash()
    override def visit[T, U[+_]](visitor: LazyParsleyIVisitor[T, U], context: T): U[Any] = visitor.visitUnknown(this, context)
    private [parsley] var debugName = "<**>"
}
