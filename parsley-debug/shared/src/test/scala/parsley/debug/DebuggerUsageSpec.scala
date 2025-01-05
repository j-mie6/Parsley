/*
 * Copyright 2020 Parsley Contributors <https://github.com/j-mie6/Parsley/graphs/contributors>
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package parsley.debug

import scala.annotation.experimental

// scalastyle:off underscore.import
import org.typelevel.scalaccompat.annotation.unused
import parsley.Parsley, Parsley._
import parsley.ParsleyTest
import parsley.character._
import parsley.debug.combinator.{attachDebugger, named, detectDivergence}
import parsley.debug.internal.DebugContext
import parsley.expr._
import parsley.internal.deepembedding.frontend.debug.TaggedWith
import parsley.internal.deepembedding.backend.debug.Debugging
// scalastyle:on underscore.import

@experimental
class DebuggerUsageSpec extends ParsleyTest {
    import DebuggerUsageSpec.Arithmetic
    "the Debugged internal frontend class" should "not allow nesting of Debugged nodes" in {
        val factory = new Debugging(new DebugContext(parsley.debug.combinator.DefaultStringRules))
        try {
            val _ = new TaggedWith(factory)(new TaggedWith(factory)(fresh(()).internal, null, None), null, None)
            fail("Debugged nodes have been nested")
        } catch {
            case _: Throwable => info("assertion exception thrown, as expected")
        }
    }

    it should "preserve the prettified names of the parsers" in {
        val factory = new Debugging(new DebugContext(parsley.debug.combinator.DefaultStringRules))
        new TaggedWith(factory)(named(fresh(()), "foo").internal, null, None).debugName shouldBe "foo"
        new TaggedWith(factory)(fresh(()).internal, null, None).debugName shouldBe "fresh"
        new TaggedWith(factory)(fresh(()).internal, null, Some("bar")).debugName shouldBe "bar"
    }

    "the debugger runtime" should "preserve the result of parsers" in {
        val (_, debugMath) = attachDebugger(Arithmetic.prog)

        debugMath.parse("1+1").get.head shouldBe 2
        debugMath.parse("2*3").get.head shouldBe 6
        debugMath.parse("9-4").get.head shouldBe 5
        debugMath.parse("6/2").get.head shouldBe 3

        debugMath.parse("1+2+3+4+5\n2*3*4").get shouldBe List(15, 24) // scalastyle:ignore magic.number
    }

    it should "not cause references to be unallocated" in {
        import parsley.state._
        val p = detectDivergence(7.makeRef(r => many(char('a') *> r.get)))
        p.parse("aaa").get shouldBe List(7, 7, 7)
    }

    it should "preserve the side-effecting behaviour of parsers" in {
        var hit1 = false
        var hit2 = false
        var hit3 = false

        val parser = fresh { hit1 = true }.impure *> fresh { hit2 = true }.impure <* fresh { hit3 = true }
        val debugged = attachDebugger(parser)

        val _ = debugged._2.parse(""): @unused

        assert(hit1 && hit2 && hit3)
    }

    it should "store the children, representing the paths the parse-time execution took" in {
        val (generator, parser) = attachDebugger(Arithmetic.prog)
        parser.parse("1+1+1").get.head shouldBe 3

        assert(generator().nodeChildren.nonEmpty)
    }

    it should "factor out inputs of child parsers" in {
        val parser = many(string("abc"))
        val (treeF, debugged) = attachDebugger(parser)

        val _ = debugged.parse("abcabc"): @unused
        val tree = treeF()

        tree.parseResults match {
            case Some(result) => result.rawInput shouldBe "{1}{2}"
            case None         => fail("No result recorded.")
        }
    }
}

object DebuggerUsageSpec {
    @experimental @parsley.debuggable
    private [parsley] object Arithmetic {
        val int: Parsley[BigInt] = satisfy(_.isDigit).foldLeft1(BigInt(0))((acc, c) => acc * 10 + c.asDigit)
        lazy val expr: Parsley[BigInt] =
            // I've put these brackets explicitly, as they put expr into a strict position
            precedence[BigInt](int, char('(') ~> (expr <~ char(')')))(
                Ops(InfixL)(char('*') #> (_ * _), char('/') #> (_ / _)),
                Ops(InfixL)(char('+') #> (_ + _), char('-') #> (_ - _))
            )
        lazy val prog: Parsley[List[BigInt]] = many(many(satisfy("\r\n".contains(_))) ~> expr)
    }
}
