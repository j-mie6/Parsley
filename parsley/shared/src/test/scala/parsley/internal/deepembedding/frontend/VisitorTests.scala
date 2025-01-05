/*
 * Copyright 2020 Parsley Contributors <https://github.com/j-mie6/Parsley/graphs/contributors>
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package parsley.internal.deepembedding.frontend

import org.scalatest.Assertion
import org.typelevel.scalaccompat.annotation.unused
import parsley.{Parsley, ParsleyTest}
import parsley.debug.FullBreak
import parsley.errors, errors.{DefaultErrorBuilder, ErrorBuilder, Token}
import parsley.internal.collection.immutable.Trie
import parsley.internal.deepembedding.{ContOps, Sign}
import parsley.internal.deepembedding.backend.StrictParsley
import parsley.internal.deepembedding.singletons.*
import parsley.internal.deepembedding.singletons.token.*
import parsley.internal.errors.{CaretWidth, FlexibleCaret}
import parsley.state.Ref
import parsley.token.descriptions.SpaceDesc
import parsley.token.descriptions.PlusSignPresence
import parsley.token.errors.{ErrorConfig, BasicFilter, LabelConfig, SpecializedFilterConfig}
import parsley.token.Basic
import parsley.token.errors.NotConfigured

class VisitorTests extends ParsleyTest {
    sealed trait ConstUnit[+A]
    object CUnit extends ConstUnit[Nothing]

    private val testVisitor: LazyParsleyIVisitor[Unit, ConstUnit] =
        new GenericLazyParsleyIVisitor[Unit, ConstUnit] {
            override def visitSingleton[A](self: Singleton[A], context: Unit): ConstUnit[A] = CUnit

            override def visitUnary[A, B](self: Unary[A, B], context: Unit)(p: LazyParsley[A]): ConstUnit[B] = CUnit

            override def visitBinary[A, B, C](self: Binary[A, B, C], context: Unit)(l: LazyParsley[A], r: => LazyParsley[B]): ConstUnit[C] = CUnit

            override def visitTernary[A, B, C, D](self: Ternary[A, B, C, D], context: Unit)(f: LazyParsley[A],
                                                                                            s: => LazyParsley[B],
                                                                                            t: => LazyParsley[C]): ConstUnit[D] = CUnit

            override def visit[A](self: <|>[A], context: Unit)(p: LazyParsley[A], q: LazyParsley[A]): ConstUnit[A] = CUnit

            override def visit[A](self: ChainPre[A], context: Unit)(p: LazyParsley[A], op: => LazyParsley[A => A]): ConstUnit[A] = CUnit

            override def visitUnknown[A](self: LazyParsley[A], context: Unit): ConstUnit[A] = CUnit
        }

    private def dontExecute(): Nothing = fail("Should not execute.")

    private val dummyParser: LazyParsley[Nothing] = new LazyParsley[Nothing] {
        override protected def findLetsAux[M[_, +_] : ContOps, R](seen: Set[LazyParsley[_]])(implicit state: LetFinderState): M[R, Unit] = dontExecute()
        override protected def preprocess[M[_, +_] : ContOps, R, A_ >: Nothing](implicit lets: LetMap): M[R, StrictParsley[A_]] = dontExecute()
        override def visit[T, U[+_]](visitor: LazyParsleyIVisitor[T, U], context: T): U[Nothing] = dontExecute()
        private [parsley] var debugName: String = "dummy"
    }

    private val dummyLabelConfig: LabelConfig = NotConfigured

    private val dummyCaretWidth: CaretWidth = new FlexibleCaret(0)

    private val dummyErrorBuilder: ErrorBuilder[String] = new DefaultErrorBuilder {
        override def unexpectedToken(cs: Iterable[Char], amountOfInputParserWanted: Int, lexicalError: Boolean): Token = dontExecute()
    }

    private def dummySFConfig[A](): SpecializedFilterConfig[A] = new BasicFilter[A]

    implicit private class TestVisitorOps[A](p: LazyParsley[A]) {
        def testV: Assertion = p.visit(testVisitor, ()) shouldBe CUnit
    }

    private def dummyRef(): Ref[Unit] = Ref.make[Unit]

    def dontEval: Nothing = fail("Laziness was not maintained.")
    val crash = new PartialFunction[Any, Nothing] {
        def apply(@unused x: Any) = dontEval
        def isDefinedAt(x: Any): Boolean = false
    }
    def crash(@unused x: Any, @unused y: Any): Nothing = dontEval
    def crash(@unused x: Any, @unused y: Any, @unused z: Any): Nothing = dontEval

    they should "maintain laziness of the parsers visited" in {
        new NewReg(dummyRef(), dummyParser, dontEval).testV
        new Branch(dummyParser, dontEval, dontEval).testV
        new If(dummyParser, dontEval, dontEval).testV
        new Lift2[Nothing, Nothing, Nothing](crash, dummyParser, dontEval).testV
        new Lift3[Nothing, Nothing, Nothing, Nothing](crash, dummyParser, dontEval, dontEval).testV
        new Local(dummyRef(), dummyParser, dontEval).testV
        new <*>(dummyParser, dontEval).testV
        new *>(dummyParser, dontEval).testV
        new <*(dummyParser, dontEval).testV
        new ChainPost(dummyParser, dontEval).testV
        new Chainl(dummyParser, dontEval, dontEval, "dummy").testV
        new Chainr[Nothing, Nothing](dummyParser, dontEval, crash).testV
        new SepEndBy1(dummyParser, dontEval, null).testV
        new Filter[Any](dummyParser, _ => false, dontEval).testV
        new MapFilter[Any, Nothing](dummyParser, _ => None, dontEval).testV
    }

    they should "all return the constant unit object from the test visitor" in {
        // The lazy parsers have been tested for this in the laziness preservation test.
        new Pure(()).testV
        new Fresh(()).testV
        new Satisfy(_ => true, dummyLabelConfig).testV
        Line.testV
        Col.testV
        Offset.testV
        new Get(dummyRef()).testV
        new WhiteSpace(_ => true, SpaceDesc.plain, new ErrorConfig).testV
        new SkipComments(SpaceDesc.plain, new ErrorConfig).testV
        new Comment(SpaceDesc.plain, new ErrorConfig).testV
        new Sign(Sign.CombinedType, PlusSignPresence.Optional).testV
        new NonSpecific("foo", identity[String], "foo", _ => true, _ => true, _ => false).testV
        new CharTok(' ', ' ', dummyLabelConfig).testV
        new SupplementaryCharTok(0, 0, dummyLabelConfig).testV
        new StringTok("bar", 4, dummyLabelConfig).testV
        Eof.testV
        new UniSatisfy(_ => true, dummyLabelConfig).testV
        new Modify(dummyRef(), identity[Unit]).testV
        Parsley.empty.internal.testV
        new Fail(dummyCaretWidth).testV
        new Unexpected("qux", dummyCaretWidth).testV
        new VanillaGen(new errors.VanillaGen).testV
        new SpecializedGen[Any](new errors.SpecializedGen[Any] {
            def messages(x: Any) = Seq.empty
        }).testV
        new EscapeMapped(Trie.empty[Int], Set("quux")).testV
        new EscapeAtMost(0, 0).testV
        new EscapeOneOfExactly(0, Nil, dummySFConfig[Int]()).testV
        new SoftKeyword("corge", Basic(_ => true), false, dummyLabelConfig, "grault").testV
        new SoftOperator("garply", Basic(_ => true), Trie.empty[Unit], dummyLabelConfig, "waldo").testV
        new Atomic(dummyParser).testV
        new Look(dummyParser).testV
        new NotFollowedBy(dummyParser).testV
        new Put(dummyRef(), dummyParser).testV
        new Debug(dummyParser, "fred", false, FullBreak, Seq.empty: @org.typelevel.scalaccompat.annotation.nowarn3).testV
        new DebugError(dummyParser, "plugh", false, dummyErrorBuilder).testV
        new <|>(dummyParser, dummyParser, "dummy").testV
        new >>=[Nothing, Nothing](dummyParser, crash).testV
        new Many(dummyParser, null).testV
        new ChainPre(dummyParser, dummyParser).testV
        new Span(dummyParser).testV
        new Profile(dummyParser, "", null).testV
        new ManyUntil(dummyParser, null).testV
        new SkipManyUntil(dummyParser).testV
        new ErrorLabel(dummyParser, "test", Seq("bazola")).testV
        new ErrorHide(dummyParser).testV
        new ErrorExplain(dummyParser, "ztesch").testV
        new ErrorAmend(dummyParser, false).testV
        new ErrorEntrench(dummyParser).testV
        new ErrorDislodge(0, dummyParser).testV
        new ErrorLexical(dummyParser).testV
    }
}
